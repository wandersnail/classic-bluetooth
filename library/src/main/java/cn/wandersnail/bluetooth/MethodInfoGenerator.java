package cn.wandersnail.bluetooth;


import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

import cn.wandersnail.commons.poster.MethodInfo;

/**
 * date: 2019/8/4 08:14
 * author: zengfansheng
 */
class MethodInfoGenerator {
    static MethodInfo onBluetoothAdapterStateChanged(int state) {
        return new MethodInfo("onBluetoothAdapterStateChanged", new MethodInfo.Parameter(int.class, state));
    }

    static MethodInfo onConnectionStateChanged(BluetoothDevice device, int state) {
        return new MethodInfo("onConnectionStateChanged", new MethodInfo.Parameter(BluetoothDevice.class, device),
                new MethodInfo.Parameter(int.class, state));
    }

    static MethodInfo onRead(BluetoothDevice device, byte[] value) {
        return new MethodInfo("onRead", new MethodInfo.Parameter(BluetoothDevice.class, device),
                new MethodInfo.Parameter(byte[].class, value));
    }

    static MethodInfo onWrite(BluetoothDevice device, @NonNull String tag, @NonNull byte[] value, boolean result) {
        return new MethodInfo("onWrite", new MethodInfo.Parameter(BluetoothDevice.class, device),
                new MethodInfo.Parameter(String.class, tag), new MethodInfo.Parameter(byte[].class, value),
                new MethodInfo.Parameter(boolean.class, result));
    }
}
