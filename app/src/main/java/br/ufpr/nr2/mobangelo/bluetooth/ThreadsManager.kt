package br.ufpr.nr2.mobangelo.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import br.ufpr.nr2.mobangelo.bluetooth.threads.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

// from: https://github.com/googlearchive/android-BluetoothChat/tree/d6e5db25764fe94f294eb1b6e9ea521dc2f02c71
// https://github.com/android/connectivity-samples/tree/master/BluetoothChat
// Connects to 1 remote device at a time and returns the connection
// OUTDATED: updated to multi connections based on https://github.com/polyclef/BluetoothChatMulti/
// Using only one UUID: https://stackoverflow.com/questions/50981915/is-it-possible-to-connect-multiple-bluetooth-client-devices-to-same-uuid-servic
class ThreadsManager(var activityHandler: Handler,
                     var btAdapter: BluetoothAdapter,
                     val uuid: UUID,
                     val communicationManager: CommunicationManager) {

    var mState: AtomicInteger = AtomicInteger(STATE_INITIALIZING)

    var mConnectThread: ConnectThread? = null

    private var mConnectedThread: ConnectedThread? = null
    private var mServerThread: ServerThread? = null
    private val mLock : ReentrantLock = ReentrantLock()
    private var pendingDevices: MutableList<BluetoothDevice>
    private var serverResponseList: MutableList<ServerResponseThread> = mutableListOf()

    init {
        pendingDevices = mutableListOf()
    }

    fun getEncodedPayload() : String{
        Log.d(Constants.TAG, "[ThreadsManager] Handshake content: ${communicationManager.encodeHandshake()}")
        return communicationManager.encodeHandshake()
    }

    companion object{
        // Constants that indicate the current connection state
        const val STATE_INITIALIZING = 0 // we're doing nothing
        const val STATE_LISTENING    = 1 // now listening for incoming connections
        const val STATE_CONNECTING   = 2 // now initiating an outgoing connection
        const val STATE_CONNECTED    = 3 // now connected to a remote device
        const val STATE_STOPPED      = 4 // service stopped
        const val STATE_ERROR        = 5 // State that represents unrecoverable error
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

            mServerThread =
                ServerThread(this)
            mServerThread!!.start()

            mState.set(STATE_LISTENING)
//        TODO: Send handler message
            Log.d(Constants.TAG, "[Start method] Started accept thread, server is now listening...")
        }finally {
          mLock.unlock()
        }
    }

    fun reset(){
        mLock.lock()
        try{
            Log.d(Constants.TAG, "[Reset method] start")

            // Cancel any thread attempting to make a connection
            if (mConnectThread != null) {
                Log.e(Constants.TAG, "[Reset method] mConnectThread is not null")
                mConnectThread!!.cancel()
                mConnectThread = null
            }

            // Cancel any thread currently running a connection
            if (mConnectedThread != null) {
                Log.e(Constants.TAG, "[Reset method] mConnectedThread is not null")
                mConnectedThread!!.cancel()
                mConnectedThread = null
            }

            mServerThread = ServerThread(this)
            mServerThread!!.start()


            mState.set(STATE_LISTENING)
            Log.d(Constants.TAG, "[Reset method] Connections were stopped and server is running...")
        }finally {
            mLock.unlock()
        }
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param devices The list of BluetoothDevices to connect
     */
    @Synchronized
    fun connect(devices: List<BluetoothDevice>) {
        mLock.lock()
        try {
            // Cancel any thread attempting to make a connection
            if (mState.get() == STATE_CONNECTING) {
                Log.e(Constants.TAG, "[Connect] Trying to connect when there is already a connect thread running")
                return
            }

            if(mState.get() == STATE_LISTENING){
                Log.i(Constants.TAG, "[Connect] Changing state from listening to connecting")
            }

            // Cancel any thread currently running a connection
            if (mConnectedThread != null) {
                Log.e(Constants.TAG, "[Connect] Calling connect when device is already connected!")
                mConnectedThread!!.cancel()
                mConnectedThread = null
            }
            mState.set(STATE_CONNECTING)
            pendingDevices = devices.toMutableList()
            connectPendingDevices()
            Log.d(Constants.TAG, "[Connect method] Executed connectionThread for ${devices.size} devices")
        }finally {
            mLock.unlock()
        }
    }

    fun connectPendingDevices() {
        mLock.lock()
        try {
            if (pendingDevices.size > 0){
                val device = pendingDevices.first()
                Log.d(Constants.TAG, "[connectPendingDevices] Starting connect thread to device ${device.address}")
                mConnectThread =
                    ConnectThread(
                        device,
                        uuid,
                        this
                    )
                mConnectThread!!.start()
                Log.d(Constants.TAG, "[connectPendingDevices] Removing device ${device.address} from list of pending devices")
                pendingDevices.remove(device)
                val msg: Message =
                    activityHandler.obtainMessage(Constants.MESSAGE_DEVICE_FOUND)
                val bundle = Bundle()
                bundle.putInt(Constants.PENDING_DEVICES, pendingDevices.size)
                msg.data = bundle
                activityHandler.sendMessage(msg)
            } else {
                val msg: Message =
                    activityHandler.obtainMessage(Constants.MESSAGE_COMMUNITY_COMPLETE)
                activityHandler.sendMessage(msg)
                Log.d(Constants.TAG, "[connectPendingDevices] Pending devices list is empty")
            }
        } finally {
            mLock.unlock()
        }
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
//    TODO: maybe even synchronized is not needed (to be verified)
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
            ConnectedThread(
                socket!!,
                previousState,
                this
            )
        connectedThread.start()
        mConnectedThread = connectedThread

//        TODO: refactor handler message
        // Send the name of the connected device back to the UI Activity
        val msg: Message =
            activityHandler.obtainMessage(Constants.MESSAGE_NAME)
        val bundle = Bundle()
        bundle.putString(Constants.DEVICE_NAME, "${device.address}:${device.name}")
        msg.data = bundle

        activityHandler.sendMessage(msg)
        mState.set(STATE_CONNECTED)
        Log.d(Constants.TAG, "[Connected method] Connected with device - ${device.name}")

    }

    @Synchronized
    fun handshake(
        socket: BluetoothSocket?,
        device: BluetoothDevice
    ){
        Log.d(Constants.TAG, "[handshake method] Will send handshake to device - ${device.name}:${device.address}")
        // Start the thread to manage the connection and perform transmissions
        val responseThread =
            ServerResponseThread(
                socket!!,
                this
            )
        responseThread.start()

        serverResponseList.add(responseThread)
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
            if(serverResponseList.any { it.isAlive }){
                Log.d(Constants.TAG, "[Stop method] ERROR, not all response threads stopped")
            }else{
                Log.d(Constants.TAG, "[Stop method] All response threads stopped")
            }

            mState.set(STATE_STOPPED)
//        TODO: send handler message
            Log.d(Constants.TAG, "[Stop method] All communication stopped!")
        } finally {
            mLock.unlock()
        }
    }

    //    @Synchronized
    fun connectionFailed(device: BluetoothDevice) {
        mLock.lock()
        try {
            if (mConnectThread != null){
                mConnectThread!!.cancel()
                mConnectThread = null
            }
            val msg =
                activityHandler.obtainMessage(Constants.MESSAGE_CONNECTION_FAILED)
            val bundle = Bundle()
            bundle.putString(Constants.DEVICE_NAME, "Failed to connect to device ${device.name}")
            msg.data = bundle
            activityHandler.sendMessage(msg)

//            TODO: fix here?
            mState.set(STATE_LISTENING)
            Log.d(Constants.TAG, "[Connection Failed] Connection failed with device ${device.name}")
        } finally {
            mLock.unlock()
        }
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
//    @Synchronized
    fun connectionLost(device: BluetoothDevice) {
        mLock.lock()
        try {
            if (mConnectedThread != null){
                mConnectedThread!!.cancel()
                mConnectedThread = null
            }
            // Send signal to remove neighbour back to the Activity
            val msg =
                activityHandler.obtainMessage(Constants.MESSAGE_CONNECTION_LOST)
            val bundle = Bundle()
            bundle.putString(Constants.DEVICE_ADDRESS, device.address)
            msg.data = bundle
            activityHandler.sendMessage(msg)

//            // Send message back to the Activity
//            msg = activityHandler.obtainMessage(Constants.MESSAGE_TOAST)
//            bundle = Bundle()
//            bundle.putString(
//                Constants.TOAST,
//                "Device ${device.address} connection was lost"
//            )
//            msg.data = bundle
//            activityHandler.sendMessage(msg)

            mState.set(STATE_LISTENING)
            Log.d(Constants.TAG, "[Connection Lost] Connection lost: device ${device.name}")
        } finally {
            mLock.unlock()
        }
    }
}