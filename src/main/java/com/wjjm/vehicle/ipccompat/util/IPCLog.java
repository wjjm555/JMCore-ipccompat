package com.wjjm.vehicle.ipccompat.util;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

public class IPCLog {

    private static boolean DEBUG = false;

    private static final String ACTION = "com.wjjm.vehicle.ipccompat.log";

    /**
     * 整个进程只动态注册一个接收器，避免多个接收器同时切换开关导致状态互相抵消
     */
    private static volatile boolean registered;

    @SuppressLint("UnspecifiedRegisterReceiverFlag") //RECEIVER_EXPORTED 在 API 33 才可用,低版本分支无法携带 flag
    public static void init() {
        if (registered) return;
        synchronized (IPCLog.class) {
            if (registered) return;
            try {
                IntentFilter filter = new IntentFilter();
                filter.addAction(ACTION);
                Context context = ContextManager.getContext();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(new IPCLogBroadcastReceiver(), filter, Context.RECEIVER_EXPORTED);
                } else {
                    context.registerReceiver(new IPCLogBroadcastReceiver(), filter);
                }
                registered = true;
            } catch (Throwable t) {
                //无 Context 时跳过动态注册，不影响库的正常使用
            }
        }
    }

    public static void whatever(String tag, String msg) {
        Log.e(tag, msg);
    }

    public static void e(String tag, String msg) {
        if (DEBUG) {
            Log.e(tag, msg);
        }
    }

    public static void w(String tag, String msg) {
        if (DEBUG) {
            Log.w(tag, msg);
        }
    }

    public static void d(String tag, String msg) {
        if (DEBUG) {
            Log.d(tag, msg);
        }
    }

    public static void i(String tag, String msg) {
        if (DEBUG) {
            Log.i(tag, msg);
        }
    }

    public static void v(String tag, String msg) {
        if (DEBUG) {
            Log.v(tag, msg);
        }
    }

    public static void printStackTrace(Throwable throwable) {
        if (DEBUG && throwable != null) throwable.printStackTrace();
    }

    public static void printStackTraceWhatever(Throwable throwable) {
        if (throwable != null) throwable.printStackTrace();
    }

    public static void setDEBUG(boolean debug) {
        DEBUG = debug;
    }

    /**
     * 切换开关：am broadcast -a com.wjjm.vehicle.ipccompat.log --es "p" "your package name"
     * 显式设置：am broadcast -a com.wjjm.vehicle.ipccompat.log --es "p" "your package name" --es "d" "true"
     */
    static class IPCLogBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String packageName = context.getPackageName();
            if (intent != null) {
                String actionName = intent.getAction();
                String targetPackageName = intent.getStringExtra("p");

                boolean hit = TextUtils.equals(actionName, ACTION) && !TextUtils.isEmpty(packageName) && TextUtils.equals(packageName, targetPackageName);

                Log.w("IPCCompat_Log", String.format("Hit %b, currentPackageName is %s , targetPackageName is %s", hit, packageName, targetPackageName));

                if (hit) {
                    String debug = intent.getStringExtra("d");
                    if (TextUtils.isEmpty(debug)) {
                        DEBUG = !DEBUG;
                    } else {
                        DEBUG = Boolean.parseBoolean(debug);
                    }
                    Log.w("IPCCompat_Log", packageName + " IPC Log Open : " + DEBUG);
                }
            }
        }
    }
}
