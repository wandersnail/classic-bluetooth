package cn.wandersnail.btsppexample;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import cn.wandersnail.bluetooth.BTManager;
import cn.wandersnail.bluetooth.EventObserver;

/**
 * date: 2019/8/11 09:42
 * author: zengfansheng
 */
public class BaseActivity extends AppCompatActivity implements EventObserver {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BTManager.getInstance().registerObserver(this);
    }

    @Override
    protected void onDestroy() {
        BTManager.getInstance().unregisterObserver(this);
        super.onDestroy();
    }
}
