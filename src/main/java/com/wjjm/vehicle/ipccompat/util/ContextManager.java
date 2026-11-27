package com.wjjm.vehicle.ipccompat.util;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

public class ContextManager {

    private static volatile Context context;

    private static volatile Application application;

    /**
     * 显式初始化，推荐在 Application.onCreate 中调用。
     * 不调用时会通过反射兜底获取，反射失败则会抛出带提示的异常。
     */
    public static void init(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        ContextManager.context = appContext;
        if (appContext instanceof Application)
            ContextManager.application = (Application) appContext;
    }

    public static Context getContext() {
        Context appContext = context;
        if (appContext != null) return appContext;
        Application app = getCApplication();
        if (app != null) return app.getBaseContext();
        throw new IllegalStateException("ContextManager is not initialized, please call ContextManager.init(context) in your Application");
    }

    @SuppressLint("PrivateApi")
    public static Application getMApplication() {
        Application app = application;
        if (app != null) return app;
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread;
            activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
            @SuppressLint("DiscouragedPrivateApi") Field activitiesField = activityThreadClass.getDeclaredField("mInitialApplication");
            activitiesField.setAccessible(true);
            return (Application) activitiesField.get(activityThread);
        } catch (IllegalAccessException | NoSuchFieldException | ClassNotFoundException |
                 InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }


    @SuppressLint("PrivateApi")
    public static Application getCApplication() {
        try {
            return (Application) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null, (Object[]) null);
        } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException |
                 NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }
}
