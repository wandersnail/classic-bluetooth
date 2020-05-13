package cn.wandersnail.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;

import cn.wandersnail.commons.observer.Observable;
import cn.wandersnail.commons.poster.PosterDispatcher;
import cn.wandersnail.commons.util.StringUtils;

/**
 * date: 2020/5/5 20:53
 * author: zengfansheng
 */
class SocketConnection {
    private BluetoothSocket socket;
    private BluetoothDevice device;
    private OutputStream outStream;
    private EventObserver observer;
    private PosterDispatcher posterDispatcher;
    private Observable observable;
    
    SocketConnection(ConnectionImpl connection, BluetoothDevice device, UUID uuid, ConnectCallback callback) {
        this.device = device;
        observer = connection.observer;
        posterDispatcher = connection.posterDispatcher;
        observable = connection.observable;
        BluetoothSocket tmp;
        try {
            connection.state = Connection.STATE_CONNECTING;
            if (BTManager.isDebugMode) {
                Log.d(BTManager.DEBUG_TAG, "Connecting...");
            }
            if (observer != null) {
                posterDispatcher.post(observer, MethodInfoGenerator.onConnectionStateChanged(device, Connection.STATE_CONNECTING));
            }
            observable.notifyObservers(MethodInfoGenerator.onConnectionStateChanged(device, Connection.STATE_CONNECTING));
            tmp = device.createRfcommSocketToServiceRecord(uuid == null ? Connection.SPP_UUID : uuid);
        } catch (IOException e) {
            if (BTManager.isDebugMode) {
                Log.e(BTManager.DEBUG_TAG, "Socket's create() method failed");
            }
            if (callback != null) {
                callback.onFail("Connect failed: " + e.getMessage(), e);
            }
            return;
        }
        socket = tmp;
        BTManager btManager = connection.btManager;
        btManager.getExecutorService().execute(() -> {
            InputStream inputStream;
            OutputStream tmpOut;
            try {
                btManager.stopDiscovery();//停止搜索
                socket.connect();
                inputStream = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                connection.state = Connection.STATE_DISCONNECTED;
                if (BTManager.isDebugMode) {
                    Log.w(BTManager.DEBUG_TAG, "Connect failed: " + e.getMessage());
                }
                close();
                if (callback != null) {
                    callback.onFail("Connect failed: " + e.getMessage(), e);
                }
                return;
            }
            connection.state = Connection.STATE_CONNECTED;
            if (callback != null) {
                callback.onSuccess();
            }
            if (BTManager.isDebugMode) {
                Log.d(BTManager.DEBUG_TAG, "Connected");
            }
            if (observer != null) {
                posterDispatcher.post(observer, MethodInfoGenerator.onConnectionStateChanged(device, Connection.STATE_CONNECTED));
            }
            observable.notifyObservers(MethodInfoGenerator.onConnectionStateChanged(device, Connection.STATE_CONNECTED));
            outStream = tmpOut;
            byte[] buffer = new byte[1024];
            int len;
            while (true) {
                try {
                    len = inputStream.read(buffer);
                    byte[] data = Arrays.copyOf(buffer, len);
                    if (BTManager.isDebugMode) {
                        Log.d(BTManager.DEBUG_TAG, "Receive data =>> " + StringUtils.toHex(data));
                    }
                    if (observer != null) {
                        posterDispatcher.post(observer, MethodInfoGenerator.onRead(device, data));
                    }
                    observable.notifyObservers(MethodInfoGenerator.onRead(device, data));
                } catch (IOException e) {
                    if (BTManager.isDebugMode) {
                        Log.w(BTManager.DEBUG_TAG, "Disconnected: " + e.getMessage());
                    }
                    connection.state = Connection.STATE_DISCONNECTED;
                    if (observer != null) {
                        posterDispatcher.post(observer, MethodInfoGenerator.onConnectionStateChanged(device, Connection.STATE_DISCONNECTED));
                    }
                    observable.notifyObservers(MethodInfoGenerator.onConnectionStateChanged(device, Connection.STATE_DISCONNECTED));
                    break;
                }
            }
            close();
        });
    }

    void write(WriteData data) {
        if (outStream != null) {
            try {
                outStream.write(data.value);
                if (observer != null) {
                    posterDispatcher.post(observer, MethodInfoGenerator.onWrite(device, data.tag, data.value, true));
                }
                observable.notifyObservers(MethodInfoGenerator.onWrite(device, data.tag, data.value, true));                
            } catch (IOException e) {
                if (BTManager.isDebugMode) {
                    Log.w(BTManager.DEBUG_TAG, "Write failed: " + e.getMessage());
                }
                if (observer != null) {
                    posterDispatcher.post(observer, MethodInfoGenerator.onWrite(device, data.tag, data.value, false));
                }
                observable.notifyObservers(MethodInfoGenerator.onWrite(device, data.tag, data.value, false));
            }
        } else {
            if (BTManager.isDebugMode) {
                Log.w(BTManager.DEBUG_TAG, "Write failed: OutputStream is null");
            }
            if (observer != null) {
                posterDispatcher.post(observer, MethodInfoGenerator.onWrite(device, data.tag, data.value, false));
            }
            observable.notifyObservers(MethodInfoGenerator.onWrite(device, data.tag, data.value, false));
        }
    }
    
    void close() {
        if (socket != null) {
            try {
                socket.close();
                socket = null;
            } catch (Exception e) {
                if (BTManager.isDebugMode) {
                    Log.e(BTManager.DEBUG_TAG, "Could not close the client socket: " + e.getMessage());
                }
            }
        }
    }
    
    boolean isConnected() {
        return socket != null && socket.isConnected();
    }
    
    static class WriteData {
        String tag;
        byte[] value;
        WriteCallback callback;

        WriteData(String tag, byte[] value) {
            this.tag = tag;
            this.value = value;
        }
    }
}
