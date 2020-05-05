package cn.wandersnail.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import cn.wandersnail.commons.observer.Observer;

/**
 * 各种事件。蓝牙状态，连接状态，接收到数据等等
 * <p>
 * date: 2019/8/3 13:15
 * author: zengfansheng
 */
public interface EventObserver extends Observer {
    /**
     * 蓝牙开关状态变化
     *
     * @param state {@link BluetoothAdapter#STATE_OFF}等
     */
    default void onBluetoothAdapterStateChanged(int state) {
    }

    /**
     * 收到数据
     *
     * @param device 设备
     * @param value  收到的数据
     */
    default void onDataReceive(@NonNull BluetoothDevice device, @NonNull byte[] value) {
    }

    /**
     * 连接状态变化
     *
     * @param device 设备
     * @param state  设备。状态{@link Connection#STATE_CONNECTED}，可能的值{@link Connection#STATE_RELEASED}等
     */
    default void onConnectionStateChanged(@NonNull BluetoothDevice device, int state) {
    }

    /**
     * 连接失败
     *
     * @param device 设备
     * @param msg    错误信息
     * @param e      错误异常
     */
    default void onConnectFailed(@NonNull BluetoothDevice device, @NonNull String msg, @Nullable Throwable e) {
    }
}
