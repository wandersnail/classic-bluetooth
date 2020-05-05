package cn.wandersnail.bluetooth;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

import java.util.UUID;

/**
 * date: 2020/5/5 10:29
 * author: zengfansheng
 */
public interface Connection {
    UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    
    /** 未连接 */
    int STATE_DISCONNECTED = 0;
    /** 配对中 */
    int STATE_PAIRING = 1;
    /** 已配对 */
    int STATE_BONDED = 2;
    /** 已连接 */
    int STATE_CONNECTED = 3;
    /** 连接已释放 */
    int STATE_RELEASED = 4;

    @NonNull
    BluetoothDevice getDevice();

    /**
     * 使用{@link #SPP_UUID}连接
     */
    void connect();

    /**
     * 指定连接的UUID
     */
    void connect(@NonNull UUID uuid);
    
    /**
     * 断开连接
     */
    void disconnect();

    /**
     * 销毁连接
     */
    void release();

    /**
     * 销毁连接，不通知观察者
     */
    void releaseNoEvent();

    /**
     * 获取连接状态
     */
    int getState();

    /**
     * 清除请求队列，不触发事件
     */
    void clearQueue();

    /**
     * 写数据，加入队列尾部
     */
    void write(@NonNull byte[] value);

    /**
     * 写数据，加入队列最前
     */
    void writeImmediately(@NonNull byte[] value);
}
