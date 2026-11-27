package com.wjjm.vehicle.ipccompat.interfaces;

public interface IPCServiceInterface extends IPCBaseInterface {

    /**
     * 调用所有客户端 invokeToAll
     */
    void invokeAllAsync(Object... objects);

    /**
     * 调用所有客户端 invokeToAll
     */
    void invokeAllSync(Object... objects);

    /**
     * 调用指定客户端 invokeToTarget
     */
    void invokeToAsync(Target target, Object... objects);

    /**
     * 调用指定客户端 invokeToTarget
     */
    void invokeToSync(Target target, Object... objects);

    /**
     *
     * @param tClass
     * @param target
     * @param objects
     * @param <T>
     * @return
     */
    <T> T invokeToReturn(Class<T> tClass, Target target, Object... objects);

}
