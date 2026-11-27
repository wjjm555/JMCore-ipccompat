package com.wjjm.vehicle.ipccompat.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Arrays;

public class MessageBeanTest {

    @Test
    public void data_roundtripsThroughList() {
        MessageBean bean = new MessageBean.Builder()
                .setData(false, "hello", 42)
                .setWhat(MessageBean.WHAT_STANDARD)
                .build();
        assertEquals(MessageBean.WHAT_STANDARD, bean.getWhat());
        assertEquals(Arrays.asList("hello", 42), bean.getList());
    }

    @Test
    public void setReturn_null_doesNotThrow() {
        assertNotNull(new MessageBean.Builder().setReturn(null).build());
    }

    @Test
    public void setReturn_supportedTypes_doNotThrow() {
        assertNotNull(new MessageBean.Builder().setReturn("ok").build());
        assertNotNull(new MessageBean.Builder().setReturn(Arrays.asList(1, 2)).build());
        assertNotNull(new MessageBean.Builder().setReturn(new int[]{1, 2}).build());
    }

    @Test
    public void setReturn_unsupportedType_throws() {
        try {
            new MessageBean.Builder().setReturn(new Object());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }
}
