package me.binhmod.adb;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import binhmod.server.IShellService;

public class BootstrapProvider extends ContentProvider {

    public static final String METHOD_SEND_BINDER = "sendBinder";
    private static final String TAG = "BootstrapProvider";

    private static volatile IBinder sBinder;
    private static volatile IShellService sService;

    public static IBinder getBinder() { return sBinder; }
    public static IShellService getService() { return sService; }

    @Override
    public void attachInfo(android.content.Context ctx, ProviderInfo info) {
        super.attachInfo(ctx, info);
        if (info.multiprocess)
            throw new IllegalStateException("multiprocess must be false");
        if (!info.exported)
            throw new IllegalStateException("exported must be true");
    }

    @Override
    public boolean onCreate() { return true; }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (extras == null) return null;

        if (METHOD_SEND_BINDER.equals(method)) {
            IBinder binder = extras.getBinder("binder");
            if (binder == null || !binder.pingBinder()) {
                Log.w(TAG, "Received null or dead binder, ignoring");
                return new Bundle();
            }

            if (sBinder != null && sBinder != binder) {
                Log.d(TAG, "Replacing old binder");
                sBinder = null;
                sService = null;
            }

            Log.i(TAG, "Binder received from server");
            sBinder = binder;
            sService = IShellService.Stub.asInterface(binder);

            // auto clear when die
            try {
                binder.linkToDeath(() -> {
                    Log.w(TAG, "Server binder died, clearing");
                    sBinder = null;
                    sService = null;
                }, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "linkToDeath failed", e);
            }

            getContext().sendBroadcast(
                new Intent("binhmod.server.BINDER_RECEIVED")
                    .setPackage(getContext().getPackageName())
            );
        }
        return new Bundle();
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] sa, String so) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] sa) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] sa) { return 0; }
}