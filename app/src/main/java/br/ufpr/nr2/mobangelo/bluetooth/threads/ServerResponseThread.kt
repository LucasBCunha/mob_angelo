package br.ufpr.nr2.mobangelo.bluetooth.threads

import android.bluetooth.BluetoothSocket
import android.util.Log
import br.ufpr.nr2.mobangelo.bluetooth.Constants
import br.ufpr.nr2.mobangelo.bluetooth.CommunicationManager.Companion.ACK_SIGNAL
import br.ufpr.nr2.mobangelo.bluetooth.ThreadsManager
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * This thread runs during a connection with a remote device.
 * It handles all incoming and outgoing transmissions.
 */
class ServerResponseThread(
    socket: BluetoothSocket,
    private val service: ThreadsManager
) :
    Thread() {
    private var mmSocket: BluetoothSocket?
    private var mmOutStream: OutputStream?
    private var mmInStream: InputStream?

    init {
        Log.d(
            Constants.TAG,
            "[ServerResponseThread] create ServerResponseThread: Insecure"
        )
        mmSocket = socket

        var tmpIn: InputStream? = null
        var tmpOut: OutputStream? = null

        // Get the BluetoothSocket and output stream
        try {
            tmpIn = socket.inputStream
            tmpOut = socket.outputStream
        } catch (e: IOException) {
            Log.e(
                Constants.TAG,
                "[ServerResponseThread] temp sockets not created",
                e
            )
        }

        mmInStream = tmpIn
        mmOutStream = tmpOut

        Log.d(
            Constants.TAG,
            "[Connected Thread] Status: ${service.mState}"
        )
    }

    override fun run() {
        val buffer = ByteArray(1024)
        Log.i(
            Constants.TAG,
            "[ServerResponseThread] BEGIN ServerResponseThread"
        )
        val msg = "${service.getEncodedPayload()}\\0"
        write(msg.toByteArray(Charsets.UTF_8), Constants.MESSAGE_HANDSHAKE)

        try{
//            Read ACK message if present
            mmInStream!!.read(buffer)
            handleEmergency(buffer)
        } catch(e: IOException){
            Log.i(Constants.TAG, "[ServerResponseThread] Connection was of normal type")
        }
        cancel()
        Log.i(Constants.TAG, "[ServerResponseThread] End ServerResponseThread")
    }

    private fun handleEmergency(buffer: ByteArray){
        try {
            val msg = "Recebido de: ${mmSocket?.remoteDevice?.name}\n" + String(buffer, Charsets.UTF_8)
            // Send the obtained bytes to the UI Activity
            service.activityHandler.obtainMessage(
                Constants.MESSAGE_EMERGENCY_READ,
                msg.toByteArray().size,
                -1,
                msg.toByteArray()
            )
                .sendToTarget()
            write(ACK_SIGNAL.toByteArray(Charsets.UTF_8), Constants.MESSAGE_WRITE)
        } catch (e: IOException) {
            Log.e(
                Constants.TAG,
                "[Connected Thread] disconnected",
                e
            )
//            service.emergencyConnectionLost(mmSocket!!.remoteDevice)
        }
        Log.i(
            Constants.TAG,
            "[Connected Thread] End mConnectedThread"
        )
//        TODO: Could improve here - busy wait is not the best idea
        while(mmSocket!!.isConnected){
            sleep(15)
        }
    }

    /**
     * Write to the connected OutStream.
     *
     * @param buffer The bytes to write
     */
    private fun write(buffer: ByteArray?, messageType: Int) {
        try {
            if(buffer != null){
                mmOutStream!!.write(buffer)
                // Share the sent message back to the UI Activity

                service.activityHandler.obtainMessage(
                    messageType,
                    -1,
                    -1,
                    buffer
                ).sendToTarget()
            }
        } catch (e: IOException) {
            Log.e(
                Constants.TAG,
                "[ServerResponseThread] Exception during write",
                e
            )
        }
    }

    private fun cancel() {
        try {
            mmOutStream!!.close()
            mmInStream!!.close()
            mmSocket!!.close()
        } catch (e: IOException) {
            Log.e(
                Constants.TAG,
                "[ServerResponseThread] close() of connect socket failed",
                e
            )
        }
    }
}