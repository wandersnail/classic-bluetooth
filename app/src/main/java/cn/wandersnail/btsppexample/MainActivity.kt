package cn.wandersnail.btsppexample

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cn.wandersnail.bluetooth.BTManager
import cn.wandersnail.bluetooth.ConnectCallbck
import cn.wandersnail.bluetooth.Connection

class MainActivity : AppCompatActivity() {
    private var connection: Connection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val device: BluetoothDevice = intent.getParcelableExtra("device")
        connection = BTManager.getInstance().createConnection(device)
        if (connection == null) {
            finish()
            return
        }
        connection!!.connect(null, object : ConnectCallbck {
            override fun onSuccess() {
                
            }

            override fun onFail(errMsg: String, e: Throwable?) {
                
            }
        })
    }

    override fun onDestroy() {
        connection?.release()
        super.onDestroy()
    }
}
