package cn.wandersnail.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

/**
 * 蓝牙搜索监听器
 * <p>
 * date: 2019/8/3 09:17
 * author: zengfansheng
 */
public interface DiscoveryListener {
    /**
     * 缺少定位权限。 {@link Manifest.permission#ACCESS_COARSE_LOCATION} 或者 {@link Manifest.permission#ACCESS_FINE_LOCATION}
     */
    int ERROR_LACK_LOCATION_PERMISSION = 0;
    /**
     * 系统位置服务未开启
     */
    int ERROR_LOCATION_SERVICE_CLOSED = 1;
    /**
     * 搜索错误
     */
    int ERROR_SCAN_FAILED = 2;

    /**
     * 蓝牙搜索开始
     */
    void onDiscoveryStart();

    /**
     * 蓝牙搜索停止
     */
    void onDiscoveryStop();

    /**
     * 搜索到蓝牙设备
     *
     * @param device 搜索到的蓝牙设备
     * @param rssi   信息强度
     */
    void onDeviceFound(@NonNull BluetoothDevice device, int rssi);

    /**
     * 搜索错误
     *
     * @param errorCode {@link #ERROR_LACK_LOCATION_PERMISSION}, {@link #ERROR_LOCATION_SERVICE_CLOSED}
     */
    void onDiscoveryError(int errorCode, @NonNull String errorMsg);
}
