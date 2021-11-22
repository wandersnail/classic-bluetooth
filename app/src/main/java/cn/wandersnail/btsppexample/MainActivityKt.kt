package cn.wandersnail.btsppexample

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cn.wandersnail.bluetooth.BTManager
import cn.wandersnail.bluetooth.ConnectCallback
import cn.wandersnail.bluetooth.Connection
import cn.wandersnail.bluetooth.EventObserver
import cn.wandersnail.commons.poster.RunOn
import cn.wandersnail.commons.poster.ThreadMode
import cn.wandersnail.commons.util.ToastUtils
import kotlinx.android.synthetic.main.activity_main.*

class MainActivityKt : AppCompatActivity(), EventObserver {
    private var connection: Connection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val device: BluetoothDevice? = intent.getParcelableExtra("device")
        if (device == null ) {
            finish()
            return
        }
        connection = BTManager.getInstance().createConnection(device, this)
        if (connection == null ) {
            finish()
            return
        }
        connection!!.connect(null, object : ConnectCallback {
            override fun onSuccess() {
                
            }

            override fun onFail(errMsg: String, e: Throwable?) {
                runOnUiThread { tvLog.append("连接失败\n") }
            }
        })
        btnSend.setOnClickListener { 
            if (connection?.isConnected == true) {
                if (etMsg.text?.isNotEmpty() == true) {
                    connection?.write(null, etMsg.text!!.toString().toByteArray(), null)
                }
            } else {
                ToastUtils.showShort("未连接")
            }
        }
    }

    @RunOn(ThreadMode.MAIN)
    override fun onRead(device: BluetoothDevice, value: ByteArray) {
        tvLog.append("${String(value)}\n")
    }

    override fun onWrite(device: BluetoothDevice, tag: String, value: ByteArray, result: Boolean) {
        
    }

    @RunOn(ThreadMode.MAIN)
    override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
        val msg = when (state) {
            Connection.STATE_PAIRING -> "配对中..."
            Connection.STATE_PAIRED -> "配对成功"
            Connection.STATE_CONNECTED -> "连接成功"
            Connection.STATE_DISCONNECTED -> "连接断开"
            Connection.STATE_RELEASED -> "连接已销毁"
            else -> ""
        }
        if (msg.isNotEmpty()) {
            tvLog.append("$msg\n")
        }
    }

    override fun onDestroy() {
        connection?.release()
        super.onDestroy()
    }
}
