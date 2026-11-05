package cn.wandersnail.bluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
    private BroadcastReceiver stateBroadcastReceiver;
    private BroadcastReceiver discoveryBroadcastReceiver;
    private final Map<String, Connection> connectionMap = new ConcurrentHashMap<>();
    //已连接的设备MAC地址集合
    private final List<String> addressList = new CopyOnWriteArrayList<>();
    private final boolean internalObservable;
    private boolean isDiscovering;
    /**
     * 发现广播接收者是否已注册。
     *
     * 为什么要单独记这个标记：接收者改为“初始化时注册、只有 release 时才注销”，
     * 避免每轮 FINISHED 后注销、下次 start 前再注册，中间出现广播空窗（空窗内 STARTED/FINISHED 会直接丢失）。
     */
    private boolean discoveryReceiverRegistered;
    private final List<DiscoveryListener> discoveryListeners = new CopyOnWriteArrayList<>();
    public static boolean isDebugMode;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
            BTLogger.instance.e(DEBUG_TAG, "尝试获取Application失败", e);
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

    private BluetoothDevice getDeviceFromIntent(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        } else {
            return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        } 
    }
    
    /** 发现调度状态机，库内部实现；对外只通过 start/stop + DiscoveryListener 暴露，见类注释 */
    private final DiscoveryController discoveryController = new DiscoveryController();

    private class DiscoveryBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                        discoveryController.onStarted();
                        break;
                    case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                        discoveryController.onFinished();
                        break;
                    case BluetoothDevice.ACTION_FOUND:
                        BluetoothDevice device = getDeviceFromIntent(intent);                        
                        if (device != null) {
                            int rssi = -120;
                            Bundle extras = intent.getExtras();
                            if (extras != null) {
                                rssi = extras.getShort(BluetoothDevice.EXTRA_RSSI);
                            }
                            handleDiscoveryCallback(false, device, rssi, 0, "");
                        }
                        break;
                }
            }
        }
    }
    
    private class StateBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case BluetoothAdapter.ACTION_STATE_CHANGED:
                        BTLogger.instance.d(DEBUG_TAG, "Bluetooth state changed: " + bluetoothAdapter.getState());
                        if (bluetoothAdapter != null) {
                            //通知观察者蓝牙状态
                            observable.notifyObservers(MethodInfoGenerator.onBluetoothAdapterStateChanged(bluetoothAdapter.getState()));
                            if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
                                // 蓝牙关闭时让状态机复位，否则会一直停在 ACTIVE 挡住后续搜索
                                discoveryController.onBluetoothOff();
                            }
                        }
                        break;
                    case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                        BTLogger.instance.d(DEBUG_TAG, "Bluetooth device bond state changed: " + intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1));
                        BluetoothDevice device = getDeviceFromIntent(intent);
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
        BluetoothManager bluetoothManager = (BluetoothManager) application.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        //注册蓝牙开关状态广播接收者
        if (stateBroadcastReceiver == null) {
            stateBroadcastReceiver = new StateBroadcastReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            application.registerReceiver(stateBroadcastReceiver, filter);
        }
        // 发现广播接收者在这里一次性注册并常驻，避免每轮搜索前后“注销-再注册”造成广播空窗
        registerDiscoveryReceiver();
        isInitialized = true;
    }
    
    private IntentFilter getDiscoveryFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        return filter;
    }

    private synchronized void registerDiscoveryReceiver() {
        // 常驻注册：已经注册过就直接复用，重复注册会导致同一事件回调多次
        if (discoveryReceiverRegistered) {
            return;
        }
        if (discoveryBroadcastReceiver == null) {
            discoveryBroadcastReceiver = new DiscoveryBroadcastReceiver();
        }
        try {
            ContextCompat.registerReceiver(application, discoveryBroadcastReceiver, getDiscoveryFilter(), ContextCompat.RECEIVER_EXPORTED);
            discoveryReceiverRegistered = true;
        } catch (Exception e) {
            BTLogger.instance.e(DEBUG_TAG, "注册广播接收者失败", e);
        }
    }
    
    private synchronized void unregisterDiscoveryReceiver() {
        if (discoveryBroadcastReceiver != null && discoveryReceiverRegistered) {
            try {
                application.unregisterReceiver(discoveryBroadcastReceiver);
            } catch (Exception e) {
                BTLogger.instance.e(DEBUG_TAG, "注销广播接收者失败", e);
            }
            discoveryReceiverRegistered = false;
        }
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

    //检查是否有连接权限
    private boolean noConnectPermission(Context context) {
        //在31以上的需要连接权限才能连接蓝牙设备
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return !PermissionChecker.hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT);
        }
        return false;
    }

    /**
     * 开始搜索。部分机型使用此方法无法正确判断出是否拥有相应的权限，请使用{@link #startDiscovery(Activity)}
     */
    @Deprecated
    public void startDiscovery() {
        // 没有 Activity 时由状态机回退到 Application 做权限判断
        discoveryController.start(null);
    }

    /**
     * 开始搜索。
     *
     * <p>语义说明：本方法不再直接调用系统 API 后立即返回，而是交给库内部的发现调度状态机。
     * 内部会：合并重复请求（不会叠加多次 start）、在开始前用 {@code bluetoothAdapter.isDiscovering()} 实时判断
     * 并先 cancel（最多 3 次 / 2s，超限强制 start）、start 返回 true 但 1.5s 内没有
     * {@code ACTION_DISCOVERY_STARTED} 时判定与栈脱同步并按 1.5s→3s→8s→16s 退避重试、
     * 会话 15s 无 FINISHED 时主动 cancel 复位、两轮搜索之间至少间隔 3s。</p>
     *
     * <p>因此 {@link DiscoveryListener#onDiscoveryStart()} 只会在真正收到 STARTED 后回调；
     * 若多次重试仍无法开始，会回调一次 {@link DiscoveryListener#onDiscoveryError(int, String)}
     * （errorCode 为 {@link DiscoveryListener#ERROR_SCAN_FAILED}）。</p>
     */
    public void startDiscovery(@NonNull Activity activity) {
        discoveryController.start(activity);
    }

    /**
     * 停止搜索。
     *
     * <p>语义说明：无论内部缓存如何，这里都会向系统发起一次 {@code cancelDiscovery()}，
     * 用于清掉 AdapterService 侧可能残留的 discovering 标记；随后回调一次
     * {@link DiscoveryListener#onDiscoveryStop()}（仅当本轮搜索真正开始过）。</p>
     */
    public void stopDiscovery() {
        discoveryController.stop();
    }

    /**
     * 发现调度状态机（库内部实现，不对外暴露）。
     *
     * <p>为什么需要它：{@code BluetoothAdapter.startDiscovery()} 返回 true 只代表请求被系统服务受理，
     * 并不代表底层 BTA 真的开始了搜索。部分 ROM 在上一轮搜索结束后仍停留在 DISCOVER_ACTIVE（例如为设备补读名字），
     * 此时新的 start 请求会被直接丢弃，表现为“第二次搜索没开始”，而 AdapterService 的 isDiscovering 仍是 false。</p>
     *
     * <p>职责：所有搜索入口都在这里串行化（主线程 Handler），对外仍只有
     * {@link #startDiscovery(Activity)}/{@link #stopDiscovery()} 与 {@link DiscoveryListener}，
     * 不新增并行公开 API。</p>
     */
    private final class DiscoveryController {
        /** cancel 后等待 STOPPED 落地的窗口 */
        private static final long CANCEL_SETTLE_MS = 500L;
        /** cancel 的最大次数；超过后强制 start，避免在“永远为 true”的机型上卡死 */
        private static final int CANCEL_MAX_ATTEMPTS = 3;
        /** 一次 cancel 阶段的总时限 */
        private static final long CANCEL_TOTAL_TIMEOUT_MS = 2000L;
        /** start 受理后等待 STARTED 的时限；超时即判定与栈脱同步 */
        private static final long START_TIMEOUT_MS = 1500L;
        /** 会话看门狗：ACTIVE 后这么久没有 FINISHED 就主动复位 */
        private static final long SESSION_WATCHDOG_MS = 15000L;
        /** 两轮搜索之间的最小间隔，避开上一轮的收尾作业 */
        private static final long MIN_RESTART_GAP_MS = 3000L;
        /**
         * start 超时后的退避序列，累计跨度要盖住 BTM_ReadRemoteDeviceName 的 30s 超时窗口。
         * 旧实现几百毫秒的快速重试会全部落在这个窗口内，必然失败。
         */
        private static final long[] RETRY_DELAYS_MS = {1500L, 3000L, 8000L, 16000L};

        private enum State {IDLE, WAITING_CANCEL, WAITING_STACK, STARTING, ACTIVE}

        private State state = State.IDLE;
        /** 多入口合并标记：会话进行中再次请求搜索，只记一次，绝不叠加 start */
        private boolean pending;
        private boolean sessionActive;
        private int retryCount;
        private int cancelAttempt;
        private long cancelDeadline;
        private long lastFinishAt;

        private final Runnable restartRunnable = () -> {
            if (state == State.IDLE || state == State.WAITING_STACK) {
                log("重启定时器触发，state=" + state);
                beginStart(null);
            }
        };
        private final Runnable cancelSettleRunnable = () -> {
            if (state != State.WAITING_CANCEL) {
                return;
            }
            if (realtimeDiscovering()) {
                waitCancelOrForce();
            } else {
                log("cancel 后已确认 isDiscovering=false，开始 start");
                forceStart();
            }
        };
        private final Runnable startTimeoutRunnable = () -> {
            if (state != State.STARTING) {
                return;
            }
            log("start 后 " + START_TIMEOUT_MS + "ms 未收到 STARTED，判定与栈脱同步");
            // 复位缓存，避免上层永远以为“正在搜索”
            isDiscovering = false;
            handleStartFailure("已接受搜索请求但未开始，蓝牙服务可能处于异常状态");
        };
        private final Runnable sessionWatchdogRunnable = () -> {
            if (state == State.ACTIVE) {
                log("会话看门狗触发：ACTIVE 超过 " + SESSION_WATCHDOG_MS + "ms 无 FINISHED，主动 cancel 复位");
                cancelForcefully("看门狗复位");
                lastFinishAt = SystemClock.elapsedRealtime();
                setState(State.IDLE, "看门狗复位");
                isDiscovering = false;
                if (sessionActive) {
                    sessionActive = false;
                    handleDiscoveryCallback(false, null, -120, -1, "");
                }
                if (pending) {
                    pending = false;
                    scheduleRestart(MIN_RESTART_GAP_MS);
                }
            }
        };

        void start(@Nullable Activity activity) {
            mainHandler.post(() -> handleStart(activity));
        }

        void stop() {
            mainHandler.post(() -> {
                log("stopDiscovery 请求：state=" + state + ", isDiscovering=" + realtimeDiscovering());
                pending = false;
                retryCount = 0;
                cancelAttempt = 0;
                removeAllTimers();
                cancelForcefully("stopDiscovery");
                setState(State.IDLE, "stopDiscovery");
                if (sessionActive) {
                    sessionActive = false;
                    handleDiscoveryCallback(false, null, -120, -1, "");
                }
            });
        }

        /** 供广播接收者调用：STARTED */
        void onStarted() {
            mainHandler.post(() -> {
                // 退避等待中也可能收到“迟到的 STARTED”，说明上一次请求最终还是启动了
                if (state != State.STARTING && state != State.WAITING_STACK) {
                    log("收到 STARTED 但 state=" + state + "，忽略（不是本库发起的会话）");
                    return;
                }
                removeAllTimers();
                isDiscovering = true;
                setState(State.ACTIVE, "收到 STARTED");
                retryCount = 0;
                cancelAttempt = 0;
                sessionActive = true;
                mainHandler.postDelayed(sessionWatchdogRunnable, SESSION_WATCHDOG_MS);
                handleDiscoveryCallback(true, null, -120, -1, "");
            });
        }

        /** 供广播接收者调用：FINISHED */
        void onFinished() {
            mainHandler.post(() -> {
                log("收到 FINISHED：state=" + state + ", pending=" + pending);
                isDiscovering = false;
                lastFinishAt = SystemClock.elapsedRealtime();
                if (state == State.WAITING_STACK) {
                    // 属于上一轮的残留广播，不打断已排好的退避重试
                    log("退避等待中收到残留 FINISHED，保持重试计划不变");
                    return;
                }
                removeAllTimers();
                setState(State.IDLE, "收到 FINISHED");
                retryCount = 0;
                cancelAttempt = 0;
                if (sessionActive) {
                    sessionActive = false;
                    handleDiscoveryCallback(false, null, -120, -1, "");
                }
                if (pending) {
                    pending = false;
                    scheduleRestart(MIN_RESTART_GAP_MS);
                }
            });
        }

        /** 蓝牙关闭时复位，避免状态机停在 ACTIVE */
        void onBluetoothOff() {
            mainHandler.post(() -> {
                removeAllTimers();
                pending = false;
                retryCount = 0;
                cancelAttempt = 0;
                isDiscovering = false;
                setState(State.IDLE, "蓝牙已关闭");
            });
        }

        private void handleStart(@Nullable Activity activity) {
            log("startDiscovery 请求：state=" + state + ", isDiscovering=" + realtimeDiscovering() + ", pending=" + pending);
            switch (state) {
                case IDLE:
                    retryCount = 0;
                    cancelAttempt = 0;
                    pending = false;
                    beginStart(activity);
                    break;
                case WAITING_CANCEL:
                    // 已经在 cancel 等待流程里，不再叠加
                    log("正处于 WAITING_CANCEL，忽略本次请求");
                    break;
                case WAITING_STACK:
                case STARTING:
                case ACTIVE:
                    pending = true;
                    log("会话进行中，合并本次搜索请求（pending=true）");
                    break;
            }
        }

        @SuppressLint("MissingPermission")
        private void beginStart(@Nullable Activity activity) {
            if (!isInitialized() || bluetoothAdapter == null) {
                return;
            }
            Context context = activity == null ? application : activity;
            if (!isLocationEnabled(context)) {
                dispatchError(DiscoveryListener.ERROR_LOCATION_SERVICE_CLOSED,
                        "Unable to scan for Bluetooth devices, the phone's location service is not turned on.");
                return;
            }
            if (noLocationPermission(context)) {
                dispatchError(DiscoveryListener.ERROR_LACK_LOCATION_PERMISSION,
                        "Unable to scan for Bluetooth devices, lack location permission.");
                return;
            }
            if (noScanPermission(context)) {
                dispatchError(DiscoveryListener.ERROR_LACK_SCAN_PERMISSION,
                        "Unable to scan for Bluetooth devices, lack scan permission.");
                return;
            }
            if (noConnectPermission(context)) {
                dispatchError(DiscoveryListener.ERROR_LACK_CONNECT_PERMISSION,
                        "Unable to scan for Bluetooth devices, lack connect permission.");
                return;
            }
            // 两轮搜索至少间隔 3s，避开上一轮 FINISHED 后的栈收尾作业
            long sinceFinish = SystemClock.elapsedRealtime() - lastFinishAt;
            if (lastFinishAt != 0 && sinceFinish < MIN_RESTART_GAP_MS) {
                long delay = MIN_RESTART_GAP_MS - sinceFinish;
                log("距上次会话结束仅 " + sinceFinish + "ms，延后 " + delay + "ms 再 start");
                scheduleRestart(delay);
                return;
            }
            if (realtimeDiscovering()) {
                waitCancelOrForce();
            } else {
                forceStart();
            }
        }

        /**
         * 先 cancel 再 start。为什么不能直接用缓存的 isDiscovering：缓存可能长期为 false，
         * 而栈侧仍有未落地的发现作业，因此这里以实时状态 + 有限次数 cancel 为准。
         */
        private void waitCancelOrForce() {
            long now = SystemClock.elapsedRealtime();
            if (cancelAttempt == 0) {
                cancelDeadline = now + CANCEL_TOTAL_TIMEOUT_MS;
            }
            if (cancelAttempt >= CANCEL_MAX_ATTEMPTS || now >= cancelDeadline) {
                log("cancel 已达上限（attempt=" + cancelAttempt + "），强制 start");
                cancelAttempt = 0;
                forceStart();
                return;
            }
            cancelAttempt++;
            setState(State.WAITING_CANCEL, "第 " + cancelAttempt + " 次 cancel");
            cancelForcefully("等待取消");
            mainHandler.postDelayed(cancelSettleRunnable, CANCEL_SETTLE_MS);
        }

        @SuppressLint("MissingPermission")
        private void forceStart() {
            mainHandler.removeCallbacks(cancelSettleRunnable);
            cancelAttempt = 0;
            removeRestart();
            setState(State.STARTING, "startDiscovery");
            isDiscovering = false;
            boolean accepted;
            try {
                accepted = bluetoothAdapter.startDiscovery();
            } catch (Throwable e) {
                log("startDiscovery 异常：" + e.getClass().getSimpleName());
                accepted = false;
            }
            log("startDiscovery -> " + accepted + "（第 " + (retryCount + 1) + " 次尝试，isDiscovering="
                    + realtimeDiscovering() + "）");
            if (!accepted) {
                handleStartFailure("startDiscovery 被系统拒绝");
                return;
            }
            mainHandler.postDelayed(startTimeoutRunnable, START_TIMEOUT_MS);
        }

        private void handleStartFailure(String reason) {
            // 先清掉所有定时器，避免重试与看门狗重复回调
            removeAllTimers();
            cancelAttempt = 0;
            // 即使实时状态是 false 也强制 cancel：清掉 AdapterService 可能残留的 mDiscovering
            cancelForcefully("start 失败兜底");
            if (retryCount < RETRY_DELAYS_MS.length) {
                long delay = RETRY_DELAYS_MS[retryCount];
                retryCount++;
                setState(State.WAITING_STACK, "第 " + retryCount + "/" + RETRY_DELAYS_MS.length
                        + " 次退避重试，" + delay + "ms 后发起");
                scheduleRestart(delay);
            } else {
                retryCount = 0;
                pending = false;
                setState(State.IDLE, "重试耗尽");
                log("重试耗尽（累计约 33s 仍无 STARTED），回调错误");
                dispatchError(DiscoveryListener.ERROR_SCAN_FAILED,
                        reason + "，请关闭后重新打开蓝牙");
            }
        }

        @SuppressLint("MissingPermission")
        private void cancelForcefully(String reason) {
            if (bluetoothAdapter == null) {
                return;
            }
            boolean before = realtimeDiscovering();
            try {
                bluetoothAdapter.cancelDiscovery();
                log("cancelDiscovery（" + reason + "）：已调用，调用前 isDiscovering=" + before);
            } catch (Throwable e) {
                log("cancelDiscovery（" + reason + "）异常：" + e.getClass().getSimpleName());
            }
        }

        private void dispatchError(int errorCode, String errorMsg) {
            BTLogger.instance.e(DEBUG_TAG, errorMsg);
            handleDiscoveryCallback(false, null, -120, errorCode, errorMsg);
        }

        @SuppressLint("MissingPermission")
        private boolean realtimeDiscovering() {
            if (bluetoothAdapter == null) {
                return false;
            }
            try {
                return bluetoothAdapter.isDiscovering();
            } catch (Throwable e) {
                return false;
            }
        }

        private void scheduleRestart(long delay) {
            removeRestart();
            mainHandler.postDelayed(restartRunnable, delay);
        }

        private void removeRestart() {
            mainHandler.removeCallbacks(restartRunnable);
        }

        private void removeAllTimers() {
            mainHandler.removeCallbacks(restartRunnable);
            mainHandler.removeCallbacks(cancelSettleRunnable);
            mainHandler.removeCallbacks(startTimeoutRunnable);
            mainHandler.removeCallbacks(sessionWatchdogRunnable);
        }

        private void setState(State newState, String reason) {
            if (state != newState) {
                log("state " + state + " -> " + newState + "（" + reason + "）");
                state = newState;
            }
        }

        void release() {
            mainHandler.post(() -> {
                removeAllTimers();
                pending = false;
                sessionActive = false;
                retryCount = 0;
                cancelAttempt = 0;
                isDiscovering = false;
                state = State.IDLE;
            });
        }

        /** 日志统一走库的 isDebugMode 开关，tag 固定为 BTManager，便于真机过滤 */
        private void log(String message) {
            BTLogger.instance.d(DEBUG_TAG, "[DiscoveryController] " + message);
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
    public Connection createConnection(@NonNull String address, @NonNull UUIDWrapper uuidWrapper) {
        return createConnection(address, uuidWrapper, null);
    }

    /**
     * 创建连接
     *
     * @param device 蓝牙设备实例
     * @return 返回创建的连接实例，创建失败则返回null
     */
    @Nullable
    public Connection createConnection(@NonNull BluetoothDevice device, @NonNull UUIDWrapper uuidWrapper) {
        return createConnection(device, uuidWrapper, null);
    }

    /**
     * 创建连接
     *
     * @param address  蓝牙地址
     * @param observer 伴生观察者
     * @return 返回创建的连接实例，创建失败则返回null
     */
    @Nullable
    public Connection createConnection(@NonNull String address, @NonNull UUIDWrapper uuidWrapper, EventObserver observer) {
        if (checkStatus()) {
            Objects.requireNonNull(address, "address can't be null");
            Objects.requireNonNull(uuidWrapper, "uuidWrapper can't be null");
            BluetoothDevice remoteDevice;
            try {
                remoteDevice = bluetoothAdapter.getRemoteDevice(address);
            } catch (IllegalArgumentException e) {
                BTLogger.instance.e(DEBUG_TAG, "Invalid bluetooth address: " + address);
                return null;
            }
            return createConnection(remoteDevice, uuidWrapper, observer);
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
    public Connection createConnection(@NonNull BluetoothDevice device, @NonNull UUIDWrapper uuidWrapper, EventObserver observer) {
        if (checkStatus()) {
            Objects.requireNonNull(device, "device can't be null");
            Objects.requireNonNull(uuidWrapper, "uuidWrapper can't be null");

            String key = _getConnectionMapKey(device.getAddress(), uuidWrapper);

            Connection connection = connectionMap.remove(key);
            //如果连接已存在，先释放掉
            if (connection != null) {
                connection.releaseNoEvent();
            }
            connection = new ConnectionImpl(this, bluetoothAdapter, device, uuidWrapper, observer);
            connectionMap.put(key, connection);
            addressList.add(key);
            return connection;
        }
        return null;
    }

    /**
     * 获取集合map用到KEY
     *
     * @param macAddress  蓝牙设备地址
     * @param uuidWrapper UUID包装器
     * @return KEY
     */
    private String _getConnectionMapKey(@NonNull String macAddress, UUIDWrapper uuidWrapper) {
        UUID uuid = uuidWrapper.getUuid();
        return macAddress + ":" + uuid;
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
        for (String key : addressList) {
            Connection connection = connectionMap.get(key);
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
    public Connection getConnection(BluetoothDevice device, @NonNull UUIDWrapper uuidWrapper) {
        return device == null ? null : connectionMap.get(_getConnectionMapKey(device.getAddress(), uuidWrapper));
    }

    @Nullable
    public Connection getConnection(String address, @NonNull UUIDWrapper uuidWrapper) {
        return address == null ? null : connectionMap.get(_getConnectionMapKey(address, uuidWrapper));
    }

    /**
     * 断开连接
     */
    public void disconnectConnection(BluetoothDevice device, @NonNull UUIDWrapper uuidWrapper) {
        if (checkStatus() && device != null) {
            Connection connection = connectionMap.get(_getConnectionMapKey(device.getAddress(), uuidWrapper));
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 断开连接
     */
    public void disconnectConnection(String address, @NonNull UUIDWrapper uuidWrapper) {
        if (checkStatus() && address != null) {
            Connection connection = connectionMap.get(_getConnectionMapKey(address, uuidWrapper));
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
    public void releaseConnection(String address, @NonNull UUIDWrapper uuidWrapper) {
        if (checkStatus() && address != null) {
            String key = _getConnectionMapKey(address, uuidWrapper);
            addressList.remove(key);
            Connection connection = connectionMap.remove(key);
            if (connection != null) {
                connection.release();
            }
        }
    }

    /**
     * 释放连接
     */
    public void releaseConnection(BluetoothDevice device, @NonNull UUIDWrapper uuidWrapper) {
        if (checkStatus() && device != null) {
            String key = _getConnectionMapKey(device.getAddress(), uuidWrapper);
            addressList.remove(key);
            Connection connection = connectionMap.remove(key);
            if (connection != null) {
                connection.release();
            }
        }
    }

    //连接被释放后从集合中移除。不做初始化检查，避免在release过程中触发自动重新初始化
    void onConnectionReleased(BluetoothDevice device, UUIDWrapper uuidWrapper) {
        String key = _getConnectionMapKey(device.getAddress(), uuidWrapper);
        addressList.remove(key);
        connectionMap.remove(key);
    }

    /**
     * 关闭所有连接并释放资源
     */
    @SuppressLint("MissingPermission")
    public synchronized void release() {
        isInitialized = false;
        if (application != null) {
            if (stateBroadcastReceiver != null) {
                try {
                    application.unregisterReceiver(stateBroadcastReceiver);
                } catch (Throwable ignore) {
                }
            }
            stateBroadcastReceiver = null;
            // 接收者常驻，仅在整体释放时注销；状态机的定时器也在这里一并清掉
            discoveryController.release();
            unregisterDiscoveryReceiver();
        }
        if (bluetoothAdapter != null) {
            try {
                bluetoothAdapter.cancelDiscovery();
            } catch (Exception e) {
                BTLogger.instance.e(DEBUG_TAG, "停止搜索异常", e);
            }
        }
        discoveryListeners.clear();
        //逐个释放连接。不能调用releaseAllConnections()，其内部的checkStatus会在release过程中触发自动重新初始化
        for (Connection connection : connectionMap.values()) {
            connection.release();
        }
        connectionMap.clear();
        addressList.clear();
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
    @SuppressLint("MissingPermission")
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
    @SuppressLint("MissingPermission")
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
    @SuppressLint("MissingPermission")
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
