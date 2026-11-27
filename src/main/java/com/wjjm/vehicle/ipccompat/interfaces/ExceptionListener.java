package com.wjjm.vehicle.ipccompat.interfaces;

public interface ExceptionListener {
    void onMethodNotFound(String methodName);

    void onServiceNotFound(String packageName, String serviceName);

    /**
     * 业务方法执行异常时回调，默认空实现
     */
    default void onException(Throwable throwable) {
    }
}
