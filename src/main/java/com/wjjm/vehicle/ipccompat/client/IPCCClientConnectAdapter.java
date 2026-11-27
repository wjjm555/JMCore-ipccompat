package com.wjjm.vehicle.ipccompat.client;

import static com.wjjm.vehicle.ipccompat.Constants.BindingDied;
import static com.wjjm.vehicle.ipccompat.Constants.Disconnected;
import static com.wjjm.vehicle.ipccompat.Constants.NullBinding;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;

import com.wjjm.vehicle.ipccompat.Constants;
import com.wjjm.vehicle.ipccompat.interfaces.IPCConnectAdapter;
import com.wjjm.vehicle.ipccompat.interfaces.IPCConnectListener;
import com.wjjm.vehicle.ipccompat.util.IPCLog;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IPCCClientConnectAdapter implements IPCConnectAdapter {

    final String TAG = "IPCCompat_ConnectAdapter";

    private volatile IPCConnectListener listener;

    private volatile boolean connected = false, connecting = false;

    private volatile IBinder service;

    private volatile MyDeathRecipient deathRecipient;

    private volatile ServiceConnection mConnection;

    private ExecutorService executorServiceAPI29;

    private volatile long autoRegisterDelay = Constants.AutoRegisterDelay;

    private volatile boolean isAutoRegister = false;

    /**
     * 每次发起连接或断开时递增，用于使上一次连接的过期回调失效，
     * 防止旧连接的 binderDied/onServiceDisconnected 破坏新连接的状态
     */
    private volatile int connectGeneration;

    private final Handler handler;

    IPCCClientConnectAdapter(IPCConnectListener listener) {
        setListener(listener);
        HandlerThread handlerThreadAPI24 = new HandlerThread("IPCCClientConnectAdapter");
        handlerThreadAPI24.start();
        handler = new Handler(handlerThreadAPI24.getLooper());
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void setListener(IPCConnectListener listener) {
        this.listener = listener;
    }

    public synchronized boolean connect(Context context, String packageName, String className, boolean linkToDeath, long autoRegisterDelay) {
        return connect(context, packageName, className, linkToDeath, autoRegisterDelay, false);
    }

    @Override
    public synchronized boolean connect(Context context, String packageName, String className, boolean isAutoRegister, long autoRegisterDelay, boolean isDeathRecipient) {
        IPCLog.w(TAG, "connect start");
        if (!connected && !connecting) {
            IPCLog.w(TAG, "connect step 1");
            this.isAutoRegister = isAutoRegister;
            this.autoRegisterDelay = autoRegisterDelay;
            connecting = true;
            int generation = ++connectGeneration;
            Intent intent = new Intent();
            intent.putExtra(Constants.TAG_DEATH_RECIPIENT, isDeathRecipient);
            intent.putExtra(Constants.TAG_PACKAGE_NAME, context.getPackageName());
            intent.setClassName(packageName, className);
            ServiceConnection connection = new MyServiceConnection(generation);
            mConnection = connection;
            boolean success;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                IPCLog.w(TAG, "connect step 2.1");
                if (executorServiceAPI29 == null)
                    executorServiceAPI29 = Executors.newSingleThreadExecutor();
                success = context.bindService(intent, Context.BIND_AUTO_CREATE, executorServiceAPI29, connection);
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                IPCLog.w(TAG, "connect step 2.2");
                success = bindService(context, intent, connection, Context.BIND_AUTO_CREATE);
            } else {
                IPCLog.w(TAG, "connect step 2.3 , Under 24 api synchronization methods can cause thread deadlock");
                //TODO Under 24 api , synchronization methods can cause thread deadlock
                success = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            }
            IPCLog.w(TAG, "connect end , success : " + success);
            if (!success) connecting = false;
            return success;
        }
        IPCLog.w(TAG, "connect end");
        return true;
    }

    @Override
    public synchronized void disconnect(Context context) {
        IPCLog.w(TAG, "disconnect start");
        boolean wasConnected = connected;
        IBinder binder = service;
        MyDeathRecipient recipient = deathRecipient;
        if (binder != null && recipient != null) {
            try {
                binder.unlinkToDeath(recipient, 0);
            } catch (Exception e) {
                IPCLog.e(TAG, "Exception:" + e.getMessage());
                IPCLog.printStackTrace(e);
            }
        }
        isAutoRegister = false;
        connected = false;
        connecting = false;
        service = null;
        deathRecipient = null;
        connectGeneration++;
        ServiceConnection connection = mConnection;
        if (connection != null) {
            try {
                context.unbindService(connection);
            } catch (Exception e) {
                IPCLog.e(TAG, "Exception:" + e.getMessage());
            }
        }
        IPCLog.w(TAG, "disconnect end");
        if (wasConnected && listener != null) listener.onDisconnected(Disconnected);
    }

    @Override
    public void start(Context context, String packageName, String className) {
        IPCLog.w(TAG, "start start");
        if (context != null) {
            IPCLog.w(TAG, "start step 1");
            Intent intent = new Intent();
            intent.setClassName(packageName, className);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                IPCLog.w(TAG, "start step 2.1");
                context.startForegroundService(intent);
            } else {
                IPCLog.w(TAG, "start step 2.2");
                context.startService(intent);
            }
        }
        IPCLog.w(TAG, "start end");
    }

    private boolean bindService(Context context, Intent intent, ServiceConnection mConnection, int flag) {
        if (context instanceof ContextWrapper) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        setHiddenApiExemptions();

        try {
            Method method = context.getClass().getMethod("bindServiceAsUser", Intent.class, ServiceConnection.class, int.class, Handler.class, UserHandle.class);
            method.setAccessible(true);
            Object success = method.invoke(context, intent, mConnection, flag, handler, android.os.Process.myUserHandle());
            return (boolean) success;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            IPCLog.e(TAG, "Exception:" + e.getMessage());
            IPCLog.printStackTrace(e);
        }
        return false;
    }

    private void setHiddenApiExemptions() {
        try {
            Method forName = Class.class.getDeclaredMethod("forName", String.class);
            Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
            Class<?> vmRuntimeClass = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
            Method getRuntime = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null);
            Method setHiddenApiExemptions = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions", new Class[]{String[].class});
            Object sVmRuntime = getRuntime.invoke(null);
            setHiddenApiExemptions.invoke(sVmRuntime, new Object[]{new String[]{"L"}});
        } catch (Throwable e) {
            IPCLog.e(TAG, "Exception:" + e.getMessage());
            IPCLog.printStackTrace(e);
        }
    }

    private class MyServiceConnection implements ServiceConnection {

        final int generation;

        MyServiceConnection(int generation) {
            this.generation = generation;
        }

        private boolean isCurrent() {
            return generation == connectGeneration;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            IPCLog.w(TAG, "onServiceConnected:" + mConnection);
            if (!isCurrent()) return;
            connected = true;
            connecting = false;
            service = binder;
            if (isAutoRegister) {
                MyDeathRecipient recipient = new MyDeathRecipient(binder, generation);
                deathRecipient = recipient;
                try {
                    binder.linkToDeath(recipient, 0);
                } catch (RemoteException e) {
                    IPCLog.e(TAG, "Exception:" + e.getMessage());
                    IPCLog.printStackTrace(e);
                }
            }
            if (listener != null) listener.onConnected(binder);
            if (listener != null) listener.onConnectedFinish();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            IPCLog.w(TAG, "onServiceDisconnected:" + mConnection);
            if (!isCurrent()) return;
            connected = false;
            connecting = false;
            service = null;
            if (listener != null) listener.onDisconnected(Disconnected);
        }

        @Override
        public void onBindingDied(ComponentName name) {
            IPCLog.w(TAG, "onBindingDied:" + mConnection);
            if (!isCurrent()) return;
            connected = false;
            connecting = false;
            service = null;
            if (listener != null) listener.onDisconnected(BindingDied);
        }

        @Override
        public void onNullBinding(ComponentName name) {
            IPCLog.w(TAG, "onNullBinding:" + mConnection);
            if (!isCurrent()) return;
            connected = false;
            connecting = false;
            service = null;
            if (listener != null) listener.onDisconnected(NullBinding);
        }
    }

    private class MyDeathRecipient implements IBinder.DeathRecipient {
        final IBinder binder;
        final int generation;

        MyDeathRecipient(IBinder binder, int generation) {
            this.binder = binder;
            this.generation = generation;
        }

        @Override
        public void binderDied() {
            IPCLog.w(TAG, "binderDied start");
            boolean current;
            synchronized (IPCCClientConnectAdapter.this) {
                // 只处理当前连接的死亡通知，重连/断开后过期连接的通知只做解注册
                current = generation == connectGeneration;
                if (current) {
                    service = null;
                    connected = false;
                    connecting = false;
                }
            }
            try {
                binder.unlinkToDeath(this, 0);
            } catch (Exception e) {
                IPCLog.e(TAG, "Exception:" + e.getMessage());
                IPCLog.printStackTrace(e);
            }
            if (!current) return;
            IPCLog.w(TAG, "binderDied isAutoRegister : " + isAutoRegister + " autoRegisterDelay:" + autoRegisterDelay);
            if (isAutoRegister) {
                handler.postDelayed(() -> {
                    IPCLog.w(TAG, "binderDied postDelayed");
                    // 延迟期间可能已经通过其他方式重连成功，避免重复触发重连
                    if (listener != null && !connected)
                        listener.onBinderDied();
                }, autoRegisterDelay);
            } else if (listener != null) {
                listener.onBinderDied();
            }
        }
    }

}
