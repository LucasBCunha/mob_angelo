package br.ufpr.nr2.mobangelo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import br.ufpr.nr2.mobangelo.R.*
import br.ufpr.nr2.mobangelo.bluetooth.Constants
import br.ufpr.nr2.mobangelo.helpers.LogHelper


class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MOBANGELO"
        const val CONFIG_FILE = "mob_angelo_configuration"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_main)

        LogHelper.saveLogInSDCard(applicationContext)

        findViewById<Button>(id.btn_bt_client_activity).setOnClickListener{
            val intent = Intent(this, BluetoothActivity::class.java)
            Log.d(TAG, findViewById<EditText>(R.id.edt_device_competency).text.toString())
            intent.putExtra(Constants.MY_DEVICE_COMPETENCY, findViewById<EditText>(R.id.edt_device_competency).text.toString())
            startActivity(intent)
        }
    }
}
