package binhmod.server;

import binhmod.server.IShellCallback;

interface IShellSession {

    void write(in byte[] data);

    byte[] read();

    byte[] poll(int timeout);

    void setCallback(IShellCallback cb);

    void stop();

    boolean ping();
}