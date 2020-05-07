package cn.wandersnail.bluetooth;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

/**
 * date: 2020/5/7 09:26
 * author: zengfansheng
 */
public interface WriteCallback {
    /**
     * 写入结果
     *
     * @param device 设备
     * @param tag    写入时设置的tag
     * @param value  要写入的数据
     * @param result 写入结果
     */
    void onWrite(@NonNull BluetoothDevice device, @NonNull String tag, @NonNull byte[] value, boolean result);
}
