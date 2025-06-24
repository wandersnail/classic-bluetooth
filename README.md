## 推荐一款工具箱【蜗牛工具箱】

> 涵盖广，功能丰富。生活实用、效率办公、图片处理等等，还有隐藏的VIP功能，总之很多惊喜的功能。各大应用市场搜索【蜗牛工具箱】安装即可。

<div align="center">
    <img src="https://pic1.imgdb.cn/item/685a0c1658cb8da5c8696135.png" width=150>
    <img src="https://pic1.imgdb.cn/item/685a0c1758cb8da5c869613d.png" width=150>
    <img src="https://pic1.imgdb.cn/item/685a0c1758cb8da5c869613e.png" width=150>
    <img src="https://pic1.imgdb.cn/item/685a0c1758cb8da5c8696140.png" width=150>
</div>

**部分功能介绍**

- 【滚动字幕】超实用应援打call神器，输入文字内容使文字在屏幕中滚动显示；
- 【振动器】可自定义振动频率、时长，达到各种有意思的效果；
- 【测量仪器】手机当直尺、水平仪、指南针、分贝仪；
- 【文件加解密】可加密任意文件，可用于私密文件分享；
- 【金额转大写】将阿拉伯数字类型的金额转成中文大写；
- 【二维码】调用相机扫描或扫描图片识别二维码，支持解析WiFi二维码获取密码，输入文字生成相应的二维码；
- 【图片模糊处理】将图片进行高斯模糊处理，毛玻璃效果；
- 【黑白图片上色】黑白图片变彩色；
- 【成语词典】查询成语拼音、释义、出处、例句；
- 【图片拼接】支持长图、4宫格、9宫格拼接；
- 【自动点击】自动连点器，解放双手；
- 【图片加水印】图片上添加自定义水印；
- 【网页定时刷新】设定刷新后自动定时刷新网页；
- 【应用管理】查看本机安装的应用详细信息，并可提取安装包分享；
- 【BLE调试】低功耗蓝牙GATT通信调试，支持主从模式，可多设备同时连接，实时日志；
- 【SPP蓝牙调试】经典蓝牙Socket通信调试，支持自定义UUID，多设备同时连接，实时日志；
- 【USB调试】USB串口调试，兼容芯片多，实时日志；
- 【MQTT调试】MQTT通信调试，实时日志、自定义按键、订阅主题保存；
- 【TCP/UDP调试】支持TCP客户端、TCP服务端、UDP客户端、UDP服务端；
- 【私密相册】加密存储图片，保护个人隐私；
- ……

已集成上百个小工具，持续更新中...

点击下方按钮或扫码下载【蜗牛工具箱】

[![](https://img.shields.io/badge/下载-%E8%9C%97%E7%89%9B%E5%B7%A5%E5%85%B7%E7%AE%B1-red.svg)](https://www.pgyer.com/8AN5OhVd)

<img src="https://www.pgyer.com/app/qrcode/8AN5OhVd" width=150>

----------------------------------------------

# Android传统（经典）蓝牙框架使用说明

## 最新版本
[![Maven Central](https://img.shields.io/maven-central/v/cn.wandersnail/classic-bluetooth.svg?color=4AC61C)](https://central.sonatype.com/artifact/cn.wandersnail/classic-bluetooth/versions)
[![Release](https://jitpack.io/v/cn.wandersnail/classic-bluetooth.svg)](https://jitpack.io/#cn.wandersnail/classic-bluetooth)
[![](https://img.shields.io/badge/源码-github-blue.svg)](https://github.com/wandersnail/classic-bluetooth)
[![](https://img.shields.io/badge/源码-码云-red.svg)](https://gitee.com/fszeng/classic-bluetooth)

## 功能
- 支持多设备同时连接
- 支持观察者监听或回调方式。注意：观察者监听和回调只能取其一！
- 支持使用注解@RunOn控制回调线程
- 支持设置回调或观察者的方法默认执行线程

## 配置

1. 因为使用了jdk8的一些特性，需要在module的build.gradle里添加如下配置：
```
//纯java的项目
android {
	compileOptions {
		sourceCompatibility JavaVersion.VERSION_1_8
		targetCompatibility JavaVersion.VERSION_1_8
	}
}

//有kotlin的项目还需要在project的build.gradle里添加
allprojects {
    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).all {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8

        kotlinOptions {
            jvmTarget = '1.8'
        }
    }
}
```

2. module的build.gradle中的添加依赖，自行修改为最新版本，commons-android [最新版本](https://gitee.com/fszeng/commons-android) ，同步后通常就可以用了：
```
dependencies {
	...
	implementation 'cn.wandersnail:classic-bluetooth:latestVersion'
	//额外依赖
	implementation 'cn.wandersnail:commons-android:latestVersion'
}
```

3. 在project的build.gradle里的repositories添加内容，最好两个都加上，添加完再次同步即可。
```
allprojects {
	repositories {
		...
		mavenCentral()
		maven { url 'https://jitpack.io' }
	}
}
```

## 使用方法

### 初始化SDK

**实例化有两种方式：**

1. 使用默认方式自动构建实例，直接获取实例即可

```
//实例化并初始化
BTManager.getInstance().initialize(application);
```

2. 构建自定义实例，必须在BTManager.getInstance()之前！！

```
BTManager manager = BTManager.getBuilder()
        .setExecutorService(executorService)//自定义线程池用来执行后台任务，也可使用默认
		.setObserveAnnotationRequired(false)//不强制使用{@link Observe}注解才会收到被观察者的消息，强制使用的话，性能会好一些
		.setMethodDefaultThreadMode(ThreadMode.MAIN)//指定回调方法和观察者方法的默认线程
		.build();
manager.initialize(application);
```

### 销毁SDK

```
//如果中途需要修改配置重新实例化，调用此方法后即可重新构建BTManager实例
BTManager.getInstance().destroy();
```

### 日志输出控制

```
BTManager.isDebugMode = true;;//开启日志打印
```

### 搜索设备

1. 定义搜索监听器

   > Android6.0以上搜索需要至少模糊定位权限，如果targetSdkVersion设置29以上需要精确定位权限。权限需要动态申请

```
private DiscoveryListener discoveryListener = new DiscoveryListener() {
	@Override
	public void onDiscoveryStart() {
		//搜索开始
	}

	@Override
	public void onDiscoveryStop() {
		//搜索停止
	}

    /**
     * 搜索到蓝牙设备
     *
     * @param device 搜索到的设备
     * @param rssi   信号强度
     */
	@Override
	public void onDeviceFound(@NonNull BluetoothDevice device, int rssi) {
		//搜索结果
	}

	@Override
	public void onDiscoveryError(int errorCode, @NotNull String errorMsg) {
		switch(errorCode) {
			case ScanListener.ERROR_LACK_LOCATION_PERMISSION://缺少定位权限		
				break;
			case ScanListener.ERROR_LOCATION_SERVICE_CLOSED://位置服务未开启		
				break;
			case ScanListener.ERROR_SCAN_FAILED://搜索失败
				break;
		}
	}
};
```
2. 添加监听

```
BTManager.getInstance().addDiscoveryListener(discoveryListener);
```
3. 开始搜索

```
BTManager.getInstance().startDiscovery();
```
4. 停止搜索

```
BTManager.getInstance().stopDiscovery();
```
5. 停止监听

```
BTManager.getInstance().removeDiscoveryListener(discoveryListener);
```

### 观察者模式数据及事件

1. 定义观察者。实现EventObserver接口即可：

```
public class MainActivity extends AppCompatActivity implements EventObserver {
    /**
     * 使用{@link Observe}确定要接收消息，{@link RunOn}指定在主线程执行方法，设置{@link Tag}防混淆后找不到方法
     */
    @Tag("onConnectionStateChanged") 
    @Observe
    @RunOn(ThreadMode.MAIN)
    @Override
    public void onConnectionStateChanged(@NonNull BluetoothDevice device, int state) {
        switch (state) {
            case Connection.STATE_CONNECTING:
                break;
            case Connection.STATE_PAIRING:
                break;
            case Connection.STATE_PAIRED:
                break;
            case Connection.STATE_CONNECTED:
                break;
            case Connection.STATE_DISCONNECTED:
                break;            
            case Connection.STATE_RELEASED:
                break;
        }
    }
	
    /**
     * 如果{@link BTManager.Builder#setObserveAnnotationRequired(boolean)}设置为false时，无论加不加{@link Observe}注解都会收到消息。
     * 设置为true时，必须加{@link Observe}才会收到消息。
     * 默认为false，方法默认执行线程在{@link BTManager.Builder#setMethodDefaultThreadMode(ThreadMode)}指定
     */
    @Observe
    @Override
    public void onRead(@NonNull BluetoothDevice device, @NonNull byte[] value) {
        Log.d("BTManager", "收到数据：" + StringUtils.toHex(value, " "));
    }
    
    @Override
    public void onWrite(@NonNull BluetoothDevice device, @NonNull String tag, @NonNull byte[] value, boolean result) {
        Log.d("BTManager", "写入结果：" + result);
    }
}
```

2. 注册观察者

```
BTManager.getInstance().registerObserver(observer);
```

3. 取消注册观察者

```
BTManager.getInstance().unregisterObserver(observer);
```

### 连接

1. 建立指定设备指定UUID的连接

```
//UUIDWrapper.useDefault()，使用默认的UUID(00001101-0000-1000-8000-00805f9b34fb)
//UUIDWrapper.useCustom()，自定义UUID
connection = BTManager.getInstance().createConnection(device, UUIDWrapper.useDefault(), observer);//观察者监听连接状态
connection.connect(new ConnectCallback() {
    @Override
    public void onSuccess() {
      
    }
    
    @Override
    public void onFail(@NonNull String errMsg, @Nullable Throwable e) {
    
    }
});
```

2. 断开连接，还可再次连接

```
BTManager.getInstance().disconnectConnection(device, UUIDWrapper.useDefault());//断开指定设备的指定UUID的连接
//BTManager.getInstance().disconnectAllConnections();//断开所有连接
```

3. 释放连接，不可重连，需要重新建立连接

```
BTManager.getInstance().releaseConnection(device, UUIDWrapper.useDefault());//释放指定设备的指定UUID的连接
//BTManager.getInstance().releaseAllConnections();//释放所有连接
```

### 读写数据

1. 接收数据

上面说到的定义观察者。实现EventObserver接口，在onRead里接收数据。

2. 写入数据

**两种方式：**

2.1 接口回调方式

```
/**
 * 写数据，加入队列尾部
 *
 * @param tag      数据标识
 * @param value    要写入的数据
 * @param callback 写入回调。不为null时，写入结果以回调返回；传null时，写入结果以通知观察者方式返回
 */
connection.write(tag, value, new WriteCallback() {
    @Override
    public void onWrite(@NonNull BluetoothDevice device, @NonNull String tag, @NonNull byte[] value, boolean result) {
        
    }
});

/**
 * 写数据，加入队列最前
 *
 * @param tag      数据标识
 * @param value    要写入的数据
 * @param callback 写入回调。不为null时，写入结果以回调返回；传null时，写入结果以通知观察者方式返回
 */
connection.writeImmediately(tag, value, new WriteCallback() {
    @Override
    public void onWrite(@NonNull BluetoothDevice device, @NonNull String tag, @NonNull byte[] value, boolean result) {
     
    }
});
```

2.2 使用观察者模式接收结果

和接收数据一样，定义观察者。实现EventObserver接口，在onWrite里接收写入结果。

### 释放SDK，释放后必须重新初始化后方可使用

```
BTManager.getInstance().release();
```
### 代码混淆

```
-keep class * implements cn.wandersnail.commons.observer.Observe {
	public <methods>;
}
```

## Demo效果预览
![image](https://s1.ax1x.com/2020/05/07/YZBLHs.gif)
