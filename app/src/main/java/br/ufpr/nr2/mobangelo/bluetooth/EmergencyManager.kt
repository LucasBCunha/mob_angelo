package br.ufpr.nr2.mobangelo.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import br.ufpr.nr2.mobangelo.MainActivity
import br.ufpr.nr2.mobangelo.bluetooth.threads.emergency.EmergencyConnectThread
import br.ufpr.nr2.mobangelo.bluetooth.threads.emergency.EmergencyConnectedThread
import br.ufpr.nr2.mobangelo.bluetooth.threads.emergency.EmergencyServerThread
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock


class EmergencyManager(var activityHandler: Handler,
                       var btAdapter: BluetoothAdapter,
                       private val uuid: UUID,
                       private val communicationManager: CommunicationManager) {


    private val mLock : ReentrantLock = ReentrantLock()
    var mState: AtomicInteger = AtomicInteger(ThreadsManager.STATE_INITIALIZING)

    var mConnectThread: EmergencyConnectThread? = null

    private var mConnectedThread: EmergencyConnectedThread? = null
    private var mServerThread: EmergencyServerThread? = null
    var mEmergencyMessage: String = ""



    fun handleEmergencyReceived(socket: BluetoothSocket){
        val device = socket.remoteDevice
        Log.d(Constants.TAG, "[EmergencyManager] Emergency connection from  ${device.name}:${device.address}")
        // Start the thread to manage the connection and perform transmissions
        val readEmergencyThread =
            EmergencyConnectedThread(
                socket,
                this
            )
        readEmergencyThread.start()
    }

    private fun updateEmergencyMessage(){
        val sp = communicationManager.context.getSharedPreferences(MainActivity.CONFIG_FILE, 0)
        mEmergencyMessage = sp.getString(Constants.CONFIG_MESSAGE, "")!!
    }

    fun handleEmergency(){
        val bestNeighbour = communicationManager.getBestNeighbour()
        if(bestNeighbour == null){
            val msg =
                activityHandler.obtainMessage(Constants.MESSAGE_NO_NEIGHBOUR)
            communicationManager.mHandler.sendMessage(msg)
            Log.d(Constants.TAG, "NO NEIGHBOUR AVAILABLE!! FAILED TO GET HELP")
            return
        }
        updateEmergencyMessage()
        mState.set(STATE_CLIENT_CONNECTING)
        // Always cancel discovery because it will slow down a connection
        if(communicationManager.mAdapter!!.isDiscovering){
            Log.e(
                Constants.TAG,
                "[EmergencyManager] Cancelling discovery..."
            )
            communicationManager.mAdapter!!.cancelDiscovery()
        }
        Log.d(Constants.TAG, "[EmergencyManager] Starting Connect Thread")
        mConnectThread = EmergencyConnectThread(bestNeighbour.device, uuid, this)
        mConnectThread!!.start()
    }

    fun neighbourTimeout(bluetoothDevice: BluetoothDevice){
        communicationManager.neighbourTimeout(bluetoothDevice)
        handleEmergency()
    }

    fun emergencyConnectionLost(bluetoothDevice: BluetoothDevice){
//        TODO: reset normal work of the system(?)
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode.
     */
    @Synchronized
    fun start() {
        mLock.lock()
        try{
            Log.d(Constants.TAG, "[Start method] start")

            // Cancel any thread attempting to make a connection
            if (mConnectThread != null) {
                Log.e(Constants.TAG, "[Start method] mConnectThread is not null")
                mConnectThread!!.cancel()
                mConnectThread = null
            }

            // Cancel any thread currently running a connection
            if (mConnectedThread != null) {
                Log.e(Constants.TAG, "[Start method] mConnectedThread is not null")
                mConnectedThread!!.cancel()
                mConnectedThread = null
            }

            if(mServerThread != null){
                Log.e(Constants.TAG, "[Start method] mInsecureConnectThread is not null")
                mServerThread!!.cancel()
            }

//            mServerThread =
//                EmergencyServerThread(stealthManager, uuid)
//            mServerThread!!.start()

//        TODO: Send handler message
            Log.d(Constants.TAG, "[Start method] Started accept thread, server is now listening...")
        }finally {
            mLock.unlock()
        }
    }

    @Synchronized
    fun connected(
        socket: BluetoothSocket?,
        device: BluetoothDevice,
        previousState: Int
    ) {
        Log.d(Constants.TAG, "connected to ${device.name}, starting thread")

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            Log.d(Constants.TAG, "[Connected method] Connected Thread is not null!")
//            return
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        // Start the thread to manage the connection and perform transmissions
        val connectedThread =
            EmergencyConnectedThread(
                socket!!,
                this
            )
        if (previousState == STATE_CLIENT_CONNECTING){
            mState.set(STATE_CLIENT_CONNECTED)
        } else if(previousState == STATE_SERVER_LISTENING){
            mState.set(STATE_SERVER_CONNECTED)
        }
        connectedThread.start()
        mConnectedThread = connectedThread

//        TODO: refactor handler message
        // Send the name of the connected device back to the UI Activity
        val msg: Message =
            activityHandler.obtainMessage(Constants.MESSAGE_NAME)
        val bundle = Bundle()
        bundle.putString(Constants.DEVICE_NAME, "${device.address}:${device.name} - EMERGENCY")
        msg.data = bundle

        activityHandler.sendMessage(msg)
        Log.d(Constants.TAG, "[EmergencyManager] Connected() with device - ${device.name}")
    }

    /**
     * Stop all threads
     */
    @Synchronized
    fun stop() {
        mLock.lock()
        try {
            Log.d(Constants.TAG, "stop bluetooth service")

            if (mConnectThread != null) {
                mConnectThread!!.cancel()
                mConnectThread = null
            }

            if (mConnectedThread != null) {
                mConnectedThread!!.cancel()
                mConnectedThread = null
            }

            if (mServerThread != null) {
                mServerThread!!.cancel()
                mServerThread = null
            }
            mState.set(STATE_STOPPED)
//        TODO: send handler message
            Log.d(Constants.TAG, "[Stop method] All communication stopped!")
        } finally {
            mLock.unlock()
        }
    }

    companion object{
        // Constants that indicate the current connection state
        const val STATE_STOPPED           = 4 // service stopped
        const val STATE_ERROR        = 5 // State that represents unrecoverable error

        const val STATE_SERVER_LISTENING = 1 // now listening for incoming connections
        const val STATE_SERVER_CONNECTED = 2 // now connected to a remote device
        const val STATE_SERVER_BLOCKED   = 3 // server can't accept connection because it's helping someone else

        const val STATE_CLIENT_CONNECTING = 10 // now initiating an outgoing connection
        const val STATE_CLIENT_CONNECTED  = 11
        const val STATE_CLIENT_HELPED     = 12
    }
}