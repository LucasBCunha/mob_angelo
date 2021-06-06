package br.ufpr.nr2.mobangelo.bluetooth.threads.emergency

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import br.ufpr.nr2.mobangelo.bluetooth.Constants
import br.ufpr.nr2.mobangelo.bluetooth.EmergencyManager
import java.io.IOException
import java.util.*

class EmergencyConnectThread(private val mmDevice: BluetoothDevice,
                             uuidToTry: UUID,
                             private val service: EmergencyManager) : Thread() {

    private var mmSocket: BluetoothSocket?
    private val tempUuid: UUID?

    init {
        var tmp: BluetoothSocket? = null
        tempUuid = uuidToTry

        // Get a BluetoothSocket for a connection with the
        // given BluetoothDevice
        try {
            tmp = mmDevice.createInsecureRfcommSocketToServiceRecord(uuidToTry)
        } catch (e: IOException) {
            Log.e(
                Constants.TAG,
                "[EmergencyConnectThread] create() failed",
                e
            )
        }
        mmSocket = tmp
        Log.d(
            Constants.TAG,
            "[EmergencyConnectThread] Status: ${service.mState}"
        )
    }

    override fun run() {
//            Set thread name
        name = "ConnectThreadEmergency"
        Log.i(
            Constants.TAG,
            "[EmergencyConnectThread] BEGIN mConnectThread $this"
        )
        // Make a connection to the BluetoothSocket
        try {
            mmSocket!!.connect()
        } catch (e: IOException) {
            // Close the socket
            try {
                mmSocket!!.close()
            } catch (e2: IOException) {
                Log.e(
                    Constants.TAG,
                    "[EmergencyConnectThread] unable to close() socket during connection failure",
                    e2
                )
            }
            service.neighbourTimeout(mmDevice)
            return
        } catch (e5: Exception){
            Log.e(
                Constants.TAG,
                "[EmergencyConnectThread] Unexpected error:",
                e5
            )
            service.mState.set(EmergencyManager.STATE_ERROR)
            throw e5
        }

        Log.d(Constants.TAG, "[EmergencyConnectThread] Connection success!")
        // Reset the ConnectThread because we're done
        synchronized(service) { service.mConnectThread = null }

        // Start the connected thread
        service.connected(mmSocket, mmDevice,
            EmergencyManager.STATE_CLIENT_CONNECTING
        )
        Log.d(Constants.TAG, "[EmergencyConnectThread] End of thread")
    }

    fun cancel() {
        try {
            Log.d(
                Constants.TAG,
                "[EmergencyConnectThread] Closing socket..."
            )
            mmSocket!!.close()
        } catch (e: IOException) {
            Log.e(
                Constants.TAG,
                "[EmergencyConnectThread] close() of socket failed",
                e
            )
        }
    }
}