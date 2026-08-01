package cn.wandersnail.btsppexample

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cn.wandersnail.bluetooth.*
import cn.wandersnail.btsppexample.databinding.ActivityMainBinding
import cn.wandersnail.commons.poster.RunOn
import cn.wandersnail.commons.poster.ThreadMode
import cn.wandersnail.commons.util.ToastUtils
import java.util.*

class MainActivityKt : AppCompatActivity(), EventObserver {
    private var connection: Connection? = null
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val device: BluetoothDevice? = intent.getParcelableExtra("device")
        if (device == null) {
            finish()
            return
        }
        connection =
            BTManager.getInstance().createConnection(
                device,
                UUIDWrapper.useDefault(),
                this
            )
        if (connection == null) {
            finish()
            return
        }
        connection!!.connect(object : ConnectCallback {
            override fun onSuccess() {

            }

            override fun onFail(errMsg: String, e: Throwable?) {
                runOnUiThread { binding.tvLog.append("连接失败\n") }
            }
        })
        binding.btnSend.setOnClickListener {
            if (connection?.isConnected == true) {
                if (binding.etMsg.text?.isNotEmpty() == true) {
                    connection?.write(null, binding.etMsg.text!!.toString().toByteArray(), null)
                }
            } else {
                ToastUtils.showShort("未连接")
            }
        }
    }

    @RunOn(ThreadMode.MAIN)
    override fun onRead(device: BluetoothDevice, warpper: UUIDWrapper, value: ByteArray) {
        binding.tvLog.append("${String(value)}\n")
    }

    override fun onWrite(device: BluetoothDevice, warpper: UUIDWrapper, tag: String, value: ByteArray, result: Boolean) {

    }

    @RunOn(ThreadMode.MAIN)
    override fun onConnectionStateChanged(device: BluetoothDevice, warpper: UUIDWrapper, state: Int) {
        val msg = when (state) {
            Connection.STATE_PAIRING -> "配对中..."
            Connection.STATE_PAIRED -> "配对成功"
            Connection.STATE_CONNECTED -> "连接成功"
            Connection.STATE_DISCONNECTED -> "连接断开"
            Connection.STATE_RELEASED -> "连接已销毁"
            else -> ""
        }
        if (msg.isNotEmpty()) {
            binding.tvLog.append("$msg\n")
        }
    }

    override fun onDestroy() {
        connection?.release()
        super.onDestroy()
    }
}
