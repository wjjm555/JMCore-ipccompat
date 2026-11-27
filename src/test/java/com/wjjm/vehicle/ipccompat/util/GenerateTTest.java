package com.wjjm.vehicle.ipccompat.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class GenerateTTest {

    private final IPCBase base = new IPCBase();

    @Test
    public void numericDefaults_areMinusOne() {
        assertEquals(Integer.valueOf(-1), base.GenerateT(int.class));
        assertEquals(Integer.valueOf(-1), base.GenerateT(Integer.class));
        assertEquals(Long.valueOf(-1), base.GenerateT(long.class));
        assertEquals(Short.valueOf((short) -1), base.GenerateT(short.class));
        assertEquals(Byte.valueOf((byte) -1), base.GenerateT(byte.class));
        assertEquals(Float.valueOf(-1f), base.GenerateT(float.class));
        assertEquals(Double.valueOf(-1d), base.GenerateT(double.class));
    }

    @Test
    public void booleanDefault_isFalse() {
        assertEquals(Boolean.FALSE, base.GenerateT(boolean.class));
    }

    @Test
    public void charDefault_isSpace() {
        assertEquals(Character.valueOf(' '), base.GenerateT(char.class));
    }

    @Test
    public void stringDefault_isEmpty() {
        assertEquals("", base.GenerateT(String.class));
    }

    @Test
    public void objectDefault_isNewInstance() {
        assertNotNull(base.GenerateT(Foo.class));
    }

    @Test
    public void noDefaultConstructor_returnsNull() {
        assertNull(base.GenerateT(Bar.class));
    }

    public static class Foo {
    }

    public static class Bar {
        public Bar(String ignored) {
        }
    }
}
