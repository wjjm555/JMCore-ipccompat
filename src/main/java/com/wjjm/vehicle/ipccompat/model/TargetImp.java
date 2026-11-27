package com.wjjm.vehicle.ipccompat.model;

import com.wjjm.vehicle.ipccompat.interfaces.Target;

import androidx.annotation.NonNull;

import java.util.Objects;

public class TargetImp implements Target, Cloneable {

    private final String target;

    public TargetImp(String target) {
        this.target = target;
    }

    @Override
    public final String getTarget() {
        return target;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TargetImp that = (TargetImp) o;
        return Objects.equals(target, that.target);
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(target);
    }

    @NonNull
    @Override
    public final String toString() {
        return target == null ? "" : target;
    }

    @Override
    @NonNull
    public final Target clone() {
        return new TargetImp(target);
    }
}
