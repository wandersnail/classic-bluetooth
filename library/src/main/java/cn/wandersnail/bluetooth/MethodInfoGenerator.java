package cn.wandersnail.bluetooth;


import android.bluetooth.BluetoothDevice;

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

    static MethodInfo onConnectFailed(BluetoothDevice device, String msg, Throwable e) {
        return new MethodInfo("onConnectFailed", new MethodInfo.Parameter(BluetoothDevice.class, device),
                new MethodInfo.Parameter(String.class, msg), new MethodInfo.Parameter(Throwable.class, e));
    }

    static MethodInfo onDataReceive(BluetoothDevice device, byte[] value) {
        return new MethodInfo("onDataReceive", new MethodInfo.Parameter(BluetoothDevice.class, device),
                new MethodInfo.Parameter(byte[].class, value));
    }
}
