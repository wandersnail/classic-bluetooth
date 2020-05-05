package cn.wandersnail.btsppexample;

import android.app.Application;

import cn.wandersnail.bluetooth.BTManager;
import cn.wandersnail.commons.base.AppHolder;
import cn.wandersnail.commons.poster.ThreadMode;

/**
 * date: 2019/8/4 15:14
 * author: zengfansheng
 */
public class MyApplication extends Application {
    private static MyApplication instance;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        AppHolder.initialize(this);
        //构建自定义实例，需要在EasyBLE.getInstance()之前        
        BTManager manager = BTManager.getBuilder()
                .setObserveAnnotationRequired(false)//不强制使用{@link Observe}注解才会收到被观察者的消息，强制使用的话，性能会好一些
                .setMethodDefaultThreadMode(ThreadMode.BACKGROUND)//指定回调方法和观察者方法的默认线程
                .build();
        manager.initialize(this);
    }
    
    public static MyApplication getInstance() {
        return instance;
    }
}
