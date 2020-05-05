package cn.wandersnail.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

import java.util.UUID;

import cn.wandersnail.commons.observer.Observable;
import cn.wandersnail.commons.poster.PosterDispatcher;

/**
 * date: 2020/5/5 12:11
 * author: zengfansheng
 */
class ConnectionImpl implements Connection {
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothDevice device;
    private EventObserver observer;//伴生观察者
    private boolean isReleased;//连接是否已释放
    private final Observable observable;
    private final PosterDispatcher posterDispatcher;
    private final BTManager btManager;
    private int state = Connection.STATE_DISCONNECTED;

    ConnectionImpl(BTManager btManager, BluetoothAdapter bluetoothAdapter, BluetoothDevice device, EventObserver observer) {
        this.btManager = btManager;
        this.bluetoothAdapter = bluetoothAdapter;
        this.device = device;
        this.observer = observer;
        observable = btManager.getObservable();
        posterDispatcher = btManager.getPosterDispatcher();
    }

    @NonNull
    @Override
    public BluetoothDevice getDevice() {
        return device;
    }

    @Override
    public void connect() {
        
    }

    @Override
    public void connect(@NonNull UUID uuid) {

    }

    @Override
    public void disconnect() {

    }

    @Override
    public void release() {

    }

    @Override
    public void releaseNoEvent() {

    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public void clearQueue() {

    }

    @Override
    public void write(@NonNull byte[] value) {

    }

    @Override
    public void writeImmediately(@NonNull byte[] value) {

    }
}
