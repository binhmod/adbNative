package binhmod.server;

interface IShellCallback {

    void onData();

    void onExit(int code);

    void onError(String msg);
}