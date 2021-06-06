package br.ufpr.nr2.mobangelo

import android.bluetooth.BluetoothAdapter
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import br.ufpr.nr2.mobangelo.bluetooth.Constants

class EmergencyMessageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_message)
        val emergencyMessage = intent.extras?.get(Constants.EMERGENCY_MESSAGE)!!.toString()
        findViewById<TextView>(R.id.tv_emergency_message).text = emergencyMessage

        //Start of dynamic title code---------------------
        val actionBar: ActionBar? = supportActionBar
        if (actionBar != null) {
            val dynamicTitle: String = "MobAngelo - " + BluetoothAdapter.getDefaultAdapter().name
            actionBar.title = (dynamicTitle)
        }
        //End of dynamic title code----------------------
    }
}
