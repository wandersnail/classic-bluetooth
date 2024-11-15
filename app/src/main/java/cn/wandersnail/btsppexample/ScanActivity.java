package cn.wandersnail.btsppexample;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import cn.wandersnail.bluetooth.BTManager;
import cn.wandersnail.bluetooth.DiscoveryListener;
import cn.wandersnail.commons.helper.PermissionsRequester2;
import cn.wandersnail.commons.util.ToastUtils;
import cn.wandersnail.widget.listview.BaseListAdapter;
import cn.wandersnail.widget.listview.BaseViewHolder;
import cn.wandersnail.widget.listview.PullRefreshLayout;

/**
 * date: 2019/8/4 15:13
 * author: zengfansheng
 */
public class ScanActivity extends AppCompatActivity {
    private ListAdapter listAdapter;
    private PullRefreshLayout refreshLayout;
    private TextView tvEmpty;
    private final List<Device> devList = new ArrayList<>();

    private static class Device {
        BluetoothDevice device;
        int rssi;

        Device(BluetoothDevice device, int rssi) {
            this.device = device;
            this.rssi = rssi;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Device)) return false;
            Device device1 = (Device) o;
            return device.equals(device1.device);
        }

        @Override
        public int hashCode() {
            return Objects.hash(device);
        }
    }
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        initViews();
        BTManager.isDebugMode = true;
        BTManager.getInstance().addDiscoveryListener(discoveryListener);        
        initialize();
    }

    private void initViews() {
        refreshLayout = findViewById(R.id.refreshLayout);
        ListView lv = findViewById(R.id.lv);
        tvEmpty = findViewById(R.id.tvEmpty);
        refreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.colorAccent));
        listAdapter = new ListAdapter(this, devList);
        lv.setAdapter(listAdapter);
        lv.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(ScanActivity.this, MainActivityKt.class);
            intent.putExtra("device", devList.get(position).device);
            startActivity(intent);
        });
        refreshLayout.setOnRefreshListener(() -> {
            if (BTManager.getInstance().isInitialized()) {
                BTManager.getInstance().stopDiscovery();
                doStartDiscovery();
            }
            refreshLayout.postDelayed(() -> refreshLayout.setRefreshing(false), 500);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BTManager.getInstance().release();
        Process.killProcess(Process.myPid());
    }

    private final DiscoveryListener discoveryListener = new DiscoveryListener() {
        @Override
        public void onDiscoveryStart() {
            invalidateOptionsMenu();
        }

        @Override
        public void onDiscoveryStop() {
            invalidateOptionsMenu();
        }

        @Override
        public void onDiscoveryError(int errorCode, @NonNull String errorMsg) {
            switch(errorCode) {
                case DiscoveryListener.ERROR_LACK_LOCATION_PERMISSION://缺少定位权限		
                    break;
                case DiscoveryListener.ERROR_LOCATION_SERVICE_CLOSED://位置服务未开启		
                    break;
                case DiscoveryListener.ERROR_LACK_SCAN_PERMISSION://缺少搜索权限		
                    break;
                case DiscoveryListener.ERROR_SCAN_FAILED://搜索失败
                    ToastUtils.showShort("搜索出错：" + errorCode);
                    break;
            }
        }

        @Override
        public void onDeviceFound(@NonNull BluetoothDevice device, int rssi) {
            tvEmpty.setVisibility(View.INVISIBLE);
            Device dev = new Device(device, rssi);
            if (!devList.contains(dev)) {
                devList.add(dev);
                listAdapter.notifyDataSetChanged();
            }
        }
    };
    
    //需要进行检测的权限
    private List<String> getNeedPermissions() {
        List<String> list = new ArrayList<>();
        if (getApplicationInfo().targetSdkVersion >= 29) {//target sdk版本在29以上的需要精确定位权限才能搜索到蓝牙设备
            list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        //Android 12需要
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN);
            list.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        return list;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("Main", "onResume");
        if (BTManager.getInstance().isInitialized()) {
            if (BTManager.getInstance().isBluetoothOn()) {
                doStartDiscovery();
            } else {
                startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (BTManager.getInstance().isInitialized()) {
            BTManager.getInstance().stopDiscovery();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.scan, menu);
        MenuItem item = menu.findItem(R.id.menuProgress);
        item.setActionView(R.layout.toolbar_indeterminate_progress);
        item.setVisible(BTManager.getInstance().isDiscovering());
        return super.onCreateOptionsMenu(menu);
    }

    private void initialize() {
        //动态申请权限
        PermissionsRequester2 permissionsRequester = new PermissionsRequester2(this);
        permissionsRequester.setCallback(list -> {
            
        });
        permissionsRequester.checkAndRequest(getNeedPermissions());
    }

    private void doStartDiscovery() {
        devList.clear();
        listAdapter.notifyDataSetChanged();
        tvEmpty.setVisibility(View.VISIBLE);
        BTManager.getInstance().startDiscovery(this);
    }

    private static class ListAdapter extends BaseListAdapter<Device> {

        ListAdapter(@NotNull Context context, @NotNull List<Device> list) {
            super(context, list);
        }

        @NotNull
        @Override
        protected BaseViewHolder<Device> createViewHolder(int i) {
            return new BaseViewHolder<Device>() {
                TextView tvName;
                TextView tvAddr;
                TextView tvRssi;

                @Override
                public void onBind(@NonNull Device device, int i) {
                    tvName.setText(TextUtils.isEmpty(device.device.getName()) ? "N/A" : device.device.getName());
                    tvAddr.setText(device.device.getAddress());
                    tvRssi.setText("" + device.rssi);
                }

                @NotNull
                @Override
                public View createView() {
                    View view = View.inflate(context, R.layout.item_scan, null);
                    tvName = view.findViewById(R.id.tvName);
                    tvAddr = view.findViewById(R.id.tvAddr);
                    tvRssi = view.findViewById(R.id.tvRssi);
                    return view;
                }
            };
        }
    }
}
