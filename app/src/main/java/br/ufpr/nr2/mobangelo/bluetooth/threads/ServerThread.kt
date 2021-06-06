package br.ufpr.nr2.mobangelo.bluetooth.threads

import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import br.ufpr.nr2.mobangelo.bluetooth.ThreadsManager
import br.ufpr.nr2.mobangelo.bluetooth.Constants
import java.io.IOException

class ServerThread(private val service: ThreadsManager) : Thread() {
    private var mmServerSocket: BluetoothServerSocket? = null

    init {
        Log.d(
            Constants.TAG,
            "[Accept Thread] Initializing accept thread - state ${service.mState}"
        )
        try{
            mmServerSocket  = service.btAdapter.listenUsingInsecureRfcommWithServiceRecord(
                Constants.NAME_INSECURE, service.uuid)
        }catch (e: IOException){
            Log.e(
                Constants.TAG,
                "[Accept Thread] listen() failed",
                e
            );
        }
    }

    override fun run() {
//            set thread name
        name = "AcceptThread"
        Log.i(
            Constants.TAG,
            "[Accept Thread] Running mAcceptThread $this"
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
                        "[Accept Thread] accept() failed but it might be because of method close()",
                        e
                    )
                    break
                }

                if (socket != null){
                    synchronized(service){
                        when (service.mState.get()){
                            ThreadsManager.STATE_CONNECTED, ThreadsManager.STATE_LISTENING, ThreadsManager.STATE_CONNECTING -> {
                                Log.d(
                                    Constants.TAG,
                                    "[Accept Thread] Accepting connection..."
                                )
                                service.handshake(socket, socket.remoteDevice)
                            }
                            ThreadsManager.STATE_STOPPED -> {
                                Log.d(
                                    Constants.TAG,
                                    "[Accept Thread] Rejecting connection request: status is stopped..."
                                )
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                    Log.e(
                                        Constants.TAG,
                                        "[Accept Thread] Could not close unwanted socket",
                                        e
                                    )
                                }
                            }
                            ThreadsManager.STATE_ERROR -> {
                                Log.d(
                                    Constants.TAG,
                                    "[Accept Thread] Received connection request when status is error: rejecting..."
                                )
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                    Log.e(
                                        Constants.TAG,
                                        "[Accept Thread] Could not close unwanted socket",
                                        e
                                    )
                                }
                            }
                            else -> {
                                Log.d(
                                    Constants.TAG,
                                    "[Accept Thread] Received connection request when status is unknown: rejecting..."
                                )
                                service.mState.set(
                                    ThreadsManager.STATE_ERROR)
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                    Log.e(
                                        Constants.TAG,
                                        "[Accept Thread] Could not close unwanted socket",
                                        e
                                    )
                                }
                            }
                        }
                    }
                }

            }
        } catch (e: IOException){
            Log.e(
                Constants.TAG,
                "[Accept Thread] accept() failed",
                e
            )
        }

        Log.i(
            Constants.TAG,
            "[Accept Thread] Finishing mAcceptThread"
        )
    }

    // Closes the connect socket and causes the thread to finish.
    fun cancel() {
        try {
            Log.d(
                Constants.TAG,
                "[Accept Thread] Closing server socket..."
            )
            mmServerSocket?.close()
        } catch (e: IOException) {
            Log.e(
                Constants.TAG,
                "[Accept Thread] Could not close the server connection socket",
                e
            )
        }
    }
}