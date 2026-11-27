package com.wjjm.vehicle.ipccompat.util;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.wjjm.vehicle.ipccompat.interfaces.IPCInterface;
import com.wjjm.vehicle.ipccompat.model.MessageBean;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class IPCBaseReflectTest {

    public static class BoomApi implements IPCInterface {
        public Object boom() {
            return new Object();
        }
    }

    public static class BaseApi implements IPCInterface {
        public String inherited() {
            return null;
        }
    }

    public static class SubApi extends BaseApi {
    }

    @Test
    public void reflect_unsupportedReturnType_returnsThrowableInsteadOfCrashing() {
        IPCBase base = new IPCBase();
        base.addInterface(new BoomApi());
        MessageBean message = new MessageBean.Builder()
                .setTarget("boom")
                .setWhat(MessageBean.WHAT_RETURN)
                .setData(false)
                .build();

        MessageBean result = base.reflect(message);

        assertTrue(result.getReturn() instanceof Throwable);
    }

    @Test
    public void reflect_inheritedMethod_foundViaGetMethodFallback() {
        IPCBase base = new IPCBase();
        base.addInterface(new SubApi());
        MessageBean message = new MessageBean.Builder()
                .setTarget("inherited")
                .setWhat(MessageBean.WHAT_RETURN)
                .setData(false)
                .build();

        MessageBean result = base.reflect(message);

        assertNull(result.getReturn());
    }
}
