package com.wjjm.vehicle.ipccompat;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class Constants {
    public static final int Connected = 0xA, Disconnected = 0xB, BindingDied = 0xC, NullBinding = 0xD, DeathRecipientMax = 0xE;

    @IntDef({Connected, Disconnected, BindingDied, NullBinding, DeathRecipientMax})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ServiceConnectState {
    }

    public static final String TAG_DEATH_RECIPIENT = "DeathRecipient", TAG_PACKAGE_NAME = "PackageName";

    public static final long AutoRegisterDelay = 5 * 1000;

    /**
     * 同步调用等待服务连接的超时时间，超时后不再阻塞调用线程
     */
    public static final long ConnectWaitTimeout = 10 * 1000;

}
