package com.wjjm.vehicle.ipccompat.service;

import static org.junit.Assert.assertNotNull;

import android.app.Notification;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 25)
public class IPCServiceForegroundTest {

    public static class TestService extends IPCService {
    }

    @Test
    public void onStartCommand_belowApi26_startsForeground() {
        TestService service = Robolectric.setupService(TestService.class);
        service.onStartCommand(new Intent(), 0, 1);
        Notification notification = Shadows.shadowOf(service).getLastForegroundNotification();
        assertNotNull(notification);
    }
}
