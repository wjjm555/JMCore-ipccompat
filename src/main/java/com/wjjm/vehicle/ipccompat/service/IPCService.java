package com.wjjm.vehicle.ipccompat.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.text.TextUtils;

import androidx.annotation.CallSuper;

import com.wjjm.vehicle.ipccompat.Constants;
import com.wjjm.vehicle.ipccompat.IProxyAidlInterface;
import com.wjjm.vehicle.ipccompat.IServiceAidlInterface;
import com.wjjm.vehicle.ipccompat.interfaces.ExceptionListener;
import com.wjjm.vehicle.ipccompat.interfaces.IPCInterface;
import com.wjjm.vehicle.ipccompat.interfaces.IPCServiceInterface;
import com.wjjm.vehicle.ipccompat.interfaces.Target;
import com.wjjm.vehicle.ipccompat.model.MessageBean;
import com.wjjm.vehicle.ipccompat.model.TargetImp;
import com.wjjm.vehicle.ipccompat.util.IPCLog;
import com.wjjm.vehicle.ipccompat.util.IPCBase;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public abstract class IPCService extends Service implements ExceptionListener, IPCServiceInterface {

    final String TAG = "IPCCompat_IPCService";

    /**
     * client的引用，在client绑定到service的时候会设定。
     */
    private final Map<String, Set<IProxyAidlInterface>> proxies = new ConcurrentHashMap<>();

    private final IPCBase reflect = new IPCBase(this);

    private final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * 客户端携带死亡重连标记（TAG_DEATH_RECIPIENT）绑定后，延迟触发 onRestart 的毫秒数。
     * 延迟是为了等客户端在 onConnected 中完成 setProxy 注册，此时业务方可安全地调用 invokeToXxx
     */
    private static final long ON_RESTART_DELAY_MILLIS = 300;

    /**
     * 客户端在服务进程死亡后重新绑定时回调。
     * 仅当客户端绑定携带 TAG_DEATH_RECIPIENT 标记时触发，且在客户端完成代理注册之后执行
     */
    @CallSuper
    public void onRestart() {
        IPCLog.w(TAG, "onRestart");
    }

    @CallSuper
    @Override
    public void onMethodNotFound(String methodName) {
        IPCLog.whatever(TAG, "onMethodNotFound:" + methodName);
    }

    @CallSuper
    protected void onBind(Target target) {
        IPCLog.w(TAG, "onBind:" + target);
    }

    @CallSuper
    protected void onUnbind(Target target) {
        IPCLog.w(TAG, "onUnbind:" + target);
    }

    @CallSuper
    @Override
    public final void onServiceNotFound(String packageName, String serviceName) {
        IPCLog.whatever(TAG, "onServiceNotFound:" + serviceName);
    }

    @Deprecated
    protected String getChannelID() {
        return "com.wjjm.vehicle.ipccompat";
    }

    @Deprecated
    protected String getChannelName() {
        return "IPCService";
    }

    @Override
    public final IBinder onBind(Intent intent) {
        if (intent != null) {
            String clientPackageName = intent.getStringExtra(Constants.TAG_PACKAGE_NAME);

            IPCLog.w(TAG, "onBind clientPackageName:" + clientPackageName);

            if (!TextUtils.isEmpty(clientPackageName)) {
                onBind(new TargetImp(clientPackageName));
            } else {
                onBind((Target) null);
            }

            if (intent.getBooleanExtra(Constants.TAG_DEATH_RECIPIENT, false))
                handler.postDelayed(this::onRestart, ON_RESTART_DELAY_MILLIS);
        } else {
            onBind((Target) null);
        }

        return new AIDL();
    }

    @Override
    public final boolean onUnbind(Intent intent) {
        if (intent != null) {
            String clientPackageName = intent.getStringExtra(Constants.TAG_PACKAGE_NAME);

            IPCLog.w(TAG, "onUnbind clientPackageName:" + clientPackageName);

            if (!TextUtils.isEmpty(clientPackageName)) {
                onUnbind(new TargetImp(clientPackageName));
            } else {
                onUnbind((Target) null);
            }
        } else {
            onUnbind((Target) null);
        }

        return super.onUnbind(intent);
    }

    @CallSuper
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        IPCLog.w(TAG, "onStartCommand");
        startForeground();
        return super.onStartCommand(intent, flags, startId);
    }

    @CallSuper
    public void onDestroy() {
        IPCLog.w(TAG, "onDestroy");
        super.onDestroy();
        proxies.clear();
        reflect.clear();
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
     * 调用所有客户端 invokeToAll
     */
    @Override
    public final void invokeAllAsync(Object... objects) {
        IPCLog.w(TAG, "invokeAllAsync start proxies size : " + proxies.size());
        for (String key : proxies.keySet()) {
            try {
                IPCLog.w(TAG, "invokeAllAsync step 1 , proxy size : " + Objects.requireNonNull(proxies.get(key)).size());
                for (IProxyAidlInterface proxy : Objects.requireNonNull(proxies.get(key))) {
                    try {
                        IPCLog.w(TAG, "invokeAllAsync step 2 proxy : " + proxy);
                        if (proxy != null)
                            proxy.sendMessageVoidAsync(reflect.create(MessageBean.WHAT_STANDARD, false, objects));
                    } catch (DeadObjectException e) {
                        IPCLog.e(TAG, "Exception:" + e.getMessage());
                        IPCLog.printStackTrace(e);
                        try {
                            Objects.requireNonNull(proxies.get(key)).remove(proxy);
                        } catch (NullPointerException ignore) {
                        }
                    } catch (RemoteException e) {
                        IPCLog.e(TAG, "Exception:" + e.getMessage());
                        IPCLog.printStackTrace(e);
                    }
                }
            } catch (NullPointerException e) {
                IPCLog.printStackTrace(e);
            }
        }
        IPCLog.w(TAG, "invokeAllAsync end");
    }


    /**
     * 调用所有客户端 invokeToAll
     */
    @Override
    public final void invokeAllSync(Object... objects) {
        IPCLog.w(TAG, "invokeAllSync start proxies size : " + proxies.size());
        for (String key : proxies.keySet()) {
            try {
                IPCLog.w(TAG, "invokeAllSync step 1 , proxy size : " + Objects.requireNonNull(proxies.get(key)).size());
                for (IProxyAidlInterface proxy : Objects.requireNonNull(proxies.get(key))) {
                    try {
                        IPCLog.w(TAG, "invokeAllSync step 2 proxy : " + proxy);
                        if (proxy != null)
                            proxy.sendMessage(reflect.create(MessageBean.WHAT_STANDARD, false, objects));
                    } catch (DeadObjectException e) {
                        IPCLog.e(TAG, "Exception:" + e.getMessage());
                        IPCLog.printStackTrace(e);
                        try {
                            Objects.requireNonNull(proxies.get(key)).remove(proxy);
                        } catch (NullPointerException ignore) {
                        }
                    } catch (RemoteException e) {
                        IPCLog.e(TAG, "Exception:" + e.getMessage());
                        IPCLog.printStackTrace(e);
                    }
                }
            } catch (NullPointerException e) {
                IPCLog.printStackTrace(e);
            }
        }
    }


    /**
     * 调用指定客户端 invokeToTarget
     */
    @Override
    public final void invokeToAsync(Target target, Object... objects) {
        IPCLog.w(TAG, "invokeToAsync start target : " + target);
        try {
            IPCLog.w(TAG, "invokeToAsync step 1 , proxy size : " + Objects.requireNonNull(proxies.get(target.toString())).size());
            for (IProxyAidlInterface proxy : Objects.requireNonNull(proxies.get(target.toString()))) {
                try {
                    IPCLog.w(TAG, "invokeToAsync step 2 proxy : " + proxy);
                    if (proxy != null)
                        proxy.sendMessageVoidAsync(reflect.create(MessageBean.WHAT_STANDARD, false, objects));
                } catch (DeadObjectException e) {
                    IPCLog.e(TAG, "Exception:" + e.getMessage());
                    IPCLog.printStackTrace(e);
                    try {
                        Objects.requireNonNull(proxies.get(target.toString())).remove(proxy);
                    } catch (NullPointerException ignore) {
                    }
                } catch (RemoteException e) {
                    IPCLog.e(TAG, "Exception:" + e.getMessage());
                    IPCLog.printStackTrace(e);
                }
            }
        } catch (NullPointerException e) {
            IPCLog.printStackTrace(e);
        }
    }


    /**
     * 调用指定客户端 invokeToTarget
     */
    @Override
    public final void invokeToSync(Target target, Object... objects) {
        IPCLog.w(TAG, "invokeToSync start target : " + target);
        try {
            IPCLog.w(TAG, "invokeToSync step 1 , proxy size : " + Objects.requireNonNull(proxies.get(target.toString())).size());
            for (IProxyAidlInterface proxy : Objects.requireNonNull(proxies.get(target.toString()))) {
                try {
                    IPCLog.w(TAG, "invokeToSync step 2 proxy : " + proxy);
                    if (proxy != null)
                        proxy.sendMessage(reflect.create(MessageBean.WHAT_STANDARD, false, objects));
                } catch (DeadObjectException e) {
                    IPCLog.e(TAG, "Exception:" + e.getMessage());
                    IPCLog.printStackTrace(e);
                    try {
                        Objects.requireNonNull(proxies.get(target.toString())).remove(proxy);
                    } catch (NullPointerException ignore) {
                    }
                } catch (RemoteException e) {
                    IPCLog.e(TAG, "Exception:" + e.getMessage());
                    IPCLog.printStackTrace(e);
                }
            }
        } catch (NullPointerException e) {
            IPCLog.printStackTrace(e);
        }
    }


    /**
     * 调用指定客户端有返回值 invokeWithBack
     */
    @Override
    public final <T> T invokeToReturn(Class<T> tClass, Target target, Object... objects) {
        IPCLog.w(TAG, "invokeToReturn start target : " + target);
        try {
            IPCLog.w(TAG, "invokeToReturn step 1 , proxy size : " + Objects.requireNonNull(proxies.get(target.toString())).size());
            for (IProxyAidlInterface proxy : Objects.requireNonNull(proxies.get(target.toString()))) {
                try {
                    IPCLog.w(TAG, "invokeToReturn step 2 proxy : " + proxy);
                    if (proxy != null) {
                        MessageBean messageBean = proxy.sendMessage(reflect.create(MessageBean.WHAT_RETURN, false, objects));
                        Object object = messageBean.getReturn();
                        if (object instanceof Throwable) {
                            //对端业务方法抛异常：不向调用方抛异常，记录日志并通知监听器，最后返回默认值
                            Throwable cause = (Throwable) object;
                            IPCLog.e(TAG, "remote method '" + messageBean.getTarget() + "' threw an exception");
                            IPCLog.printStackTrace(cause);
                            onException(cause);
                            continue;
                        }
                        if (object != null) {
                            return (T) object;
                        }
                    }
                } catch (DeadObjectException e) {
                    IPCLog.e(TAG, "Exception:" + e.getMessage());
                    IPCLog.printStackTrace(e);
                    try {
                        Objects.requireNonNull(proxies.get(target.toString())).remove(proxy);
                    } catch (NullPointerException ignore) {
                    }
                } catch (RemoteException e) {
                    IPCLog.e(TAG, "Exception:" + e.getMessage());
                    IPCLog.printStackTrace(e);
                }
            }
        } catch (NullPointerException e) {
            IPCLog.printStackTrace(e);
        }
        return reflect.GenerateT(tClass);
    }


    private void startForeground() {
        IPCLog.w(TAG, "startForeground");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(getChannelID(), getChannelName(), NotificationManager.IMPORTANCE_MIN);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(notificationChannel);
            startForeground(1, new Notification.Builder(this, getChannelID()).setSmallIcon(android.R.drawable.ic_dialog_info).build());
        }
    }


    class AIDL extends IServiceAidlInterface.Stub {

        @Override
        public void setProxy(String key, IProxyAidlInterface proxy) throws RemoteException {
            IPCLog.w(TAG, "IServiceAidlInterface setProxy key : " + key);
            if (!proxies.containsKey(key)) {
                IPCLog.w(TAG, "IServiceAidlInterface not containsKey");
                proxies.put(key, new CopyOnWriteArraySet<>());
            }
            try {
                Objects.requireNonNull(proxies.get(key)).add(proxy);
            } catch (NullPointerException e) {
                IPCLog.printStackTrace(e);
            }
        }

        @Override
        public MessageBean sendMessage(MessageBean message) throws RemoteException {
            IPCLog.w(TAG, "IServiceAidlInterface sendMessage : " + message);
            return reflect.reflect(message);
        }

        @Override
        public void sendMessageAsync(MessageBean message, IProxyAidlInterface proxy) throws RemoteException {
            IPCLog.w(TAG, "IServiceAidlInterface sendMessageAsync : " + message);
            //oneway 消息放到工作线程池执行，避免业务代码占用 binder 线程
            IPCBase.asyncExecutor().execute(() -> reflect.reflectAsync(message, proxy));
        }

        @Override
        public void sendMessageVoidAsync(MessageBean message) throws RemoteException {
            IPCLog.w(TAG, "IServiceAidlInterface sendMessageVoidAsync : " + message);
            //oneway 消息放到工作线程池执行，避免业务代码占用 binder 线程
            IPCBase.asyncExecutor().execute(() -> reflect.reflect(message));
        }

    }
}
