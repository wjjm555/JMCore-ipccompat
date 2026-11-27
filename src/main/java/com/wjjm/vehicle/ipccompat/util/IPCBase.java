package com.wjjm.vehicle.ipccompat.util;

import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import com.wjjm.vehicle.ipccompat.IProxyAidlInterface;
import com.wjjm.vehicle.ipccompat.interfaces.ExceptionListener;
import com.wjjm.vehicle.ipccompat.interfaces.IPCCallBack;
import com.wjjm.vehicle.ipccompat.interfaces.IPCInterface;
import com.wjjm.vehicle.ipccompat.interfaces.IPCReflect;
import com.wjjm.vehicle.ipccompat.interfaces.Target;
import com.wjjm.vehicle.ipccompat.model.MessageBean;
import com.wjjm.vehicle.ipccompat.model.TargetImp;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class IPCBase implements IPCReflect {

    final String TAG = "IPCCompat_IPCBase";

    static final String JAVA_AUTO_GENERATION = "access$";

    private static final String FRAMEWORK_PACKAGE = "com.wjjm.vehicle.ipccompat.";

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    /**
     * 处理 oneway 消息的工作线程池，避免业务方法占用 binder 线程
     */
    private static final ExecutorService ASYNC_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "IPCCompat-Async-" + THREAD_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private final List<? super IPCInterface> interfaces = new CopyOnWriteArrayList<>();

    private final Map<String, IPCCallBack> callBackMap = new ConcurrentHashMap<>();

    private final Map<MethodKey, Method> methodCache = new ConcurrentHashMap<>();

    private ExceptionListener ExceptionListener;

    public IPCBase() {
        IPCLog.init();
    }

    public IPCBase(ExceptionListener ExceptionListener) {
        this();
        setExceptionListener(ExceptionListener);
    }

    public void setExceptionListener(ExceptionListener ExceptionListener) {
        this.ExceptionListener = ExceptionListener;
    }

    public static ExecutorService asyncExecutor() {
        return ASYNC_EXECUTOR;
    }

    @Override
    @SafeVarargs
    public final <T extends IPCInterface> void addInterface(T... t) {
        interfaces.addAll(Arrays.asList(t));
    }

    @Override
    @SafeVarargs
    public final <T extends IPCInterface> void removeInterface(T... t) {
        interfaces.removeAll(Arrays.asList(t));
    }

    @Override
    public MessageBean create(@MessageBean.WhatType int what, boolean variable, Object... objects) {
        return new MessageBean.Builder().setData(variable, objects).setWhat(what).setTarget(getMethodName()).build();
    }

    @Override
    public MessageBean create(@MessageBean.WhatType int what, String packageName, boolean variable, Object... objects) {
        return new MessageBean.Builder().setData(variable, objects).setWhat(what).setPackage(packageName).setTarget(getMethodName()).build();
    }

    @Override
    public MessageBean create(@MessageBean.WhatType int what, String packageName, String stub, IInterface iInterface, boolean variable, Object... objects) {
        return new MessageBean.Builder().setData(iInterface, variable, objects).setWhat(what).setPackage(packageName).setStub(stub).setTarget(getMethodName()).build();
    }

    @Override
    public <T> MessageBean create(IPCCallBack<T> callBack, boolean isRemove, @MessageBean.WhatType int what, boolean variable, Object... objects) {
        MessageBean messageBean = new MessageBean.Builder().setData(variable, objects).setWhat(what).setTarget(getMethodName()).build();
        if (isRemove)
            callBackMap.entrySet().removeIf(entry -> callBack.equals(entry.getValue()));
        else
            callBackMap.put(messageBean.getID(), callBack);
        return messageBean;
    }

    @Override
    public MessageBean reflect(MessageBean messageBean) {
        IPCLog.w(TAG, "reflect start");
        ArrayList<Object> objects = messageBean.getList();
        Class<?>[] parameterTypes = analysisClass(objects);
        String methodName = messageBean.getTarget();

        boolean NoSuchMethod = false;

        for (Object t : interfaces) {
            Method method = findMethod(t.getClass(), methodName, parameterTypes);
            if (method == null) {
                IPCLog.e(TAG, "reflect NoSuchMethodException:" + methodName);
                NoSuchMethod = true;
                continue;
            }
            try {
                Object object = method.invoke(t, objects.toArray());
                return new MessageBean.Builder().setReturn(object).build();
            } catch (IllegalAccessException | InvocationTargetException e) {
                //把业务异常作为返回值带回，调用方可以区分成功与失败，不再静默吞掉
                return reflectException(e);
            } catch (IllegalArgumentException e) {
                //返回值类型不受 Binder 支持：同样作为异常带回，避免服务进程崩溃
                return reflectException(e);
            }
        }

        IPCLog.w(TAG, "reflect end");

        if (NoSuchMethod)
            return reflectTryTarget(objects, parameterTypes, methodName, messageBean.getPackage());

        //未注册任何接口时同样视为方法未找到，保证 onMethodNotFound 语义对称
        if (interfaces.isEmpty() && ExceptionListener != null)
            ExceptionListener.onMethodNotFound(methodName);

        return new MessageBean.Builder().build();
    }

    @Override
    public void reflectAsync(final MessageBean messageBean, final IProxyAidlInterface proxy) {
        IPCLog.w(TAG, "reflectAsync start");

        ArrayList<Object> objects = messageBean.getList();
        boolean isAsync = MessageBean.WHAT_CALLBACK == messageBean.getWhat();
        Class<?>[] parameterTypes = analysisClass(objects, isAsync);
        String methodName = messageBean.getTarget();

        if (isAsync) {
            IPCLog.w(TAG, "reflectAsync isAsync isIPCCallBack");
            objects.add(0, (IPCCallBack<Object>) object -> {
                try {
                    proxy.messageIPCCallBack(new MessageBean.Builder().cloneID(messageBean).setReturn(object).build());
                } catch (RemoteException e) {
                    IPCLog.e(TAG, "Exception:" + e.getMessage());
                }
            });
        } else {
            IPCLog.w(TAG, "reflectAsync analysisInterface");
            analysisInterface(objects, parameterTypes, messageBean.getStub());
        }
        boolean NoSuchMethod = false;
        for (Object t : interfaces) {
            Method method = findMethod(t.getClass(), methodName, parameterTypes);
            if (method == null) {
                IPCLog.e(TAG, "reflectAsync NoSuchMethodException:" + methodName);
                NoSuchMethod = true;
                continue;
            }
            try {
                method.invoke(t, objects.toArray());
                return;
            } catch (IllegalAccessException | InvocationTargetException e) {
                reflectAsyncException(e, proxy, messageBean, isAsync);
                return;
            }
        }
        if ((NoSuchMethod || interfaces.isEmpty()) && ExceptionListener != null)
            ExceptionListener.onMethodNotFound(methodName);

        IPCLog.w(TAG, "reflectAsync end , NoSuchMethod : " + NoSuchMethod);
    }

    @Override
    public <T> void reflectBack(final MessageBean messageBean) {
        IPCLog.w(TAG, "reflectBack start");
        try {
            IPCCallBack<T> callBack = callBackMap.remove(messageBean.getID());
            if (callBack != null) {
                Object result = messageBean.getReturn();
                if (result instanceof Throwable) {
                    callBack.onError((Throwable) result);
                } else {
                    callBack.onResultBack((T) result);
                }
            }
        } catch (Exception e) {
            IPCLog.e(TAG, "Exception:" + e.getMessage());
            IPCLog.printStackTrace(e);
        }
        IPCLog.w(TAG, "reflectBack end");
    }

    @Override
    public <T> T GenerateT(Class<T> tClass) {
        IPCLog.w(TAG, "GenerateT tClass : " + tClass);
        IPCLog.w(TAG, "GenerateT An error occurred, generating a default value");
        if (byte.class.isAssignableFrom(tClass) || Byte.class.isAssignableFrom(tClass)) {
            return (T) new Byte((byte) -1);
        } else if (float.class.isAssignableFrom(tClass) || Float.class.isAssignableFrom(tClass)) {
            return (T) new Float(-1);
        } else if (short.class.isAssignableFrom(tClass) || Short.class.isAssignableFrom(tClass)) {
            return (T) new Short((short) -1);
        } else if (int.class.isAssignableFrom(tClass) || Integer.class.isAssignableFrom(tClass)) {
            return (T) new Integer(-1);
        } else if (long.class.isAssignableFrom(tClass) || Long.class.isAssignableFrom(tClass)) {
            return (T) new Long(-1);
        } else if (double.class.isAssignableFrom(tClass) || Double.class.isAssignableFrom(tClass)) {
            return (T) new Double(-1);
        } else if (char.class.isAssignableFrom(tClass) || Character.class.isAssignableFrom(tClass)) {
            return (T) new Character(' ');
        } else if (boolean.class.isAssignableFrom(tClass) || Boolean.class.isAssignableFrom(tClass)) {
            return (T) new Boolean(false);
        } else if (String.class.isAssignableFrom(tClass)) {
            return (T) "";
        } else {
            try {
                return tClass.newInstance();
            } catch (IllegalAccessException | InstantiationException e) {
                IPCLog.e(TAG, "Exception:" + e.getMessage());
                IPCLog.printStackTrace(e);
            }
        }
        return null;
    }

    @Override
    public void clear() {
        IPCLog.w(TAG, "clear");
        callBackMap.clear();
        interfaces.clear();
    }

    /**
     * 只清理回调缓存，保留已注册的接口
     */
    public void clearCallBacks() {
        IPCLog.w(TAG, "clearCallBacks");
        callBackMap.clear();
    }

    private MessageBean reflectTryTarget(ArrayList<Object> objects, Class<?>[] parameterTypes, String methodName, String packageName) {
        IPCLog.w(TAG, "reflectTryTarget start");
        Class<?>[] parameterTypesWithTarget = new Class<?>[parameterTypes.length + 1];
        System.arraycopy(parameterTypes, 0, parameterTypesWithTarget, 1, parameterTypes.length);
        parameterTypesWithTarget[0] = Target.class;
        Target Target = new TargetImp(packageName);
        objects.add(0, Target);
        boolean NoSuchMethod = false;
        for (Object t : interfaces) {
            Method method = findMethod(t.getClass(), methodName, parameterTypesWithTarget);
            if (method == null) {
                IPCLog.e(TAG, "reflectTryTarget NoSuchMethodException : " + methodName);
                NoSuchMethod = true;
                continue;
            }
            try {
                Object object = method.invoke(t, objects.toArray());
                return new MessageBean.Builder().setReturn(object).build();
            } catch (IllegalAccessException | InvocationTargetException e) {
                return reflectException(e);
            } catch (IllegalArgumentException e) {
                //返回值类型不受 Binder 支持：同样作为异常带回，避免服务进程崩溃
                return reflectException(e);
            }
        }

        if (NoSuchMethod && ExceptionListener != null)
            ExceptionListener.onMethodNotFound(methodName);

        IPCLog.w(TAG, "reflectTryTarget end NoSuchMethod : " + NoSuchMethod);
        return new MessageBean.Builder().build();
    }

    private Method findMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes) {
        MethodKey key = new MethodKey(clazz, methodName, parameterTypes);
        Method method = methodCache.get(key);
        if (method == null) {
            try {
                method = clazz.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ignore) {
                //实现类从基类继承方法时未在本类声明，回退查找公开继承方法
                try {
                    method = clazz.getMethod(methodName, parameterTypes);
                } catch (NoSuchMethodException ignore2) {
                }
            }
            if (method != null) {
                method.setAccessible(true);
                methodCache.put(key, method);
            }
        }
        return method;
    }

    private MessageBean reflectException(Exception e) {
        Throwable cause = unwrap(e);
        IPCLog.e(TAG, "reflect Exception:" + (cause != null ? cause.toString() : e.toString()));
        IPCLog.printStackTrace(e);
        if (ExceptionListener != null) ExceptionListener.onException(cause);
        return new MessageBean.Builder().setReturn(cause).build();
    }

    private void reflectAsyncException(Exception e, IProxyAidlInterface proxy, MessageBean messageBean, boolean isAsync) {
        Throwable cause = unwrap(e);
        IPCLog.e(TAG, "reflectAsync Exception:" + (cause != null ? cause.toString() : e.toString()));
        IPCLog.printStackTrace(e);
        if (ExceptionListener != null) ExceptionListener.onException(cause);
        //WHAT_CALLBACK 模式下把异常回传给客户端回调，避免调用方无限期等待
        if (isAsync && proxy != null) {
            try {
                proxy.messageIPCCallBack(new MessageBean.Builder().cloneID(messageBean).setReturn(cause).build());
            } catch (RemoteException re) {
                IPCLog.e(TAG, "Exception:" + re.getMessage());
            }
        }
    }

    private Throwable unwrap(Exception e) {
        return e instanceof InvocationTargetException ? e.getCause() : e;
    }

    /**
     * 从调用栈中取业务方法名：跳过本框架内部帧，取第一个非框架方法，
     * 不依赖固定的栈深度，避免不同运行环境（ART/HotSpot）及内联导致的偏移失效
     */
    private String getMethodName() {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        boolean passedSelf = false;
        for (StackTraceElement element : elements) {
            if (!passedSelf) {
                if (IPCBase.class.getName().equals(element.getClassName())
                        && "getMethodName".equals(element.getMethodName())) {
                    passedSelf = true;
                }
                continue;
            }
            String className = element.getClassName();
            if (className.startsWith(FRAMEWORK_PACKAGE)) continue;
            String name = element.getMethodName();
            if (!name.contains(JAVA_AUTO_GENERATION)) return name;
        }
        return "";
    }

    private void analysisInterface(ArrayList<Object> objects, Class<?>[] parameterTypes, String stub) {
        analysisInterface(objects, parameterTypes, stub, 0);
    }

    private void analysisInterface(ArrayList<Object> objects, Class<?>[] parameterTypes, String stub, int index) {
        try {
            Object binderProxy = objects.remove(index);
            Class<?> stubClass = Class.forName(stub);
            Method method = stubClass.getMethod("asInterface", IBinder.class);
            method.setAccessible(true);
            Object callback = method.invoke(null, binderProxy);
            objects.add(index, callback);
            //从 Proxy 实现的接口中找参数类型，不依赖 AIDL 生成代码的具体结构
            Class<?> interfaceClass = null;
            for (Class<?> clazz : callback.getClass().getInterfaces()) {
                if (IInterface.class.isAssignableFrom(clazz) && clazz != IInterface.class) {
                    interfaceClass = clazz;
                    break;
                }
            }
            if (interfaceClass == null) {
                throw new IllegalStateException("cannot resolve interface class of " + callback.getClass().getName());
            }
            parameterTypes[index] = interfaceClass;
        } catch (Exception e) {
            IPCLog.e(TAG, "analysisInterface Exception:" + e.getMessage());
        }

    }

    private Class<?>[] analysisClass(ArrayList<Object> objects) {
        return analysisClass(objects, false);
    }

    private Class<?>[] analysisClass(ArrayList<Object> objects, boolean async) {
        int length = async ? (objects.size() + 1) : objects.size();
        Class<?>[] classes = new Class[length];
        int i = 0;

        if (async) {
            classes[i++] = IPCCallBack.class;
        }

        for (; i < classes.length; ++i) {
            Object object = objects.get(async ? i - 1 : i);
            if (object == null) {
                IPCLog.e(TAG, "A parameter is null ！");
                classes[i] = Object.class;
            } else if (object instanceof Byte) {
                classes[i] = byte.class;
            } else if (object instanceof Character) {
                classes[i] = char.class;
            } else if (object instanceof Short) {
                classes[i] = short.class;
            } else if (object instanceof Integer) {
                classes[i] = int.class;
            } else if (object instanceof Long) {
                classes[i] = long.class;
            } else if (object instanceof Float) {
                classes[i] = float.class;
            } else if (object instanceof Double) {
                classes[i] = double.class;
            } else if (object instanceof Boolean) {
                classes[i] = boolean.class;
            } else if (object instanceof List) {
                classes[i] = List.class;
            } else if (object instanceof Map) {
                classes[i] = Map.class;
            } else if (object instanceof Set) {
                classes[i] = Set.class;
            } else if (object instanceof Queue) {
                classes[i] = Queue.class;
            } else {
                classes[i] = object.getClass();
            }
        }

        return classes;
    }

    private static final class MethodKey {
        final Class<?> targetClass;
        final String methodName;
        final Class<?>[] parameterTypes;

        MethodKey(Class<?> targetClass, String methodName, Class<?>[] parameterTypes) {
            this.targetClass = targetClass;
            this.methodName = methodName;
            this.parameterTypes = parameterTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MethodKey key = (MethodKey) o;
            return Objects.equals(targetClass, key.targetClass)
                    && Objects.equals(methodName, key.methodName)
                    && Arrays.equals(parameterTypes, key.parameterTypes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(targetClass, methodName) * 31 + Arrays.hashCode(parameterTypes);
        }
    }

}
