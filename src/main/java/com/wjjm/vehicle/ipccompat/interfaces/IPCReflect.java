package com.wjjm.vehicle.ipccompat.interfaces;

import android.os.IInterface;

import com.wjjm.vehicle.ipccompat.IProxyAidlInterface;
import com.wjjm.vehicle.ipccompat.model.MessageBean;

public interface IPCReflect {

    <T extends IPCInterface> void addInterface(T... t);

    <T extends IPCInterface> void removeInterface(T... t);

    MessageBean create(@MessageBean.WhatType int what, boolean variable, Object... objects);

    MessageBean create(@MessageBean.WhatType int what, String packageName, boolean variable, Object... objects);

    MessageBean create(@MessageBean.WhatType int what, String packageName, String stub, IInterface iInterface, boolean variable, Object... objects);

    <T> MessageBean create(IPCCallBack<T> callBack,boolean isRemove ,@MessageBean.WhatType int what, boolean variable, Object... objects);

    MessageBean reflect(MessageBean messageBean);

    void reflectAsync(final MessageBean messageBean, final IProxyAidlInterface proxy);

    <T> void reflectBack(final MessageBean messageBean);

    <T> T GenerateT(Class<T> tClass);

    void clear();
}
