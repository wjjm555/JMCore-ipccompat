package com.wjjm.vehicle.ipccompat.interfaces;

import android.os.IInterface;

public interface IPCClientInterface extends IPCBaseInterface {

    /**
     * 主动绑定服务
     * @return 如果系统正在启动您的客户端有权绑定的服务，则为true；如果系统找不到服务，或者您的客户端没有绑定到它的权限，则返回false。
     */
    boolean connect();

    /**
     * 主动释放绑定
     */
    void disconnect();

    /**
     * 同步调用
     */
    void invokeSync(Object... objects);

    /**
     * 异步调用
     */
    void invokeAsync(Object... objects);

    /**
     *
     * @param tClass
     * @param objects
     * @param <T>
     * @return
     */
    <T> T invokeReturn(Class<T> tClass, Object... objects);

    /**
     *
     * @param iInterface
     * @param objects
     */
    void registerInterfaceAsync(IInterface iInterface, Object... objects);

    /**
     *
     * @param iInterface
     * @param objects
     */
    void unregisterInterfaceAsync(IInterface iInterface, Object... objects);

    /**
     *
     * @param callBack
     * @param objects
     * @param <T>
     */
    <T> void registerIPCCallBackAsync(IPCCallBack<T> callBack, Object... objects);


}
