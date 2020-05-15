package cn.wandersnail.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import cn.wandersnail.commons.observer.Observable;
import cn.wandersnail.commons.poster.MethodInfo;
import cn.wandersnail.commons.poster.PosterDispatcher;
import cn.wandersnail.commons.util.StringUtils;

/**
 * date: 2020/5/5 12:11
 * author: zengfansheng
 */
class ConnectionImpl extends Connection {
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothDevice device;
    private final EventObserver observer;//伴生观察者
    private boolean isReleased;//连接是否已释放
    private final Observable observable;
    private final PosterDispatcher posterDispatcher;
    private final BTManager btManager;
    int state = Connection.STATE_DISCONNECTED;    
    private SocketConnection socketConnection;
    private final List<SocketConnection.WriteData> writeQueue = new ArrayList<>();//请求队列
    private volatile boolean writeRunning;

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

    void callback(MethodInfo info) {
        if (observer != null) {
            posterDispatcher.post(observer, info);
        }
        observable.notifyObservers(info);
    }
    
    @Override
    public void connect(UUID uuid, ConnectCallback callback) {
        if (isReleased) {
            if (callback != null) {
                callback.onFail("Already released.", null);
            }
        } else if (socketConnection != null && socketConnection.isConnected()) {
            if (callback != null) {
                callback.onFail("Already connected.", null);
            }
        } else {
            socketConnection = new SocketConnection(this, btManager, device, uuid, callback);
        }        
    }

    @Override
    public boolean isConnected() {
        return state == STATE_CONNECTED;
    }

    @Override
    public void disconnect() {
        if (socketConnection != null) {
            socketConnection.close();
            socketConnection = null;
        }
    }

    @Override
    public void release() {
        release(false);
    }

    @Override
    public void releaseNoEvent() {
        release(true);
    }

    private void release(boolean noEvent) {
        if (!isReleased) {
            clearQueue();
            disconnect();
            isReleased = true;
            state = Connection.STATE_RELEASED;
            if (BTManager.isDebugMode) {
                Log.d(BTManager.DEBUG_TAG, "connection released!");
            }
            if (!noEvent) {
                callback(MethodInfoGenerator.onConnectionStateChanged(device, state));
            }
            btManager.releaseConnection(device);//从集合中删除
        }
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public void setState(int state) {
        if (BTManager.isDebugMode) {
            Log.d(BTManager.DEBUG_TAG, "state changed: " + state);
        }
        this.state = state;
        callback(MethodInfoGenerator.onConnectionStateChanged(device, state));
    }
    
    @Override
    public void clearQueue() {
        synchronized (this) {
            writeQueue.clear();
        }
    }

    @Override
    public void write(@Nullable String tag, @NonNull byte[] value, @Nullable WriteCallback callback) {
        write(tag, value, false, callback);
    }

    @Override
    public void writeImmediately(@Nullable String tag, @NonNull byte[] value, @Nullable WriteCallback callback) {
        write(tag, value, true, callback);
    }
    
    private void write(String tag, byte[] value, boolean immediately, @Nullable WriteCallback callback) {
        tag = tag == null ? StringUtils.randomUuid() : tag;
        if (isReleased || !bluetoothAdapter.isEnabled()) {
            if (callback != null) {
                callback.onWrite(device, tag, value, false);
            } else {
                this.callback(MethodInfoGenerator.onWrite(device, tag, value, false));
            }
        } else {
            synchronized (this) {
                SocketConnection.WriteData writeData = new SocketConnection.WriteData(tag, value);
                writeData.callback = callback;
                if (immediately) {
                    writeQueue.add(0, writeData);
                } else {
                    writeQueue.add(writeData);
                }
                if (!writeRunning) {
                    writeRunning = true;
                    btManager.getExecutorService().execute(writeRunnable);
                }
            }
        }
    }
    
    private Runnable writeRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                while (true) {
                    SocketConnection.WriteData data;
                    synchronized (this) {
                        if (writeQueue.isEmpty()) {
                            writeRunning = false;
                            return;
                        } else {
                            data = writeQueue.remove(0);
                        }
                    }
                    SocketConnection sc = socketConnection;
                    if (sc != null) {
                        sc.write(data);
                    }
                }
            } finally {
                writeRunning = false;
            }
        }
    };
}
