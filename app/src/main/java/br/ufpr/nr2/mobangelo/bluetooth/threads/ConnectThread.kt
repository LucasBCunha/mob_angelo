package br.ufpr.nr2.mobangelo.bluetooth.threads

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import br.ufpr.nr2.mobangelo.bluetooth.ThreadsManager
import br.ufpr.nr2.mobangelo.bluetooth.Constants
import java.io.IOException
import java.util.*

/**
 * This thread runs while attempting to make an outgoing connection
 * with a device. It runs straight through; the connection either
 * succeeds or fails.
 */
class ConnectThread(private val mmDevice: BluetoothDevice, uuidToTry: UUID,
                            private val service: ThreadsManager
) :
    Thread() {
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
                "[Connect Thread] create() failed",
                e
            )
        }
        mmSocket = tmp
        Log.d(
            Constants.TAG,
            "[Connect Thread] Status: ${service.mState}"
        )
    }

    override fun run() {
//            Set thread name
        name = "ConnectThreadInsecure"
        Log.i(
            Constants.TAG,
            "[Connect Thread] BEGIN mConnectThread $this"
        )

        // Always cancel discovery because it will slow down a connection
        if(service.btAdapter.isDiscovering){
            Log.e(
                Constants.TAG,
                "[Connect Thread] Cancelling discovery..."
            )
//                Since discovery should never be running at this point in time, this is just a sanity check
            service.btAdapter.cancelDiscovery()
        }

        // Make a connection to the BluetoothSocket
        try {
            // This is a blocking call and will only return on a
            // successful connection or an exception
            mmSocket!!.connect()
        } catch (e: IOException) {
            // Close the socket
            try {
                mmSocket!!.close()
            } catch (e2: IOException) {
                Log.e(
                    Constants.TAG,
                    "[Connect Thread] unable to close() socket during connection failure",
                    e2
                )
            }
            service.connectionFailed(mmDevice)
            return
        } catch (e5: Exception){
            Log.e(
                Constants.TAG,
                "[Connect Thread] Unexpected error:",
                e5
            )
            service.mState.set(ThreadsManager.STATE_ERROR)
            throw e5
        }

        Log.d(Constants.TAG, "[Connect Thread] Connection success!")
        // Reset the ConnectThread because we're done
        synchronized(service) { service.mConnectThread = null }

        // Start the connected thread
        service.connected(mmSocket, mmDevice,
            ThreadsManager.STATE_CONNECTING
        )
        Log.d(Constants.TAG, "[Connect Thread] End of thread")
    }

    fun cancel() {
        try {
            Log.d(
                Constants.TAG,
                "[Connect Thread] Closing socket..."
            )
            mmSocket!!.close()
            if(service.mState.get() == ThreadsManager.STATE_CONNECTING){
                service.connectPendingDevices()
            }
        } catch (e: IOException) {
            Log.e(
                Constants.TAG,
                "[Connect Thread] close() of socket failed",
                e
            )
        }
    }

}