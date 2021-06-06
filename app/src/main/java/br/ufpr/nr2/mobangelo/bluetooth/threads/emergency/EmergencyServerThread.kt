package br.ufpr.nr2.mobangelo.bluetooth.threads.emergency

import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import br.ufpr.nr2.mobangelo.bluetooth.Constants
import br.ufpr.nr2.mobangelo.bluetooth.EmergencyManager
import br.ufpr.nr2.mobangelo.bluetooth.CommunicationManager
import java.io.IOException
import java.util.*

class EmergencyServerThread(private val service: CommunicationManager, private val uuid: UUID) : Thread() {
    private var mmServerSocket: BluetoothServerSocket? = null

    init {
        Log.d(
            Constants.TAG,
            "[EmergencyThread] Initializing EmergencyThread"
        )
        try{
            mmServerSocket  = service.mAdapter!!.listenUsingInsecureRfcommWithServiceRecord(
                Constants.NAME_INSECURE, uuid)
        }catch (e: IOException){
            Log.e(
                Constants.TAG,
                "[EmergencyThread] listen() failed",
                e
            );
        }
    }

    override fun run() {
//            set thread name
        name = "EmergencyThread"
        Log.i(
            Constants.TAG,
            "[EmergencyThread] Running mEmergencyThread: $this"
        )
        var socket: BluetoothSocket?

        try {
            while (true){
                socket = try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    mmServerSocket!!.accept()
                } catch (e: IOException) {
                    Log.e(
                        Constants.TAG,
                        "[EmergencyThread] accept() failed but it might be because of method close()",
                        e
                    )
                    break
                }

                if (socket != null){
                    synchronized(service){
                        when (service.getState()){
                            // TODO: verify if when state is stopped it should still receive emergency messages
                            EmergencyManager.STATE_STOPPED -> {
                                Log.d(
                                    Constants.TAG,
                                    "[EmergencyThread] Rejecting connection request: status is stopped..."
                                )
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                    Log.e(
                                        Constants.TAG,
                                        "[EmergencyThread] Could not close unwanted socket",
                                        e
                                    )
                                }
                            }
                            EmergencyManager.STATE_ERROR -> {
                                Log.d(
                                    Constants.TAG,
                                    "[EmergencyThread] Received connection request when status is error: rejecting..."
                                )
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                    Log.e(
                                        Constants.TAG,
                                        "[EmergencyThread] Could not close unwanted socket",
                                        e
                                    )
                                }
                            }
                            else -> {
                                Log.d(
                                    Constants.TAG,
                                    "[EmergencyThread] Accepting connection..."
                                )
                                service.handleEmergencyReceived(socket)
                            }
                        }
                    }
                }

            }
        } catch (e: IOException){
            Log.e(
                Constants.TAG,
                "[EmergencyThread] accept() failed",
                e
            )
        }

        Log.i(
            Constants.TAG,
            "[EmergencyThread] Finishing mAcceptThread"
        )
    }

    // Closes the connect socket and causes the thread to finish.
    fun cancel() {
        try {
            Log.d(
                Constants.TAG,
                "[EmergencyThread] Closing server socket..."
            )
            mmServerSocket?.close()
        } catch (e: IOException) {
            Log.e(
                Constants.TAG,
                "[EmergencyThread] Could not close the server connection socket",
                e
            )
        }
    }
}