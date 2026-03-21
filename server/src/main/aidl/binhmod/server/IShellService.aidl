package binhmod.server;

import binhmod.server.IShellCallback;

interface IShellService {

    int openShell();

    void write(int id, in byte[] data);

    byte[] read(int id);

    byte[] poll(int id, int timeout);

    void registerCallback(int id, IShellCallback cb);

    void close(int id);
    
    void exit();

    boolean ping();
}