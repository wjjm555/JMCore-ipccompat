package com.wjjm.vehicle.ipccompat.interfaces;

import android.content.Context;

public interface IPCConnectAdapter {

    boolean isConnected();

    void setListener(IPCConnectListener listener);

    boolean connect(Context context, String packageName, String className, boolean isAutoReregister,long  autoReregisterDelay,boolean isDeathRecipient);

    void disconnect(Context context);

    void start(Context context, String packageName, String className);

}
