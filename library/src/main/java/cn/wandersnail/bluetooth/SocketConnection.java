package cn.wandersnail.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;

/**
 * date: 2020/5/5 20:53
 * author: zengfansheng
 */
class SocketConnection {
    private BluetoothSocket socket;
    private BluetoothDevice device;
    private OutputStream outStream;
    private ConnectionImpl connection;
    
    SocketConnection(ConnectionImpl connection, BluetoothDevice device, UUID uuid, ConnectCallbck callback) {
        this.connection = connection;
        this.device = device;
        BluetoothSocket tmp;
        try {
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
        connection.btManager.getExecutorService().execute(() -> {
            InputStream inputStream;
            OutputStream tmpOut;
            try {
                connection.btManager.stopDiscovery();//停止搜索
                socket.connect();
                inputStream = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                if (BTManager.isDebugMode) {
                    Log.w(BTManager.DEBUG_TAG, "Connect failed: " + e.getMessage());
                }
                try {
                    socket.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                socket = null;
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
            if (connection.observer != null) {
                connection.posterDispatcher.post(connection.observer, MethodInfoGenerator.onConnectionStateChanged(device, Connection.STATE_CONNECTED));
            }
            connection.observable.notifyObservers(MethodInfoGenerator.onConnectionStateChanged(device, Connection.STATE_CONNECTED));
            outStream = tmpOut;
            byte[] buffer = new byte[1024];
            int len;
            while (true) {
                try {
                    len = inputStream.read(buffer);
                    byte[] data = Arrays.copyOf(buffer, len);
                    if (BTManager.isDebugMode) {
                        Log.d(BTManager.DEBUG_TAG, "Receive data =>> " + new String(data));
                    }
                    if (connection.observer != null) {
                        connection.posterDispatcher.post(connection.observer, MethodInfoGenerator.onDataReceive(device, data));
                    }
                    connection.observable.notifyObservers(MethodInfoGenerator.onDataReceive(device, data));
                } catch (IOException e) {
                    if (BTManager.isDebugMode) {
                        Log.w(BTManager.DEBUG_TAG, "Disconnected: " + e.getMessage());
                    }
                    connection.state = Connection.STATE_DISCONNECTED;
                    if (connection.observer != null) {
                        connection.posterDispatcher.post(connection.observer, MethodInfoGenerator.onConnectionStateChanged(device, Connection.STATE_DISCONNECTED));
                    }
                    connection.observable.notifyObservers(MethodInfoGenerator.onConnectionStateChanged(device, Connection.STATE_DISCONNECTED));
                    break;
                }
            }
            socket = null;
        });
    }

    void write(WriteData data) {
        if (outStream != null) {
            try {
                outStream.write(data.value);
                if (connection.observer != null) {
                    connection.posterDispatcher.post(connection.observer, MethodInfoGenerator.onWrite(device, data.tag, data.value, true));
                }
                connection.observable.notifyObservers(MethodInfoGenerator.onWrite(device, data.tag, data.value, true));                
            } catch (IOException e) {
                if (connection.observer != null) {
                    connection.posterDispatcher.post(connection.observer, MethodInfoGenerator.onWrite(device, data.tag, data.value, false));
                }
                connection.observable.notifyObservers(MethodInfoGenerator.onWrite(device, data.tag, data.value, false));
            }
        } else {
            if (connection.observer != null) {
                connection.posterDispatcher.post(connection.observer, MethodInfoGenerator.onWrite(device, data.tag, data.value, false));
            }
            connection.observable.notifyObservers(MethodInfoGenerator.onWrite(device, data.tag, data.value, false));
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

        WriteData(String tag, byte[] value) {
            this.tag = tag;
            this.value = value;
        }
    }
}
