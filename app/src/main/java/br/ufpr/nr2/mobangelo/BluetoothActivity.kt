package br.ufpr.nr2.mobangelo

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import br.ufpr.nr2.mobangelo.adapters.NeighbourListAdapter
import br.ufpr.nr2.mobangelo.bluetooth.CommunityManager
import br.ufpr.nr2.mobangelo.bluetooth.Constants
import br.ufpr.nr2.mobangelo.bluetooth.CommunicationManager
import br.ufpr.nr2.mobangelo.helpers.LogHelper
import java.io.UnsupportedEncodingException
import java.lang.ref.WeakReference


class BluetoothActivity : AppCompatActivity(){
    var lv: ListView? = null
    var stateTv: TextView? = null
    var pendingDevicesTv: TextView? = null
    // In the outer class, instantiate a WeakReference to BluetoothActivity.
    private val outerClass = WeakReference<BluetoothActivity>(this)
    private val mHandler = MyHandler(outerClass)
    private var mNeighbours: MutableList<CommunityManager.Neighbour> = mutableListOf()
    private var communicationManager: CommunicationManager? = null
    private var adapter: NeighbourListAdapter? = null
    private var mCompetence: Int? = null
    private var btConfiguration: Button? = null

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        private const val REQUEST_CODE_REQUIRED_PERMISSIONS = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogHelper.saveLogInSDCard(applicationContext)
        setContentView(R.layout.activity_bluetooth_client)

        btConfiguration = findViewById(R.id.btn_control_operation)
        btConfiguration?.setOnClickListener(clickListener)

        findViewById<Button>(R.id.btn_configurations).setOnClickListener(clickListener)
        findViewById<Button>(R.id.btn_emergency).setOnClickListener(clickListener)

        lv = findViewById(R.id.neighboursListView)
        stateTv = findViewById(R.id.tv_operation_state)
        pendingDevicesTv = findViewById(R.id.tv_pending_devices_size)
        
        supportActionBar?.setDisplayShowHomeEnabled(true);
        supportActionBar?.setLogo(R.drawable.ic_mobangelo_white);
        supportActionBar?.setDisplayUseLogoEnabled(true);
    }

    override fun onResume(){
        super.onResume()
        mCompetence = loadCompetence()

        if(mCompetence == null){
            findViewById<Button>(R.id.btn_control_operation).isEnabled = false
            findViewById<Button>(R.id.btn_emergency).isEnabled = false
        }else {
            val adapter = NeighbourListAdapter(this, mNeighbours)
            this.adapter = adapter
            lv?.adapter = adapter
            if(communicationManager == null){
                communicationManager = CommunicationManager(mHandler, this.baseContext)
            }
            val name = communicationManager?.mAdapter?.name
            findViewById<TextView>(R.id.tv_mdevice).text = "$name - ${Constants.getCompetencyLabel(mCompetence!!)}"
            findViewById<Button>(R.id.btn_control_operation).isEnabled = true
            findViewById<Button>(R.id.btn_emergency).isEnabled = true
        }
    }

    override fun onStart() {
        super.onStart()
        if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_REQUIRED_PERMISSIONS);
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try{
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException){
            Log.d(Constants.TAG, "Receiver was not registered!")
        }
//        TODO: should it leave bluetooth on in case user want's it for another purpose?
        Log.d(Constants.TAG, "OnDestroy called!!")
        callStopOperation()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode) {
            Constants.REQUEST_ENABLE_BT -> {
                if (resultCode == Activity.RESULT_OK) {
                    Log.i(MainActivity.TAG, "Bluetooth started...")
                    val r = Runnable {
                        requestEnableDiscoverable()
                    }
                    val h = Handler()
                    h.postDelayed(r, 100)// <-- the "100" is the delay time in milliseconds.
                } else {
                    Log.d(Constants.TAG, "Bluetooth did not start")
                    Toast.makeText(this, "Failed to start Bluetooth", Toast.LENGTH_SHORT).show()
                }
            }
            Constants.REQUEST_DISCOVERABLE_BT -> {
                if (resultCode == Activity.RESULT_OK) {
                    Log.d(Constants.TAG, "Device is now discoverable")
                } else {
                    Log.d(Constants.TAG, "Failed to make device discoverable...")
                }
//              Start socket thread
                stateTv?.text = "Iniciando..."
                startOperation()
            }
        }
    }

    private fun requestEnableDiscoverable(){
        Log.d(Constants.TAG, "Asking to make device discoverable")
        // Enabling discoverable
        val intent = Intent(ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600)
        }
        startActivityForResult(intent, Constants.REQUEST_DISCOVERABLE_BT)
    }

    private val clickListener = View.OnClickListener { view ->
        when (view.id) {
            R.id.btn_control_operation -> {
                // Starting Bluetooth
                val canStart = communicationManager!!.canRun()
                if (!canStart) {
                    // Device doesn't support Bluetooth
                    Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
                    Log.e(Constants.TAG, "Bluetooth not supported.")
                } else {
                    if (!communicationManager!!.isOperating()) {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        startActivityForResult(
                            enableBtIntent,
                            Constants.REQUEST_ENABLE_BT
                        )
                    } else {
                        callStopOperation()
                    }
                }
            }
            R.id.btn_configurations -> {
                callStopOperation()
                val intent = Intent(this, ConfigurationActivity::class.java)
                startActivity(intent)
            }
            R.id.btn_emergency -> {
                Log.d(Constants.TAG, "[Activity] Emergency clicked")
                if (communicationManager!!.isOperating()) {
                    Log.i("METRICS", "Start emergency")
                    Log.d(Constants.TAG, "[Activity] Calling handleEmergency")
                    communicationManager!!.handleLocalEmergency()
                }
            }
        }
    }

    private fun callStopOperation(){
        communicationManager?.stopOperation()
        dataSetChanged()
        stateTv?.text = "Parado"
        pendingDevicesTv?.text = "--"
        btConfiguration?.text = "Iniciar operação"
        try{
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException){
            Log.d(Constants.TAG, "Receiver was not registered!")
        }
    }

    private fun startOperation() {
        if (communicationManager!!.isDiscovering()){
            Log.d(Constants.TAG, "Manager is already running, returning...")
            return
        }
        var filter = IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(receiver, filter)

        // Register for broadcasts when discovery has finished
        filter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(receiver, filter)

        filter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        this.registerReceiver(receiver, filter)

        // Request discover from BluetoothAdapter
        communicationManager!!.startOperation()
        btConfiguration?.text = "Parar operação"
    }

    fun openEmergencyActivity(message: String){
        val intent = Intent(this, EmergencyMessageActivity::class.java)
        intent.putExtra(Constants.EMERGENCY_MESSAGE, message)
        startActivity(intent)
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.i("METRICS", "Start discovery")
                    Log.d(Constants.TAG, "[BluetoothActivity] Discovery Started...")
                    stateTv?.text = "Buscando..."
                    communicationManager?.discoveryStarted()
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    Log.d(Constants.TAG, "[BluetoothActivity] Device discovered...")
                    communicationManager?.deviceFound(device!!)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.i("METRICS", "Discovery Finished")
                    Log.d(Constants.TAG, "[BluetoothActivity] Discovery Finished...")
                    stateTv?.text = "Descoberta finalizada..."
                    communicationManager?.discoveryFinished()
                }
            }
        }
    }

    // Declare the Handler as a static class.
    private class MyHandler(private val outerClass: WeakReference<BluetoothActivity>) : Handler() {
        //Using a weak reference means you won't prevent garbage collection

        override fun handleMessage(msg: Message?) {
            val activity = outerClass.get()
            when(msg?.what){
                Constants.MESSAGE_EMERGENCY_READ, Constants.MESSAGE_READ -> {
                    var readMessage: String? = null
                    try {
                        readMessage = String(msg.obj as ByteArray, Charsets.UTF_8)
                        readMessage = readMessage.substring(0, readMessage.indexOf("\\0"))
                        Log.d(Constants.TAG, "Received message:  $readMessage")
                        if (msg.what == Constants.MESSAGE_EMERGENCY_READ) {

                            outerClass.get()?.callStopOperation()
                            outerClass.get()?.openEmergencyActivity(readMessage)
                        }
                    } catch (e: UnsupportedEncodingException) {
                        e.printStackTrace()
                    }
                    if (readMessage == null)
                        Log.d(Constants.TAG, "Received is empty")
                }
                Constants.MESSAGE_WRITE -> {
                    val writeBuf = msg.obj as ByteArray
                    // construct a string from the buffer
                    val writeMessage = String(writeBuf)
                    Log.d(Constants.TAG, "Message sent:  $writeMessage")
                }
                Constants.MESSAGE_NAME -> {
                    if (null != activity) {
                        Log.d(
                            Constants.TAG,
                            "Connected to ${msg.data.getString(Constants.DEVICE_NAME)}"
                        )
                    }
                }
                Constants.MESSAGE_HANDSHAKE -> {
//                    val handshake = Constants.CLIENT_HANDSHAKE
//                    val deviceAddress = msg.data.getString(Constants.DEVICE_ADDRESS)
                    val writeBuf = msg.obj as ByteArray
                    // construct a string from the buffer
                    val writeMessage = String(writeBuf)
                    Log.d(Constants.TAG, "Sending handshake with content: $writeMessage")
                }
//                Constants.MESSAGE_CONNECTION_FAILED -> {
//                    val message = msg.data.getString(Constants.TOAST)
//                    Log.d(Constants.TAG,"Connection lost to message")
//                }
                Constants.MESSAGE_CONNECTION_LOST -> {
                    val deviceAddress = msg.data.getString(Constants.DEVICE_ADDRESS)
                    Log.d(Constants.TAG, "Connection lost to $deviceAddress")
                }
                Constants.MESSAGE_EMERGENCY_SENT -> {
                    outerClass.get()?.callStopOperation()
                    val writeBuf = msg.obj as String
                    outerClass.get()
                        ?.openEmergencyActivity("Mensagem de emergência enviada para $writeBuf! Operação encerrada.")
                    Log.i("METRICS", "Emergency message sent & confirmed")
                }
                Constants.MESSAGE_DATASET_CHANGED -> {
                    outerClass.get()?.dataSetChanged()
                }
                Constants.MESSAGE_DEVICE_FOUND -> {
                    val size = msg.data.getInt(Constants.PENDING_DEVICES)
                    outerClass.get()?.pendingDevicesTv?.text = size.toString()
                }
                Constants.MESSAGE_COMMUNITY_COMPLETE -> {
                    outerClass.get()?.stateTv?.text = "Formação concluida"
                    outerClass.get()?.pendingDevicesTv?.text = 0.toString()
                }
                Constants.MESSAGE_NO_NEIGHBOUR -> {
                    Toast.makeText(
                        outerClass.get()?.baseContext,
                        "Nenhum vizinho disponível no momento!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {
                    Log.d(Constants.TAG, "Unknown message: ${msg?.what}")
                }
            }
        }
    }

    fun dataSetChanged(){
        mNeighbours.clear()
        if (communicationManager != null){
            mNeighbours.apply { addAll(communicationManager!!.getNeighboursList()) }
        }
        adapter?.notifyDataSetChanged()
    }

    /** Handles user acceptance (or denial) of our permission request.  */
    @CallSuper
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE_REQUIRED_PERMISSIONS) {
            return
        }
        for (grantResult in grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(
                    this,
                    "Faltando permissão para executar serviço bluetooth",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return
            }
        }
        recreate()
    }

    /** Returns true if the app was granted all the permissions. Otherwise, returns false.  */
    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }


    private fun loadCompetence(): Int?{
        val tmp = getCompetenceValue()
        if(tmp < 0){
            return null
        }
        return tmp
    }

    private fun getCompetenceValue(): Int {
        val sp = getSharedPreferences(MainActivity.CONFIG_FILE, 0)
        return sp.getInt(Constants.CONFIG_COMPETENCY, -1)
    }

}
