package br.ufpr.nr2.mobangelo.bluetooth.threads.emergency

import android.bluetooth.BluetoothSocket
import android.util.Log
import br.ufpr.nr2.mobangelo.bluetooth.Constants
import br.ufpr.nr2.mobangelo.bluetooth.EmergencyManager
import br.ufpr.nr2.mobangelo.bluetooth.CommunicationManager.Companion.ACK_SIGNAL
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class EmergencyConnectedThread(socket: BluetoothSocket,
                               private val service: EmergencyManager
) :
    Thread() {
    private var mmSocket: BluetoothSocket?
    private var mmInStream: InputStream?
    private var mmOutStream: OutputStream?
    private var mState: Int = 0

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
        mState = EmergencyManager.STATE_CLIENT_CONNECTED
    }

    override fun run() {
        Log.i(
            Constants.TAG,
            "[Connected Thread] BEGIN mConnectedThread"
        )
        when (mState) {
            EmergencyManager.STATE_CLIENT_CONNECTED -> {
                clientExecution()
            }
            else -> {
                throw IllegalArgumentException("Unknown status")
            }
        }
    }

    fun cancel() {
        try {
            mmOutStream!!.close()
            mmInStream!!.close()
            mmSocket!!.close()
        } catch (e: IOException) {
            Log.e(
                Constants.TAG,
                "[Connected Thread] close() of connect socket failed",
                e
            )
        }
    }

    private fun clientExecution(){
        val buffer = ByteArray(1024)
        val bytes: Int

        val buffer1 = ByteArray(1024)

        try {
            // Write emergency info to buffer
            val payload = service.mEmergencyMessage + "\\0"
            write(payload.toByteArray(Charsets.UTF_8))

//            TODO: add timeout verification here
//            First message is handshake
            mmInStream!!.read(buffer1)
//            Second message is ACK
            bytes = mmInStream!!.read(buffer)
            val msg = String(buffer.copyOfRange(0, bytes), Charsets.UTF_8)
            if(msg == ACK_SIGNAL){
                val deviceInfo = mmSocket?.remoteDevice?.name
                // Send the obtained bytes to the UI Activity
                service.activityHandler.obtainMessage(
                    Constants.MESSAGE_EMERGENCY_SENT,
                    -1,
                    -1,
                    deviceInfo
                )
                    .sendToTarget()
                Log.d(Constants.TAG, "[ConnectedThread] response from ${mmSocket!!.remoteDevice.name}:${mmSocket!!.remoteDevice.address} is $msg")
            } else{
                Log.d(Constants.TAG, "[ConnectedThread] response from ${mmSocket!!.remoteDevice.name}:${mmSocket!!.remoteDevice.address} is not ack: $msg")
            }
            cancel()
        } catch (e: IOException) {
            Log.e(
                Constants.TAG,
                "[Connected Thread] disconnected",
                e
            )
            cancel()
            service.neighbourTimeout(mmSocket!!.remoteDevice)
        }
        Log.i(
            Constants.TAG,
            "[Connected Thread] End mConnectedThread"
        )
    }

    /**
     * Write to the connected OutStream.
     *
     * @param buffer The bytes to write
     */
    private fun write(buffer: ByteArray?) {
        try {
            if (buffer != null) {
                mmOutStream!!.write(buffer)
                // Share the sent message back to the UI Activity
                service.activityHandler.obtainMessage(
                    Constants.MESSAGE_WRITE,
                    -1,
                    -1,
                    buffer
                ).sendToTarget()
            }
        } catch (e: IOException) {
            Log.e(Constants.TAG, "[Connected Thread] Exception during write", e)
        }
    }
}