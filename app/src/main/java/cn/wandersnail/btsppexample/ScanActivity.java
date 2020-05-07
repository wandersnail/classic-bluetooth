package cn.wandersnail.btsppexample;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
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

import cn.wandersnail.bluetooth.BTManager;
import cn.wandersnail.bluetooth.DiscoveryListener;
import cn.wandersnail.commons.helper.PermissionsRequester;
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
    private List<BluetoothDevice> devList = new ArrayList<>();
    private PermissionsRequester permissionsRequester;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        initViews();
        BTManager.isDebugMode = true;
        BTManager.getInstance().addDiscoveryListener(scanListener);        
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
            Intent intent = new Intent(ScanActivity.this, MainActivity.class);
            intent.putExtra("device", devList.get(position));
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

    private DiscoveryListener scanListener = new DiscoveryListener() {
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
                case DiscoveryListener.ERROR_SCAN_FAILED://搜索失败
                    ToastUtils.showShort("搜索出错：" + errorCode);
                    break;
            }
        }

        @Override
        public void onDeviceFound(@NonNull BluetoothDevice device) {
            tvEmpty.setVisibility(View.INVISIBLE);
            if (!devList.contains(device)) {
                devList.add(device);
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
        permissionsRequester = new PermissionsRequester(this);
        permissionsRequester.setCallback(list -> {
            
        });
//        permissionsRequester.checkAndRequest(getNeedPermissions());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionsRequester.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void doStartDiscovery() {
        devList.clear();
        listAdapter.notifyDataSetChanged();
        tvEmpty.setVisibility(View.VISIBLE);
        BTManager.getInstance().startDiscovery();
    }

    private static class ListAdapter extends BaseListAdapter<BluetoothDevice> {

        ListAdapter(@NotNull Context context, @NotNull List<BluetoothDevice> list) {
            super(context, list);
        }

        @NotNull
        @Override
        protected BaseViewHolder<BluetoothDevice> createViewHolder(int i) {
            return new BaseViewHolder<BluetoothDevice>() {
                TextView tvName;
                TextView tvAddr;

                @Override
                public void onBind(@NonNull BluetoothDevice device, int i) {
                    tvName.setText(TextUtils.isEmpty(device.getName()) ? "N/A" : device.getName());
                    tvAddr.setText(device.getAddress());
                }

                @NotNull
                @Override
                public View createView() {
                    View view = View.inflate(context, R.layout.item_scan, null);
                    tvName = view.findViewById(R.id.tvName);
                    tvAddr = view.findViewById(R.id.tvAddr);
                    return view;
                }
            };
        }
    }
}
