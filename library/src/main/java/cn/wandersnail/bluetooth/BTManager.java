package cn.wandersnail.bluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.wandersnail.commons.observer.Observable;
import cn.wandersnail.commons.observer.Observe;
import cn.wandersnail.commons.poster.MethodInfo;
import cn.wandersnail.commons.poster.PosterDispatcher;
import cn.wandersnail.commons.poster.ThreadMode;

/**
 * date: 2020/5/5 09:49
 * author: zengfansheng
 */
public class BTManager {
    public static final String DEBUG_TAG = "BTManager";
    private static volatile BTManager instance;
    private static final Builder DEFAULT_BUILDER = new Builder();
    private final ExecutorService executorService;
    private final PosterDispatcher posterDispatcher;
    private final Observable observable;
    private Application application;
    private boolean isInitialized;
    private BluetoothAdapter bluetoothAdapter;
    private BroadcastReceiver broadcastReceiver;
    private final Map<String, Connection> connectionMap = new ConcurrentHashMap<>();
    //已连接的设备MAC地址集合
    private final List<String> addressList = new CopyOnWriteArrayList<>();
    private final boolean internalObservable;
    private boolean isDiscovering;
    private final List<DiscoveryListener> discoveryListeners = new CopyOnWriteArrayList<>();
    public static boolean isDebugMode;

    private BTManager() {
        this(DEFAULT_BUILDER);
    }

    private BTManager(Builder builder) {
        tryGetApplication();
        if (builder.observable != null) {
            internalObservable = false;
            observable = builder.observable;
            posterDispatcher = observable.getPosterDispatcher();
            executorService = posterDispatcher.getExecutorService();
        } else {
            internalObservable = true;
            executorService = builder.executorService;
            posterDispatcher = new PosterDispatcher(executorService, builder.methodDefaultThreadMode);
            observable = new Observable(posterDispatcher, builder.isObserveAnnotationRequired);
        }
    }

    /**
     * 获取实例。单例的
     */
    public static BTManager getInstance() {
        if (instance == null) {
            synchronized (BTManager.class) {
                if (instance == null) {
                    instance = new BTManager();
                }
            }
        }
        return instance;
    }

    public static Builder getBuilder() {
        return new Builder();
    }

    @Nullable
    Context getContext() {
        if (application == null) {
            tryAutoInit();
        }
        return application;
    }

    @SuppressLint("PrivateApi")
    private void tryGetApplication() {
        try {
            Class<?> cls = Class.forName("android.app.ActivityThread");
            Method method = cls.getMethod("currentActivityThread");
            method.setAccessible(true);
            Object acThread = method.invoke(null);
            Method appMethod = acThread.getClass().getMethod("getApplication");
            application = (Application) appMethod.invoke(acThread);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Nullable
    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    ExecutorService getExecutorService() {
        return executorService;
    }

    PosterDispatcher getPosterDispatcher() {
        return posterDispatcher;
    }

    Observable getObservable() {
        return observable;
    }

    public boolean isInitialized() {
        return isInitialized && application != null && instance != null;
    }

    /**
     * 蓝牙是否开启
     */
    public boolean isBluetoothOn() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    private class InnerBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                switch (action) {
                    case BluetoothAdapter.ACTION_STATE_CHANGED:
                        if (bluetoothAdapter != null) {
                            //通知观察者蓝牙状态
                            observable.notifyObservers(MethodInfoGenerator.onBluetoothAdapterStateChanged(bluetoothAdapter.getState()));
                            if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
                                isDiscovering = false;
                            }
                        }
                        break;
                    case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                        isDiscovering = true;
                        handleDiscoveryCallback(true, null, -120, -1, "");
                        break;
                    case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                        isDiscovering = false;
                        handleDiscoveryCallback(false, null, -120, -1, "");
                        break;
                    case BluetoothDevice.ACTION_FOUND:
                        if (device != null) {
                            int rssi = -120;
                            Bundle extras = intent.getExtras();
                            if (extras != null) {
                                rssi = extras.getShort(BluetoothDevice.EXTRA_RSSI);
                            }
                            handleDiscoveryCallback(false, device, rssi, 0, "");
                        }
                        break;
                    case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                        if (device != null) {
                            int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                            if (bondState == BluetoothDevice.BOND_BONDED ||
                                    bondState == BluetoothDevice.BOND_BONDING) {
                                Collection<Connection> connections = getConnections();
                                for (Connection connection : connections) {
                                    if (device.equals(connection.getDevice())) {
                                        //如果已连接，忽略
                                        if (!connection.isConnected()) {
                                            connection.setState(bondState == BluetoothDevice.BOND_BONDED ?
                                                    Connection.STATE_PAIRED : Connection.STATE_PAIRING);                                            
                                        }
                                        break;
                                    }
                                }
                            }                            
                        }
                        break;
                }
            }
        }
    }

    public synchronized void initialize(@NonNull Application application) {
        if (isInitialized()) {
            return;
        }
        Objects.requireNonNull(application, "application can't be null");
        this.application = application;
        //获取蓝牙配置器
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        //注册蓝牙开关状态广播接收者
        if (broadcastReceiver == null) {
            broadcastReceiver = new InnerBroadcastReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            filter.addAction(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            application.registerReceiver(broadcastReceiver, filter);
        }
        isInitialized = true;
    }

    private synchronized boolean checkStatus() {
        Objects.requireNonNull(instance, "BTManager instance has been destroyed!");
        if (!isInitialized) {
            if (!tryAutoInit()) {
                String msg = "The SDK has not been initialized, make sure to call BTManager.getInstance().initialize(Application) first.";
                BTLogger.instance.e(DEBUG_TAG, msg);
                return false;
            }
        } else if (application == null) {
            return tryAutoInit();
        }
        return true;
    }

    private boolean tryAutoInit() {
        tryGetApplication();
        if (application != null) {
            initialize(application);
        }
        return isInitialized();
    }

    /**
     * 注册连接状态及数据接收观察者
     */
    public void registerObserver(@NonNull EventObserver observer) {
        if (checkStatus()) {
            observable.registerObserver(observer);
        }
    }

    /**
     * 查询观察者是否注册
     */
    public boolean isObserverRegistered(@NonNull EventObserver observer) {
        return observable.isRegistered(observer);
    }

    /**
     * 取消注册连接状态及数据接收观察者
     */
    public void unregisterObserver(@NonNull EventObserver observer) {
        observable.unregisterObserver(observer);
    }

    /**
     * 通知所有观察者事件变化，通常只用在
     *
     * @param info 方法信息实例
     */
    public void notifyObservers(@NonNull MethodInfo info) {
        if (checkStatus()) {
            observable.notifyObservers(info);
        }
    }

    public void addDiscoveryListener(@NonNull DiscoveryListener listener) {
        if (!discoveryListeners.contains(listener)) {
            discoveryListeners.add(listener);
        }
    }

    public void removeDiscoveryListener(@NonNull DiscoveryListener listener) {
        discoveryListeners.remove(listener);
    }

    //位置服务是否开户
    private boolean isLocationEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            return locationManager != null && locationManager.isLocationEnabled();
        } else {
            try {
                int locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
                return locationMode != Settings.Secure.LOCATION_MODE_OFF;
            } catch (Settings.SettingNotFoundException e) {
                return false;
            }
        }
    }

    //检查是否有定位权限
    private boolean noLocationPermission(Context context) {
        int sdkVersion = context.getApplicationInfo().targetSdkVersion;
        if (sdkVersion >= Build.VERSION_CODES.Q) {//target sdk版本在29以上的需要精确定位权限才能搜索到蓝牙设备
            return !PermissionChecker.hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            return !PermissionChecker.hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) &&
                    !PermissionChecker.hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION);
        }
    }

    //检查是否有搜索权限
    private boolean noScanPermission(Context context) {
        //在31以上的需要搜索权限才能搜索到蓝牙设备
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return !PermissionChecker.hasPermission(context, Manifest.permission.BLUETOOTH_SCAN);
        }
        return false;
    }

    public boolean isDiscovering() {
        return isDiscovering;
    }

    /**
     * 开始搜索
     */
    public void startDiscovery() {
        if (!checkStatus()) {
            return;
        }
        synchronized (this) {
            if (isDiscovering || !isBluetoothOn()) {
                return;
            }
            if (!isLocationEnabled(application)) {
                String errorMsg = "Unable to scan for Bluetooth devices, the phone's location service is not turned on.";
                handleDiscoveryCallback(false, null, -120, DiscoveryListener.ERROR_LOCATION_SERVICE_CLOSED, errorMsg);
                BTLogger.instance.e(DEBUG_TAG, errorMsg);
                return;
            }
            if (noLocationPermission(application)) {
                String errorMsg = "Unable to scan for Bluetooth devices, lack location permission.";
                handleDiscoveryCallback(false, null, -120, DiscoveryListener.ERROR_LACK_LOCATION_PERMISSION, errorMsg);
                BTLogger.instance.e(DEBUG_TAG, errorMsg);
                return;
            }
            if (noScanPermission(application)) {
                String errorMsg = "Unable to scan for Bluetooth devices, lack scan permission.";
                handleDiscoveryCallback(false, null, -120, DiscoveryListener.ERROR_LACK_SCAN_PERMISSION, errorMsg);
                BTLogger.instance.e(DEBUG_TAG, errorMsg);
                return;
            }
        }
        bluetoothAdapter.startDiscovery();//开始搜索
    }

    /**
     * 停止搜索
     */
    public void stopDiscovery() {
        if (checkStatus() && bluetoothAdapter != null) {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    //处理搜索回调
    private void handleDiscoveryCallback(final boolean start, @Nullable final BluetoothDevice device,
                                         final int rssi, final int errorCode, final String errorMsg) {
        posterDispatcher.post(ThreadMode.MAIN, () -> {
            for (DiscoveryListener listener : discoveryListeners) {
                if (device != null) {
                    listener.onDeviceFound(device, rssi);
                } else if (start) {
                    listener.onDiscoveryStart();
                } else if (errorCode >= 0) {
                    listener.onDiscoveryError(errorCode, errorMsg);
                } else {
                    listener.onDiscoveryStop();
                }
            }
        });
    }

    /**
     * 创建连接
     *
     * @param address 蓝牙地址
     * @return 返回创建的连接实例，创建失败则返回null
     */
    @Nullable
    public Connection createConnection(@NonNull String address) {
        return createConnection(address, null);
    }

    /**
     * 创建连接
     *
     * @param device 蓝牙设备实例
     * @return 返回创建的连接实例，创建失败则返回null
     */
    @Nullable
    public Connection createConnection(@NonNull BluetoothDevice device) {
        return createConnection(device, null);
    }

    /**
     * 创建连接
     *
     * @param address  蓝牙地址
     * @param observer 伴生观察者
     * @return 返回创建的连接实例，创建失败则返回null
     */
    @Nullable
    public Connection createConnection(@NonNull String address, EventObserver observer) {
        if (checkStatus()) {
            Objects.requireNonNull(address, "address can't be null");
            BluetoothDevice remoteDevice = bluetoothAdapter.getRemoteDevice(address);
            if (remoteDevice != null) {
                return createConnection(remoteDevice, observer);
            }
        }
        return null;
    }

    /**
     * 创建连接
     *
     * @param device   蓝牙设备实例
     * @param observer 伴生观察者
     * @return 返回创建的连接实例，创建失败则返回null
     */
    @Nullable
    public Connection createConnection(@NonNull BluetoothDevice device, EventObserver observer) {
        if (checkStatus()) {
            Objects.requireNonNull(device, "device can't be null");
            Connection connection = connectionMap.remove(device.getAddress());
            //如果连接已存在，先释放掉
            if (connection != null) {
                connection.releaseNoEvent();
            }
            connection = new ConnectionImpl(this, bluetoothAdapter, device, observer);
            connectionMap.put(device.getAddress(), connection);
            addressList.add(device.getAddress());
            return connection;
        }
        return null;
    }

    /**
     * 获取所有连接，无序的
     */
    @NonNull
    public Collection<Connection> getConnections() {
        return connectionMap.values();
    }

    /**
     * 获取所有连接，有序的
     */
    @NonNull
    public List<Connection> getOrderedConnections() {
        List<Connection> list = new ArrayList<>();
        for (String address : addressList) {
            Connection connection = connectionMap.get(address);
            if (connection != null) {
                list.add(connection);
            }
        }
        return list;
    }

    /**
     * 获取第一个连接
     */
    @Nullable
    public Connection getFirstConnection() {
        return addressList.isEmpty() ? null : connectionMap.get(addressList.get(0));
    }

    /**
     * 获取最后一个连接
     */
    @Nullable
    public Connection getLastConnection() {
        return addressList.isEmpty() ? null : connectionMap.get(addressList.get(addressList.size() - 1));
    }

    @Nullable
    public Connection getConnection(BluetoothDevice device) {
        return device == null ? null : connectionMap.get(device.getAddress());
    }

    @Nullable
    public Connection getConnection(String address) {
        return address == null ? null : connectionMap.get(address);
    }

    /**
     * 断开连接
     */
    public void disconnectConnection(BluetoothDevice device) {
        if (checkStatus() && device != null) {
            Connection connection = connectionMap.get(device.getAddress());
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 断开连接
     */
    public void disconnectConnection(String address) {
        if (checkStatus() && address != null) {
            Connection connection = connectionMap.get(address);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 断开所有连接
     */
    public void disconnectAllConnections() {
        if (checkStatus()) {
            for (Connection connection : connectionMap.values()) {
                connection.disconnect();
            }
        }
    }

    /**
     * 释放所有连接
     */
    public void releaseAllConnections() {
        if (checkStatus()) {
            for (Connection connection : connectionMap.values()) {
                connection.release();
            }
            connectionMap.clear();
            addressList.clear();
        }
    }

    /**
     * 释放连接
     */
    public void releaseConnection(String address) {
        if (checkStatus() && address != null) {
            addressList.remove(address);
            Connection connection = connectionMap.remove(address);
            if (connection != null) {
                connection.release();
            }
        }
    }

    /**
     * 释放连接
     */
    public void releaseConnection(BluetoothDevice device) {
        if (checkStatus() && device != null) {
            addressList.remove(device.getAddress());
            Connection connection = connectionMap.remove(device.getAddress());
            if (connection != null) {
                connection.release();
            }
        }
    }

    /**
     * 关闭所有连接并释放资源
     */
    public synchronized void release() {
        if (broadcastReceiver != null) {
            application.unregisterReceiver(broadcastReceiver);
            broadcastReceiver = null;
        }
        isInitialized = false;
        if (bluetoothAdapter != null) {
            bluetoothAdapter.cancelDiscovery();
        }
        discoveryListeners.clear();
        releaseAllConnections();
        if (internalObservable) {
            observable.unregisterAll();
            posterDispatcher.clearTasks();
        }
    }

    /**
     * 销毁，可重新构建
     */
    public void destroy() {
        release();
        synchronized (BTManager.class) {
            instance = null;
        }
    }

    /**
     * 根据MAC地址获取设备的配对状态
     *
     * @return {@link BluetoothDevice#BOND_NONE}，{@link BluetoothDevice#BOND_BONDED}，{@link BluetoothDevice#BOND_BONDING}
     */
    public int getBondState(@NonNull String address) {
        checkStatus();
        try {
            return bluetoothAdapter.getRemoteDevice(address).getBondState();
        } catch (Throwable e) {
            return BluetoothDevice.BOND_NONE;
        }
    }

    /**
     * 开始配对
     *
     * @param address 设备地址
     */
    public boolean createBond(@NonNull String address) {
        checkStatus();
        try {
            BluetoothDevice remoteDevice = bluetoothAdapter.getRemoteDevice(address);
            return remoteDevice.getBondState() != BluetoothDevice.BOND_NONE || remoteDevice.createBond();
        } catch (Throwable ignore) {
            return false;
        }
    }

    /**
     * 开始配对
     *
     * @param device 设备
     */
    public boolean createBond(@NonNull BluetoothDevice device) {
        checkStatus();
        try {
            return device.getBondState() != BluetoothDevice.BOND_NONE || device.createBond();
        } catch (Throwable ignore) {
            return false;
        }
    }

    /**
     * 根据过滤器，清除配对
     */
    @SuppressWarnings("all")
    public void clearBondDevices(RemoveBondFilter filter) {
        checkStatus();
        if (bluetoothAdapter != null) {
            Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice device : devices) {
                if (filter == null || filter.accept(device)) {
                    try {
                        device.getClass().getMethod("removeBond").invoke(device);
                    } catch (Throwable ignore) {
                    }
                }
            }
        }
    }

    /**
     * 解除配对
     *
     * @param address 设备地址
     */
    @SuppressWarnings("all")
    public void removeBond(@NonNull String address) {
        checkStatus();
        try {
            BluetoothDevice remoteDevice = bluetoothAdapter.getRemoteDevice(address);
            if (remoteDevice.getBondState() != BluetoothDevice.BOND_NONE) {
                remoteDevice.getClass().getMethod("removeBond").invoke(remoteDevice);
            }
        } catch (Throwable ignore) {
        }
    }

    public static class Builder {
        private final static ExecutorService DEFAULT_EXECUTOR_SERVICE = Executors.newCachedThreadPool();
        ThreadMode methodDefaultThreadMode = ThreadMode.MAIN;
        ExecutorService executorService = DEFAULT_EXECUTOR_SERVICE;
        Observable observable;
        boolean isObserveAnnotationRequired = false;

        /**
         * 自定义线程池用来执行后台任务
         */
        public Builder setExecutorService(@NonNull ExecutorService executorService) {
            Objects.requireNonNull(executorService, "executorService can't be null");
            this.executorService = executorService;
            return this;
        }

        /**
         * 观察者或者回调的方法在没有使用注解指定调用线程时，默认被调用的线程
         */
        public Builder setMethodDefaultThreadMode(@NonNull ThreadMode mode) {
            Objects.requireNonNull(mode, "mode can't be null");
            methodDefaultThreadMode = mode;
            return this;
        }

        /**
         * 被观察者，消息发布者。
         * <br>如果观察者被设置，{@link #setMethodDefaultThreadMode(ThreadMode)}、
         * {@link #setObserveAnnotationRequired(boolean)}、{@link #setExecutorService(ExecutorService)}将不起作用
         */
        public Builder setObservable(@NonNull Observable observable) {
            Objects.requireNonNull(observable, "observable can't be null");
            this.observable = observable;
            return this;
        }

        /**
         * 是否强制使用{@link Observe}注解才会收到被观察者的消息。强制使用的话，性能会好一些
         */
        public Builder setObserveAnnotationRequired(boolean observeAnnotationRequired) {
            isObserveAnnotationRequired = observeAnnotationRequired;
            return this;
        }

        /**
         * 根据当前配置构建EasyBLE实例
         */
        public BTManager build() {
            synchronized (BTManager.class) {
                if (BTManager.instance != null) {
                    throw new BTException("BTManager instance already exists. It can only be instantiated once.");
                }
                BTManager.instance = new BTManager(this);
                return BTManager.instance;
            }
        }
    }
}
