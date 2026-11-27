package com.wjjm.vehicle.ipccompat.interfaces;

public interface IPCBaseInterface {

    /**
     * 添加可以被其他应用调用的方法
     * @param t 可变参数，可以传入多个接口
     * @param <T> 接口需要继承 IPCInterface
     */
    <T extends IPCInterface> void addInterface(T... t);

    /**
     * 移除可以被其他应用调用的方法
     * @param t 可变参数，可以传入多个接口
     * @param <T> 接口需要继承 IPCInterface
     */
    <T extends IPCInterface> void removeInterface(T... t);

}
