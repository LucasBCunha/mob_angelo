package br.ufpr.nr2.mobangelo.bluetooth.threads

import android.bluetooth.BluetoothSocket
import android.util.Log
import br.ufpr.nr2.mobangelo.bluetooth.Constants
import br.ufpr.nr2.mobangelo.bluetooth.ThreadsManager
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * This thread runs during a connection with a remote device.
 * It handles all incoming and outgoing transmissions.
 */
class ConnectedThread(socket: BluetoothSocket, private val previousState: Int,
                              private val service: ThreadsManager
) :
    Thread() {
    private var mmSocket: BluetoothSocket?
    private var mmInStream: InputStream?
    private var mmOutStream: OutputStream?

    init {
        Log.d(
            Constants.TAG,
            "[Connected Thread] create ConnectedThread: Insecure"
        )
        mmSocket = socket
        var tmpIn: InputStream? = null
        var tmpOut: OutputStream? = null

        // Get the BluetoothSocket input and output streams
        try {
            tmpIn = socket.inputStream
            tmpOut = socket.outputStream
        } catch (e: IOException) {
            Log.e(
                Constants.TAG,
                "[Connected Thread] temp sockets not created",
                e
            )
        }
        mmInStream = tmpIn
        mmOutStream = tmpOut
        Log.d(
            Constants.TAG,
            "[Connected Thread] Status: ${service.mState}"
        )
        service.mState.set(ThreadsManager.STATE_CONNECTED)
    }

    override fun run() {
        Log.i(
            Constants.TAG,
            "[Connected Thread] BEGIN mConnectedThread"
        )
        val buffer = ByteArray(1024)
        val bytes: Int

//            Read handshake message from input stream
        try {
            // Read from the InputStream
            bytes = mmInStream!!.read(buffer)
            val msg = String(buffer.copyOfRange(0, bytes), Charsets.UTF_8)
            service.communicationManager.addNeighbour(mmSocket!!.remoteDevice, msg)
            // Send the obtained bytes to the UI Activity

            service.activityHandler.obtainMessage(
                Constants.MESSAGE_READ,
                bytes,
                -1,
                buffer
            )
                .sendToTarget()
        } catch (e: IOException) {
            Log.e(
                Constants.TAG,
                "[Connected Thread] disconnected",
                e
            )
            service.connectionLost(mmSocket!!.remoteDevice)

        }
        when(previousState){
            ThreadsManager.STATE_LISTENING -> {
                start()
            }
            ThreadsManager.STATE_CONNECTING -> {
//                    Create local list of devices (create???)
                service.connectPendingDevices()
            }
        }
        Log.i(
            Constants.TAG,
            "[Connected Thread] End mConnectedThread"
        )
        cancel()
    }

    fun cancel() {
        try {
            mmOutStream!!.close()
            mmInStream!!.close()
            mmSocket!!.close()
            when(previousState){
                ThreadsManager.STATE_LISTENING -> {
                    start()
                }
                ThreadsManager.STATE_CONNECTING -> {
//                    Create local list of devices
                    if(service.mState.get() == ThreadsManager.STATE_CONNECTING){
                        service.connectPendingDevices()
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(
                Constants.TAG,
                "[Connected Thread] close() of connect socket failed",
                e
            )
        }
    }
}