package com.wjjm.vehicle.ipccompat.interfaces;

public interface IPCCallBack<T> {
    void onResultBack(T object);

    /**
     * 对端业务方法抛异常时回调，默认空实现
     */
    default void onError(Throwable throwable) {
    }
}
