package cn.wandersnail.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Build;

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
    private int state = Connection.STATE_DISCONNECTED;    
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

    //检查是否有连接权限
    private boolean hasConnectPermission(Context context) {
        //在31以上的需要连接权限才能连接蓝牙设备
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return PermissionChecker.hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT);
        }
        return true;
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
            if (!hasConnectPermission(btManager.getContext())) {
                callback.onFail("Lack connect permission.", null);
                return;
            }
            socketConnection = new SocketConnection(this, btManager, device, uuid, callback);
        }        
    }

    @Override
    public boolean isReleased() {
        return isReleased;
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
            changeState(Connection.STATE_RELEASED, noEvent);
            btManager.releaseConnection(device);//从集合中删除
        }
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public void setState(int state) {
        changeState(state, false);
    }

    synchronized void changeState(int state, boolean noEvent) {
        //如果已连接状态比已配对先到，先通知已配对，后续已配对事件忽略
        if (this.state == Connection.STATE_PAIRING && state == Connection.STATE_CONNECTED) {
            setState(Connection.STATE_PAIRED);
        }
        this.state = state;
        BTLogger.instance.d(BTManager.DEBUG_TAG, "Connection state changed: " + getStateDesc(state));
        if (!noEvent) {
            callback(MethodInfoGenerator.onConnectionStateChanged(device, state));
        }
    }
    
    private String getStateDesc(int state) {
        switch(state) {
            case Connection.STATE_CONNECTED:		
        		return "connected";
            case Connection.STATE_CONNECTING:
                return "connecting";
            case Connection.STATE_DISCONNECTED:
                return "disconnected";
            case Connection.STATE_PAIRED:
                return "paired";
            case Connection.STATE_PAIRING:
                return "pairing";
            case Connection.STATE_RELEASED:
                return "released";    
            default:		
        		return "unknown state";
        }
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
    
    private final Runnable writeRunnable = new Runnable() {
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
