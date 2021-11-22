package cn.wandersnail.btsppexample;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import cn.wandersnail.bluetooth.BTManager;
import cn.wandersnail.bluetooth.ConnectCallback;
import cn.wandersnail.bluetooth.Connection;
import cn.wandersnail.bluetooth.EventObserver;

/**
 * date: 2020/5/7 10:36
 * author: zengfansheng
 */
public class MainActivity extends AppCompatActivity implements EventObserver {
    private Connection connection;
    private TextView tvLog;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvLog = findViewById(R.id.tvLog);
        BluetoothDevice device = getIntent().getParcelableExtra("device");
        connection = BTManager.getInstance().createConnection(device, this);
        if (connection == null) {
            finish();
            return;
        }
        connection.connect(null, new ConnectCallback() {
            @Override
            public void onSuccess() {
                
            }

            @Override
            public void onFail(@NonNull String errMsg, @Nullable Throwable e) {
                runOnUiThread(() -> tvLog.append("连接失败\n"));
            }
        });
        EditText etMsg = findViewById(R.id.etMsg);
        findViewById(R.id.btnSend).setOnClickListener(v -> {
            if (connection.isConnected()) {
                if (!etMsg.getText().toString().isEmpty()) {
                    connection.write(null, etMsg.getText().toString().getBytes(), (device1, tag, value, result) -> {
                        
                    });
                }
            }
        });
    }
    
    
}
