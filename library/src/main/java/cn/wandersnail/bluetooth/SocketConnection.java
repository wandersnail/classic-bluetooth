package cn.wandersnail.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import cn.wandersnail.commons.util.StringUtils;

/**
 * date: 2020/5/5 20:53
 * author: zengfansheng
 */
class SocketConnection {
    private BluetoothSocket socket;
    private final BluetoothDevice device;
    private OutputStream outStream;
    private final ConnectionImpl connection;
    
    SocketConnection(ConnectionImpl connection, BTManager btManager, BluetoothDevice device, UUID uuid, ConnectCallback callback) {
        this.device = device;
        this.connection = connection;
        BluetoothSocket tmp;
        try {
            connection.changeState(Connection.STATE_CONNECTING, false);
            tmp = device.createRfcommSocketToServiceRecord(uuid == null ? Connection.SPP_UUID : uuid);
        } catch (IOException e) {
            try {
                Method method = device.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                tmp = (BluetoothSocket) method.invoke(device, 1);
            } catch (Throwable t) {
                onConnectFail(connection, callback, "Connect failed: Socket's create() method failed", e);
                return;
            }
        }
        socket = tmp;
        btManager.getExecutorService().execute(() -> {
            InputStream inputStream;
            OutputStream tmpOut;
            try {
                btManager.stopDiscovery();//停止搜索
                socket.connect();
                inputStream = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                if (!connection.isReleased()) {
                    onConnectFail(connection, callback, "Connect failed: " + e.getMessage(), e);
                }
                return;
            }
            connection.changeState(Connection.STATE_CONNECTED, true);
            if (callback != null) {
                callback.onSuccess();
            }
            connection.callback(MethodInfoGenerator.onConnectionStateChanged(device, Connection.STATE_CONNECTED));
            outStream = tmpOut;
            byte[] buffer = new byte[1024];
            int len;
            while (true) {
                try {
                    len = inputStream.read(buffer);
                    byte[] data = Arrays.copyOf(buffer, len);
                    BTLogger.instance.d(BTManager.DEBUG_TAG, "Receive data =>> " + StringUtils.toHex(data));
                    connection.callback(MethodInfoGenerator.onRead(device, data));
                } catch (IOException e) {
                    if (!connection.isReleased()) {
                        connection.changeState(Connection.STATE_DISCONNECTED, false);
                    }
                    break;
                }
            }
            close();
        });
    }

    private void onConnectFail(ConnectionImpl connection, ConnectCallback callback, String errMsg, IOException e) {
        connection.changeState(Connection.STATE_DISCONNECTED, true);
        if (BTManager.isDebugMode) {
            Log.w(BTManager.DEBUG_TAG, errMsg);
        }
        close();
        if (callback != null) {
            callback.onFail(errMsg, e);
        }
        connection.callback(MethodInfoGenerator.onConnectionStateChanged(device, Connection.STATE_DISCONNECTED));
    }

    void write(WriteData data) {
        if (outStream != null && !connection.isReleased()) {
            try {
                outStream.write(data.value);
                BTLogger.instance.d(BTManager.DEBUG_TAG, "Write success. tag = " + data.tag);
                connection.callback(MethodInfoGenerator.onWrite(device, data.tag, data.value, true));
            } catch (IOException e) {
                onWriteFail("Write failed: " + e.getMessage(), data);
            }
        } else {
            onWriteFail("Write failed: OutputStream is null or connection is released", data);
        }
    }
    
    private void onWriteFail(String msg, WriteData data) {
        if (BTManager.isDebugMode) {
            Log.w(BTManager.DEBUG_TAG, msg);
        }
        connection.callback(MethodInfoGenerator.onWrite(device, data.tag, data.value, false));
    }
    
    void close() {
        if (socket != null) {
            try {
                socket.close();
                socket = null;
            } catch (Throwable e) {
                BTLogger.instance.e(BTManager.DEBUG_TAG, "Could not close the client socket: " + e.getMessage());
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
