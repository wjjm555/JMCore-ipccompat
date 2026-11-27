package com.wjjm.vehicle.ipccompat.interfaces;


import android.os.IBinder;

public interface IPCConnectListener {

  void onConnected(IBinder service);

  void onDisconnected(int state);

  void onBinderDied();
  default void onConnectedFinish(){}

}
