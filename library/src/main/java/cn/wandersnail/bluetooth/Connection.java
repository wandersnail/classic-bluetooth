package cn.wandersnail.bluetooth;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

/**
 * date: 2020/5/5 10:29
 * author: zengfansheng
 */
public abstract class Connection {
    public static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    /**
     * 未连接
     */
    public static final int STATE_DISCONNECTED = 0;
    /**
     * 配对中
     */
    public static final int STATE_PAIRING = 1;
    /**
     * 已配对
     */
    public static final int STATE_PAIRED = 2;
    /**
     * 已连接
     */
    public static final int STATE_CONNECTED = 3;
    /**
     * 连接已释放
     */
    public static final int STATE_RELEASED = 4;

    /**
     * 设置连接状态
     */
    abstract void setState(int state);

    /**
     * 是否已连接
     */
    public abstract boolean isConnected();

    @NonNull
    public abstract BluetoothDevice getDevice();

    /**
     * 指定连接的UUID
     *
     * @param uuid     如果传null，默认使用{@link #SPP_UUID}连接
     * @param callback 连接回调
     */
    public abstract void connect(UUID uuid, ConnectCallbck callback);

    /**
     * 断开连接
     */
    public abstract void disconnect();

    /**
     * 销毁连接
     */
    public abstract void release();

    /**
     * 销毁连接，不通知观察者
     */
    public abstract void releaseNoEvent();

    /**
     * 获取连接状态
     */
    public abstract int getState();

    /**
     * 清除请求队列，不触发事件
     */
    public abstract void clearQueue();

    /**
     * 写数据，加入队列尾部
     */
    public abstract void write(@Nullable String tag, @NonNull byte[] value);

    /**
     * 写数据，加入队列最前
     */
    public abstract void writeImmediately(@Nullable String tag, @NonNull byte[] value);
}
