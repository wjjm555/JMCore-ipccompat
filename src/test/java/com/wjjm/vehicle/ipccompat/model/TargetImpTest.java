package com.wjjm.vehicle.ipccompat.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import com.wjjm.vehicle.ipccompat.interfaces.Target;

import org.junit.Test;

public class TargetImpTest {

    @Test
    public void getTargetAndToString_returnRawValue() {
        TargetImp target = new TargetImp("com.example.app");
        assertEquals("com.example.app", target.getTarget());
        assertEquals("com.example.app", target.toString());
    }

    @Test
    public void clone_equalsOriginal() {
        TargetImp original = new TargetImp("com.example.app");
        Target clone = original.clone();
        assertEquals(original, clone);
        assertEquals(original.hashCode(), clone.hashCode());
        assertEquals("com.example.app", clone.toString());
    }

    @Test
    public void equals_comparesByValue() {
        assertEquals(new TargetImp("a"), new TargetImp("a"));
        assertEquals(new TargetImp("a").hashCode(), new TargetImp("a").hashCode());
        assertNotEquals(new TargetImp("a"), new TargetImp("b"));
        assertNotEquals(new TargetImp("a"), new Object());
    }

    @Test
    public void nullTarget_isHandled() {
        TargetImp target = new TargetImp(null);
        assertEquals("", target.toString());
        assertNull(target.getTarget());
        assertEquals(new TargetImp(null), new TargetImp(null));
    }
}
