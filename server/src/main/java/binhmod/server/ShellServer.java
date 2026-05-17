package binhmod.server;

import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;
import android.os.Handler;
import android.os.Bundle;
import android.content.IContentProvider;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import rikka.hidden.compat.ActivityManagerApis;
import binhmod.server.util.IContentProviderUtils;

import binhmod.server.BinderSender;

public class ShellServer {

    private static final String TAG = "ShellServer";
    private static final java.util.Set<Integer> sAllowedUids =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    public static void allowUid(int uid) {
        sAllowedUids.add(uid);
        Log.d(TAG, "allowUid: " + uid);
    }

    static final IShellService SERVICE = new ShellServiceImpl();

    public static void main(String[] args) {
        Log.i(TAG, "ShellServer started, uid=" + android.os.Process.myUid());
        Looper.prepareMainLooper();

        new Handler(Looper.getMainLooper())
                .post(
                        () -> {
                            sendBinderToApp("me.binhmod.adb", 0);

                            BinderSender.register();
                        });
        Looper.loop();
    }

    public static void sendBinderToApp(String packageName, int userId) {
        String authority = packageName + ".bootstrap";
        IContentProvider provider = null;
        IBinder token = null;

        try {
            // Lấy uid của app trước khi gửi binder
            int appUid = -1;
try {
    android.content.pm.IPackageManager pm =
            android.content.pm.IPackageManager.Stub.asInterface(
                    android.os.ServiceManager.getService("package"));

    android.content.pm.ApplicationInfo ai;

    if (android.os.Build.VERSION.SDK_INT >= 30) {
        // Android 11+
        java.lang.reflect.Method m = pm.getClass().getMethod(
                "getApplicationInfo",
                String.class,
                long.class,
                int.class
        );
        ai = (android.content.pm.ApplicationInfo) m.invoke(
                pm, packageName, 0L, userId
        );
    } else {
        // Android 10-
        java.lang.reflect.Method m = pm.getClass().getMethod(
                "getApplicationInfo",
                String.class,
                int.class,
                int.class
        );
        ai = (android.content.pm.ApplicationInfo) m.invoke(
                pm, packageName, 0, userId
        );
    }

    if (ai != null) {
        appUid = ai.uid;
        ShellServer.allowUid(appUid);
        Log.d(TAG, "Whitelisted uid=" + appUid + " for " + packageName);
    }

} catch (Throwable e) {
    Log.e(TAG, "Failed to get uid for " + packageName, e);
}

            provider =
                    ActivityManagerApis.getContentProviderExternal(
                            authority, userId, token, authority);

            if (provider == null) {
                Log.e(TAG, "provider is null: " + authority);
                return;
            }
            if (!provider.asBinder().pingBinder()) {
                Log.e(TAG, "provider is dead: " + authority);
                return;
            }

            Bundle extras = new Bundle();
            extras.putBinder("binder", SERVICE.asBinder());

            Bundle reply =
                    IContentProviderUtils.callCompat(
                            provider, packageName, authority, "sendBinder", null, extras);

            if (reply != null) {
                Log.i(TAG, "Binder sent to " + packageName);
            } else {
                Log.w(TAG, "Failed to send binder to " + packageName);
            }

        } catch (Exception e) {
            Log.e(TAG, "sendBinderToApp error", e);
        } finally {
            if (provider != null) {
                try {
                    ActivityManagerApis.removeContentProviderExternal(authority, token);
                } catch (Exception ignored) {
                }
            }
        }
    }

    static class ShellServiceImpl extends IShellService.Stub {

        private final ConcurrentHashMap<Integer, ShellSession> sessions = new ConcurrentHashMap<>();
        private final AtomicInteger nextId = new AtomicInteger(1);

        private void enforceCaller() {
            int callingUid = Binder.getCallingUid();
            int myUid = android.os.Process.myUid();
            if (callingUid == myUid) return;
            if (sAllowedUids.contains(callingUid)) return;
            throw new SecurityException("Unauthorized uid: " + callingUid);
        }

        @Override
        public int openShell() {
            enforceCaller();
            int id = nextId.getAndIncrement();
            Log.d(TAG, "openShell: assigning id " + id);
            try {
                ShellSession s = new ShellSession(id, this);
                sessions.put(id, s);
                Log.i(TAG, "Shell session " + id + " created");
                return id;
            } catch (RuntimeException e) {
                Log.e(TAG, "openShell failed for id " + id, e);
                return -1;
            }
        }

        @Override
        public void write(int id, byte[] data) {
            enforceCaller();
            Log.d(TAG, "write called for id " + id + ", data length=" + data.length);
            ShellSession s = sessions.get(id);
            if (s != null) {
                s.write(data);
            } else {
                Log.w(TAG, "write: session " + id + " not found");
            }
        }

        @Override
        public byte[] read(int id) {
            enforceCaller();
            ShellSession s = sessions.get(id);
            return s != null ? s.read() : null;
        }

        @Override
        public byte[] poll(int id, int timeout) {
            enforceCaller();
            ShellSession s = sessions.get(id);
            return s != null ? s.poll(timeout) : null;
        }

        @Override
        public void registerCallback(int id, IShellCallback cb) {
            enforceCaller();
            Log.d(TAG, "registerCallback for id " + id);
            ShellSession s = sessions.get(id);
            if (s != null) s.setCallback(cb);
        }

        @Override
        public void close(int id) {
            enforceCaller();
            Log.d(TAG, "close called for id " + id);
            ShellSession s = sessions.remove(id);
            if (s != null) s.stop();
        }

        void onSessionExit(int id) {
            Log.d(TAG, "onSessionExit: removing session " + id);
            sessions.remove(id);
        }
        
        @Override
        public void exit() {
            enforceCaller();
            Log.i(TAG, "exit() called, shutting down server");
            for (ShellSession s : sessions.values()) {
                s.stop();
            }
            sessions.clear();
            System.exit(0);
        }

        @Override
        public boolean ping() {
            return true;
        }
    }

    static class ShellSession extends IShellSession.Stub {

        final int id;
        final ShellServiceImpl service;

        Process process;
        OutputStream stdin;

        final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
        IShellCallback callback;

        final AtomicBoolean running = new AtomicBoolean(true);
        volatile long lastActivity = System.currentTimeMillis();

        ShellSession(int id, ShellServiceImpl service) {
            this.id = id;
            this.service = service;
            startShell();
        }

        void startShell() {
            Log.i(
                    TAG,
                    "startShell: myUid="
                            + android.os.Process.myUid()
                            + ", myPid="
                            + android.os.Process.myPid()
                            + ", callingUid="
                            + Binder.getCallingUid());

            long token = Binder.clearCallingIdentity();
            try {
                Log.i(TAG, "Starting shell process for session " + id);
                ProcessBuilder pb = new ProcessBuilder("sh");
                pb.redirectErrorStream(true);
                pb.environment().put("TERM", "xterm");
                pb.environment().put("PATH", "/system/bin:/system/xbin:/sbin:/vendor/bin");
                process = pb.start();
                Log.i(TAG, "Shell process started, pid=" + android.os.Process.myPid());
                stdin = process.getOutputStream();
                startReader(process.getInputStream());
                startWaiter();
                startWatchdog();
            } catch (Exception e) {
                Log.e(TAG, "startShell error", e);
                notifyError("Failed to start shell: " + e.toString());
                stop();
                throw new RuntimeException("Failed to start shell", e);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        void startReader(InputStream stream) {
            new Thread(
                            () -> {
                                byte[] buf = new byte[8192];
                                try {
                                    int len;
                                    while (running.get() && (len = stream.read(buf)) != -1) {
                                        lastActivity = System.currentTimeMillis();
                                        byte[] data = Arrays.copyOf(buf, len);
                                        Log.v(
                                                TAG,
                                                "Read "
                                                        + len
                                                        + " bytes from shell (session "
                                                        + id
                                                        + ")");
                                        try {
                                            queue.put(data);
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                        }
                                        notifyData();
                                    }
                                    Log.d(TAG, "Reader thread EOF for session " + id);
                                } catch (Exception e) {
                                    Log.w(TAG, "reader error for session " + id, e);
                                } finally {
                                    stop();
                                }
                            },
                            "shell-reader-" + id)
                    .start();
        }

        void startWaiter() {
            new Thread(
                            () -> {
                                try {
                                    int code = process.waitFor();
                                    Log.d(
                                            TAG,
                                            "Process exited with code "
                                                    + code
                                                    + " for session "
                                                    + id);
                                    running.set(false);
                                    notifyExit(code);
                                } catch (Exception e) {
                                    Log.w(TAG, "waiter error for session " + id, e);
                                } finally {
                                    service.onSessionExit(id);
                                }
                            },
                            "shell-waiter-" + id)
                    .start();
        }

        void startWatchdog() {
            new Thread(
                            () -> {
                                while (running.get()) {
                                    try {
                                        Thread.sleep(5000);
                                        long idle = System.currentTimeMillis() - lastActivity;
                                        if (idle > 300000) { // 5 phút
                                            Log.w(
                                                    TAG,
                                                    "watchdog killing shell session "
                                                            + id
                                                            + " due to inactivity");
                                            notifyError("Session timeout");
                                            stop();
                                            break;
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                            },
                            "shell-watchdog-" + id)
                    .start();
        }

        void notifyData() {
            if (callback != null) {
                try {
                    callback.onData();
                } catch (Exception e) {
                    Log.e(TAG, "notifyData error", e);
                }
            }
        }

        void notifyExit(int code) {
            if (callback != null) {
                try {
                    Log.d(TAG, "Sending onExit(" + code + ") to client for session " + id);
                    callback.onExit(code);
                } catch (Exception e) {
                    Log.e(TAG, "notifyExit error", e);
                }
            }
        }

        void notifyError(String msg) {
            if (callback != null) {
                try {
                    Log.e(TAG, "Sending onError(\"" + msg + "\") to client for session " + id);
                    callback.onError(msg);
                } catch (Exception e) {
                    Log.e(TAG, "notifyError error", e);
                }
            }
        }

        @Override
        public void write(byte[] data) {
            Log.d(TAG, "write() called on session " + id + " with " + data.length + " bytes");
            if (stdin == null) {
                Log.e(TAG, "stdin is null, cannot write");
                // Ném exception để client biết
                throw new IllegalStateException("Shell not ready or already closed");
            }
            try {
                stdin.write(data);
                stdin.flush();
                lastActivity = System.currentTimeMillis();
                Log.d(TAG, "Data written to shell stdin for session " + id);
            } catch (Exception e) {
                Log.e(TAG, "write error for session " + id, e);
                stop();
                throw new RuntimeException(e);
            }
        }

        @Override
        public byte[] read() {
            return queue.poll();
        }

        @Override
        public byte[] poll(int timeout) {
            try {
                return queue.poll(timeout, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        @Override
        public void setCallback(IShellCallback cb) {
            this.callback = cb;
            try {
                if (cb != null) {
                    cb.asBinder().linkToDeath(this::stop, 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "setCallback linkToDeath error", e);
            }
        }

        @Override
        public void stop() {
            if (!running.getAndSet(false)) return;
            Log.i(TAG, "Stopping shell session " + id);
            try {
                if (stdin != null) stdin.close();
            } catch (Exception ignored) {
            }
            try {
                if (process != null) {
                    process.destroy();
                    if (process.isAlive()) process.destroyForcibly();
                }
            } catch (Exception ignored) {
            }
            service.onSessionExit(id);
        }

        @Override
        public boolean ping() {
            return running.get();
        }
    }
}
