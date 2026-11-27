package com.wjjm.vehicle.ipccompat.client;

import static com.wjjm.vehicle.ipccompat.Constants.Connected;

import android.app.Application;
import android.content.Context;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.os.SystemClock;

import androidx.annotation.CallSuper;

import com.wjjm.vehicle.ipccompat.Constants;
import com.wjjm.vehicle.ipccompat.IProxyAidlInterface;
import com.wjjm.vehicle.ipccompat.IServiceAidlInterface;
import com.wjjm.vehicle.ipccompat.interfaces.ExceptionListener;
import com.wjjm.vehicle.ipccompat.interfaces.IPCCallBack;
import com.wjjm.vehicle.ipccompat.interfaces.IPCClientInterface;
import com.wjjm.vehicle.ipccompat.interfaces.IPCConnectAdapter;
import com.wjjm.vehicle.ipccompat.interfaces.IPCConnectListener;
import com.wjjm.vehicle.ipccompat.interfaces.IPCInterface;
import com.wjjm.vehicle.ipccompat.model.MessageBean;
import com.wjjm.vehicle.ipccompat.util.ContextManager;
import com.wjjm.vehicle.ipccompat.util.IPCLog;
import com.wjjm.vehicle.ipccompat.util.IPCBase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public abstract class IPCClient implements IPCClientInterface, IPCConnectListener, ExceptionListener {

    final String TAG = "IPCCompat_IPCClient";

    private final Map<IInterface, MessageBean> interfaceCache = new ConcurrentHashMap<>();

    private String packageName = null;

    private final IPCBase reflect = new IPCBase(this);

    private final IProxyAidlInterface iProxyAidlInterface = new AIDL();

    private IServiceAidlInterface iServiceAidlInterface;

    private final IPCConnectAdapter connectAdapter = new IPCCClientConnectAdapter(this);

    /**
     * 按消息 UUID 去重并保持 FIFO 顺序，避免重复消息被执行多次
     */
    private final Map<String, MessageBean> cache = Collections.synchronizedMap(new LinkedHashMap<>());

    private final Object connectLock = new Object();

    protected abstract String getTargetPackage();

    protected abstract String getTargetService();

    protected boolean isAutoReregister() {
        return false;
    }

    protected long autoReregisterDelay() {
        return Constants.AutoRegisterDelay;
    }

    /**
     * @param state {@link Constants}
     */
    protected void onServiceConnectStateChanged(@Constants.ServiceConnectState int state) {

    }

    @CallSuper
    @Override
    public void onServiceNotFound(String packageName, String serviceName) {
        IPCLog.whatever(TAG, "onServiceNotFound:" + serviceName);
    }

    @CallSuper
    @Override
    public void onMethodNotFound(String methodName) {
        IPCLog.whatever(TAG, "onMethodNotFound:" + methodName);
    }

    private String getPackageName() {
        if (packageName == null) packageName = getContext().getPackageName();
        return packageName;
    }


    @SafeVarargs
    @Override
    public final <T extends IPCInterface> void addInterface(T... t) {
        reflect.addInterface(t);
    }

    @SafeVarargs
    @Override
    public final <T extends IPCInterface> void removeInterface(T... t) {
        reflect.removeInterface(t);
    }

    /**
     * 异步调用
     */
    @Override
    public final void invokeAsync(Object... objects) {
        IPCLog.w(TAG, "invokeAsync start");
        invokeMessage(reflect.create(MessageBean.WHAT_STANDARD_ASYNC, getPackageName(), false, objects));
        IPCLog.w(TAG, "invokeAsync end");
    }


    /**
     * 异步调用，注册回调，主要用于缓存回调对象
     */
    @Override
    public final void registerInterfaceAsync(IInterface iInterface, Object... objects) {
        IPCLog.w(TAG, "registerInterfaceAsync start");
        MessageBean bean = reflect.create(MessageBean.WHAT_CUSTOM_INTERFACE, getPackageName(), Objects.requireNonNull(iInterface.getClass().getSuperclass()).getName(), iInterface, false, objects);
        IPCLog.w(TAG, "registerInterfaceAsync isAutoRegister:" + isAutoReregister());
        if (isAutoReregister()) interfaceCache.put(iInterface, bean);
        invokeMessage(bean);
        IPCLog.w(TAG, "registerInterfaceAsync end");
    }

    /**
     * 异步调用，解注册回调，主要用于清除缓存回调对象
     *
     * @param iInterface
     * @param objects
     */
    @Override
    public final void unregisterInterfaceAsync(IInterface iInterface, Object... objects) {
        IPCLog.w(TAG, "unregisterInterfaceAsync start");
        MessageBean bean = reflect.create(MessageBean.WHAT_CUSTOM_INTERFACE, getPackageName(), Objects.requireNonNull(iInterface.getClass().getSuperclass()).getName(), iInterface, false, objects);
        IPCLog.w(TAG, "unregisterInterfaceAsync isAutoRegister:" + isAutoReregister());
        if (isAutoReregister()) interfaceCache.remove(iInterface);
        invokeMessage(bean);
        IPCLog.w(TAG, "unregisterInterfaceAsync end");
    }

    /**
     * 异步调用有回调 定义接口时，回调参数必须在第一个，注册回调，主要用于缓存回调对象
     *
     * @param callBack
     * @param objects
     * @param <T>
     */
    @Override
    public final <T> void registerIPCCallBackAsync(IPCCallBack<T> callBack, Object... objects) {
        IPCLog.w(TAG, "registerIPCCallBackAsync start");
        invokeMessage(reflect.create(callBack, false, MessageBean.WHAT_CALLBACK, false, objects));
        IPCLog.w(TAG, "registerIPCCallBackAsync end");
    }


    /**
     * 同步调用有返回值
     *
     * @param tClass
     * @param objects
     * @param <T>
     * @return
     */
    @Override
    public final <T> T invokeReturn(Class<T> tClass, Object... objects) {
        IPCLog.w(TAG, "invokeReturn start");

        checkConnect(false);

        try {
            MessageBean messageBean = iServiceAidlInterface.sendMessage(reflect.create(MessageBean.WHAT_RETURN, getPackageName(), false, objects));
            Object object = messageBean.getReturn();
            if (object instanceof Throwable) {
                //对端业务方法抛异常：不向调用方抛异常，记录日志并通知监听器，最后返回默认值
                Throwable cause = (Throwable) object;
                IPCLog.e(TAG, "remote method '" + messageBean.getTarget() + "' threw an exception");
                IPCLog.printStackTrace(cause);
                onException(cause);
            } else if (object != null) {
                return (T) object;
            }
        } catch (Throwable e) {
            IPCLog.e(TAG, "Exception:" + e.getMessage());
            IPCLog.printStackTrace(e);
        }
        IPCLog.w(TAG, "invokeReturn end");
        return reflect.GenerateT(tClass);
    }


    /**
     * 同步调用
     *
     * @param objects
     */
    @Override
    public final void invokeSync(Object... objects) {
        IPCLog.w(TAG, "invokeSync start");
        checkConnect(false);
        try {
            MessageBean messageBean = iServiceAidlInterface.sendMessage(reflect.create(MessageBean.WHAT_STANDARD, getPackageName(), false, objects));
            Object object = messageBean.getReturn();
            if (object instanceof Throwable) {
                //对端业务方法抛异常：不向调用方抛异常，记录日志并通知监听器
                Throwable cause = (Throwable) object;
                IPCLog.e(TAG, "remote method '" + messageBean.getTarget() + "' threw an exception");
                IPCLog.printStackTrace(cause);
                onException(cause);
            }
        } catch (Throwable e) {
            IPCLog.e(TAG, "Exception:" + e.getMessage());
            IPCLog.printStackTrace(e);
        }
        IPCLog.w(TAG, "invokeSync end");
    }

    @Override
    public final boolean connect() {
        return connect(false);
    }

    @Override
    public final void disconnect() {
        connectAdapter.disconnect(getContext());
    }

    private synchronized boolean connect(boolean isDeathRecipient) {
        return connectAdapter.connect(getContext(), getTargetPackage(), getTargetService(), isAutoReregister(), autoReregisterDelay(), isDeathRecipient);
    }

    private void checkConnect(boolean isDeathRecipient) {
        IPCLog.w(TAG, "checkConnect isDeathRecipient:" + isDeathRecipient);
        synchronized (connectLock) {
            if (!connectAdapter.isConnected()) {
                boolean success = connect(isDeathRecipient);
                if (success) {
                    try {
                        IPCLog.w(TAG, "checkConnect wait : If the service is disconnected, the current thread will be blocked.");
                        //如果服务断开，会阻塞当前的线程，等待连接完成或超时。
                        long deadline = SystemClock.elapsedRealtime() + Constants.ConnectWaitTimeout;
                        while (!connectAdapter.isConnected()) {
                            long remaining = deadline - SystemClock.elapsedRealtime();
                            if (remaining <= 0) break;
                            connectLock.wait(remaining);
                        }
                        if (!connectAdapter.isConnected())
                            onServiceNotFound(getTargetPackage(), getTargetService());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    onServiceNotFound(getTargetPackage(), getTargetService());
                }
            }
        }
    }

    private void invokeMessage(MessageBean messageBean) {
        if (connectAdapter.isConnected()) {
            sendMessage(messageBean);
        } else {
            cache.putIfAbsent(messageBean.getID(), messageBean);
            boolean success = connect(false);
            if (!success) onServiceNotFound(getTargetPackage(), getTargetService());
        }
    }

    private void sendMessage(MessageBean messageBean) {
        try {
            switch (messageBean.getWhat()) {
                case MessageBean.WHAT_STANDARD:
                    iServiceAidlInterface.sendMessage(messageBean);
                    break;
                case MessageBean.WHAT_STANDARD_ASYNC:
                    iServiceAidlInterface.sendMessageVoidAsync(messageBean);
                    break;
                case MessageBean.WHAT_CALLBACK:
                case MessageBean.WHAT_CUSTOM_INTERFACE:
                    iServiceAidlInterface.sendMessageAsync(messageBean, iProxyAidlInterface);
                    break;
            }
        } catch (Exception e) {
            IPCLog.e(TAG, "Exception:" + e.getMessage());
            IPCLog.printStackTrace(e);
        }
    }

    @Override
    public final void onConnected(IBinder service) {
        iServiceAidlInterface = IServiceAidlInterface.Stub.asInterface(service);
        if (iServiceAidlInterface != null) {
            try {
                iServiceAidlInterface.setProxy(getPackageName(), iProxyAidlInterface);
            } catch (RemoteException e) {
                IPCLog.e(TAG, "Exception:" + e.getMessage());
                IPCLog.printStackTrace(e);
            }
        }
    }

    @Override
    public void onConnectedFinish() {
        //连接成功后，依次发送缓存中的消息。
        for (MessageBean messageBean : new ArrayList<>(cache.values())) {
            cache.remove(messageBean.getID());
            invokeMessage(messageBean);
        }

        synchronized (connectLock) {
            connectLock.notifyAll();
        }

        onServiceConnectStateChanged(Connected);
    }

    @Override
    public final void onDisconnected(int state) {
        //连接断开后，等待中的回调永远不会到达，清理掉
        iServiceAidlInterface = null;
        reflect.clearCallBacks();
        synchronized (connectLock) {
            connectLock.notifyAll();
        }
        onServiceConnectStateChanged(state);
    }

    @Override
    public final void onBinderDied() {
        //服务进程死亡，等待中的回调永远不会到达，清理掉
        reflect.clearCallBacks();
        connect(true);
        if (isAutoReregister()) {
            for (MessageBean bean : interfaceCache.values()) {
                invokeMessage(bean);
            }
        }
    }


    class AIDL extends IProxyAidlInterface.Stub {

        @Override
        public MessageBean sendMessage(MessageBean message) throws RemoteException {
            IPCLog.w(TAG, "IProxyAidlInterface sendMessage:" + message);
            return reflect.reflect(message);
        }

        @Override
        public void messageIPCCallBack(MessageBean message) throws RemoteException {
            IPCLog.w(TAG, "IProxyAidlInterface messageIPCCallBack:" + message);
            //oneway 消息放到工作线程池执行，避免业务代码占用 binder 线程
            IPCBase.asyncExecutor().execute(() -> reflect.reflectBack(message));
        }

        @Override
        public void sendMessageVoidAsync(MessageBean message) throws RemoteException {
            IPCLog.w(TAG, "IProxyAidlInterface sendMessageVoidAsync:" + message);
            //oneway 消息放到工作线程池执行，避免业务代码占用 binder 线程
            IPCBase.asyncExecutor().execute(() -> reflect.reflect(message));
        }
    }

    protected final Context getContext() {
        return ContextManager.getContext();
    }
    protected final Application getApplication() {
        return ContextManager.getMApplication();
    }
}
