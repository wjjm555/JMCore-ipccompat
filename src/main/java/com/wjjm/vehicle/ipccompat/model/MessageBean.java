package com.wjjm.vehicle.ipccompat.model;

import android.os.Bundle;
import android.os.IInterface;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntDef;

import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public class MessageBean implements Parcelable {

    /**
     * invoke添加回调，会走WHAT_CALLBACK，然后根据proxy进行回调。
     */
    public static final int WHAT_STANDARD = 0xA, WHAT_RETURN = 0xB, WHAT_CALLBACK = 0xC, WHAT_INTERFACE = 0xD, WHAT_CUSTOM_INTERFACE = 0xE, WHAT_STANDARD_ASYNC = 0xF;

    @IntDef({WHAT_STANDARD, WHAT_RETURN, WHAT_CALLBACK, WHAT_INTERFACE, WHAT_CUSTOM_INTERFACE, WHAT_STANDARD_ASYNC})
    @Retention(RetentionPolicy.SOURCE)
    public @interface WhatType {
    }

    private static final String KEY_TARGET = "TAR", KEY_ID = "ID", KEY_RETURN = "RETURN", KEY_PACKAGE = "PACKAGE", KEY_STUB = "STUB";

    private int what;

    private ArrayList<Object> list = new ArrayList<>();

    private Bundle data = new Bundle();

    private MessageBean() {

    }

    protected MessageBean(Parcel in) {
        data = in.readBundle(getClass().getClassLoader());
        list = in.readArrayList(getClass().getClassLoader());
        what = in.readInt();
    }

    public static final Creator<MessageBean> CREATOR = new Creator<MessageBean>() {
        @Override
        public MessageBean createFromParcel(Parcel in) {
            return new MessageBean(in);
        }

        @Override
        public MessageBean[] newArray(int size) {
            return new MessageBean[size];
        }
    };

    public ArrayList<Object> getList() {
        return list;
    }

    private void setList(ArrayList<Object> list) {
        this.list = list;
    }

    public String getTarget() {
        return data.getString(KEY_TARGET);
    }

    public Object getReturn() {
        return data.get(KEY_RETURN);
    }

    public String getID() {
        return data.getString(KEY_ID);
    }

    public String getPackage() {
        return data.getString(KEY_PACKAGE);
    }

    public String getStub() {
        return data.getString(KEY_STUB);
    }

    public Bundle getData() {
        return data;
    }

    private void setData(Bundle data) {
        this.data.clear();
        this.data.putAll(data);
    }

    public int getWhat() {
        return what;
    }

    private void setWhat(@WhatType int what) {
        this.what = what;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeBundle(data);
        dest.writeArray(list.toArray());
        dest.writeInt(what);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessageBean bean = (MessageBean) o;
        return what == bean.what && (list != null && list.equals(bean.list)) && (data != null && data.equals(bean.data));
    }

    @Override
    public int hashCode() {
        return Objects.hash(what, list, data);
    }

    public static final class Builder {

        Bundle data = new Bundle();

        ArrayList<Object> list = new ArrayList<>();

        int what;

        public MessageBean build() {
            MessageBean bean = new MessageBean();
            generateID();
            bean.setList(list);
            bean.setData(data);
            bean.setWhat(what);
            return bean;
        }

        public Builder setTarget(String target) {
            data.putString(KEY_TARGET, target);
            return this;
        }

        public Builder setPackage(String packageName) {
            data.putString(KEY_PACKAGE, packageName);
            return this;
        }

        public Builder setStub(String stub) {
            data.putString(KEY_STUB, stub);
            return this;
        }

        public Builder setReturn(Object object) {
            if (object instanceof Parcelable) {
                data.putParcelable(KEY_RETURN, (Parcelable) object);
            } else if (object instanceof Serializable) {
                data.putSerializable(KEY_RETURN, (Serializable) object);
            } else if (object instanceof byte[]) {
                data.putByteArray(KEY_RETURN, (byte[]) object);
            } else if (object instanceof short[]) {
                data.putShortArray(KEY_RETURN, (short[]) object);
            } else if (object instanceof char[]) {
                data.putCharArray(KEY_RETURN, (char[]) object);
            } else if (object instanceof float[]) {
                data.putFloatArray(KEY_RETURN, (float[]) object);
            } else if (object instanceof int[]) {
                data.putIntArray(KEY_RETURN, (int[]) object);
            } else if (object instanceof long[]) {
                data.putLongArray(KEY_RETURN, (long[]) object);
            } else if (object instanceof double[]) {
                data.putDoubleArray(KEY_RETURN, (double[]) object);
            } else if (object instanceof boolean[]) {
                data.putBooleanArray(KEY_RETURN, (boolean[]) object);
            } else if (object instanceof String[]) {
                data.putStringArray(KEY_RETURN, (String[]) object);
            } else if (object instanceof Queue) {
                data.putSerializable(KEY_RETURN, new LinkedList<>((Queue<?>) object));
            } else if (object instanceof List) {
                data.putSerializable(KEY_RETURN, new ArrayList<>((List<?>) object));
            } else if (object instanceof Set) {
                data.putSerializable(KEY_RETURN, new HashSet<>((Set<?>) object));
            } else if (object instanceof Map) {
                data.putSerializable(KEY_RETURN, new HashMap<>((Map<?, ?>) object));
            } else if (object != null) {
                //不支持的类型直接抛出，避免返回值被静默丢弃
                throw new IllegalArgumentException("MessageBean setReturn unsupported type : " + object.getClass().getName());
            }
            return this;
        }

        /**
         * @param variable 是否是可变参，但是实际使用中几乎无人使用可变参，并且如果有可变参数就只能只有可变参数，有些鸡肋，从入口屏蔽
         */
        public Builder setData(boolean variable, Object... objects) {
            if (variable)
                list.add(objects);
            else
                list.addAll(Arrays.asList(objects));
            return this;
        }

        public Builder setData(IInterface iInterface, boolean variable, Object... objects) {
            list.add(iInterface);
            if (variable)
                list.add(objects);
            else
                list.addAll(Arrays.asList(objects));
            return this;
        }

        public Builder cloneID(MessageBean messageBean) {
            data.putString(KEY_ID, messageBean.getID());
            return this;
        }

        public Builder setWhat(@WhatType int what) {
            this.what = what;
            return this;
        }

        private void generateID() {
            if (!data.containsKey(KEY_ID))
                data.putString(KEY_ID, UUID.randomUUID().toString());
        }
    }
}
