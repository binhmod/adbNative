package binhmod.server;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import rikka.hidden.compat.ActivityManagerApis;
import rikka.hidden.compat.PackageManagerApis;
import rikka.hidden.compat.adapter.ProcessObserverAdapter;
import rikka.hidden.compat.adapter.UidObserverAdapter;

public class BinderSender {

    private static final String TAG = "BinderSender";
    private static final String TARGET_PACKAGE = "me.binhmod.adb";

    /*
        Can use Shizuku built-in library like
    import static android.app.ActivityManagerHidden.UID_OBSERVER_ACTIVE;
    import static android.app.ActivityManagerHidden.UID_OBSERVER_CACHED;
    import static android.app.ActivityManagerHidden.UID_OBSERVER_GONE;
    import static android.app.ActivityManagerHidden.UID_OBSERVER_IDLE;
    instead of hardcore constants.
    */
    private static final int UID_OBSERVER_GONE   = 0x1;
    private static final int UID_OBSERVER_IDLE   = 0x2;
    private static final int UID_OBSERVER_ACTIVE = 0x4;
    private static final int UID_OBSERVER_CACHED = 0x8;
    private static final int PROCESS_STATE_UNKNOWN = -1;

    private static class ProcessObserver extends ProcessObserverAdapter {

        private static final List<Integer> PID_LIST = new ArrayList<>();

        @Override
        public void onForegroundActivitiesChanged(int pid, int uid, boolean fg) throws RemoteException {
            Log.d(TAG, "onForegroundActivitiesChanged: pid=" + pid + " uid=" + uid + " fg=" + fg);
            synchronized (PID_LIST) {
                if (PID_LIST.contains(pid) || !fg) return;
                PID_LIST.add(pid);
            }
            sendBinder(uid, pid);
        }

        @Override
        public void onProcessDied(int pid, int uid) {
            Log.d(TAG, "onProcessDied: pid=" + pid + " uid=" + uid);
            synchronized (PID_LIST) {
                int index = PID_LIST.indexOf(pid);
                if (index != -1) PID_LIST.remove(index);
            }
        }

        @Override
        public void onProcessStateChanged(int pid, int uid, int procState) throws RemoteException {
            Log.d(TAG, "onProcessStateChanged: pid=" + pid + " uid=" + uid);
            synchronized (PID_LIST) {
                if (PID_LIST.contains(pid)) return;
                PID_LIST.add(pid);
            }
            sendBinder(uid, pid);
        }
    }

    private static class UidObserver extends UidObserverAdapter {

        private static final List<Integer> UID_LIST = new ArrayList<>();

        @Override
        public void onUidActive(int uid) throws RemoteException {
            uidStarts(uid);
        }

        @Override
        public void onUidCachedChanged(int uid, boolean cached) throws RemoteException {
            if (!cached) uidStarts(uid);
        }

        @Override
        public void onUidIdle(int uid, boolean disabled) throws RemoteException {
            uidStarts(uid);
        }

        @Override
        public void onUidGone(int uid, boolean disabled) throws RemoteException {
            synchronized (UID_LIST) {
                int index = UID_LIST.indexOf(uid);
                if (index != -1) {
                    UID_LIST.remove(index);
                    Log.v(TAG, "Uid " + uid + " gone");
                }
            }
        }

        private void uidStarts(int uid) throws RemoteException {
            synchronized (UID_LIST) {
                if (UID_LIST.contains(uid)) {
                    Log.v(TAG, "Uid " + uid + " already tracked");
                    return;
                }
                UID_LIST.add(uid);
                Log.v(TAG, "Uid " + uid + " starts");
            }
            sendBinder(uid, -1);
        }
    }

    // Giống Shizuku: nhận pid để check permission chính xác hơn
    private static void sendBinder(int uid, int pid) throws RemoteException {
        List<String> packages = PackageManagerApis.getPackagesForUidNoThrow(uid);
        if (packages.isEmpty()) return;

        Log.d(TAG, "sendBinder to uid=" + uid
            + " packages=" + TextUtils.join(", ", packages));

        int userId = uid / 100000;

        for (String packageName : packages) {
            // Chỉ gửi cho đúng target package
            if (!TARGET_PACKAGE.equals(packageName)) continue;

            PackageInfo pi = PackageManagerApis.getPackageInfoNoThrow(
                packageName, PackageManager.GET_PERMISSIONS, userId);
            if (pi == null || pi.requestedPermissions == null) continue;

            Log.d(TAG, "Sending binder to " + packageName + " userId=" + userId);
            ShellServer.allowUid(uid);
            ShellServer.sendBinderToApp(packageName, userId);
            return;
        }
    }

    public static void register() {
        try {
            ActivityManagerApis.registerProcessObserver(new ProcessObserver());
            Log.i(TAG, "ProcessObserver registered");
        } catch (Throwable e) {
            Log.e(TAG, "registerProcessObserver failed", e);
        }

        if (Build.VERSION.SDK_INT >= 26) {
            int flags = UID_OBSERVER_GONE | UID_OBSERVER_IDLE | UID_OBSERVER_ACTIVE;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                flags |= UID_OBSERVER_CACHED;
            }
            try {
                ActivityManagerApis.registerUidObserver(
                    new UidObserver(), flags, PROCESS_STATE_UNKNOWN, null);
                Log.i(TAG, "UidObserver registered");
            } catch (Throwable e) {
                Log.e(TAG, "registerUidObserver failed", e);
            }
        }
    }
}