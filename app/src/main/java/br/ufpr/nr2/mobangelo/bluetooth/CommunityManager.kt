package br.ufpr.nr2.mobangelo.bluetooth

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.os.Message
import android.util.Log

class CommunityManager(mInterests: MutableList<Int>, private val discoveryService: ThreadsManager) {

    private var mInterests: MutableList<Int> = mInterests
    private var currentNeighbours: MutableList<Neighbour> = mutableListOf()
    private var discoveredDevices: MutableList<BluetoothDevice> = mutableListOf()

    init {
    }

    fun start(){
        Log.d(Constants.TAG, "Start CommunityManager")
        discoveryService.start()
    }

    fun stop(){
        currentNeighbours = mutableListOf()
        discoveredDevices = mutableListOf()
        discoveryService.stop()
    }

    fun discoveryStarted(){
        //TODO: is it really needed? Should it reset the lists on start operation or on discovery started?
    }

    fun resetCommunity(){
        discoveryService.reset()
        currentNeighbours = mutableListOf()
        discoveredDevices = mutableListOf()
    }

    fun deviceFound(device: BluetoothDevice){
        // Discovery has found a device. Get the BluetoothDevice
        // object and its info from the Intent.
        val deviceHardwareAddress = device.address // MAC address
        val deviceName = if (device.name == null){
            deviceHardwareAddress
        }else{
            device.name
        }
        Log.d(Constants.TAG, "[CommunityManager] Device found: $deviceName - type: ${device.type}")
        if(BluetoothDevice.DEVICE_TYPE_CLASSIC == device.type){
            discoveredDevices.add(device)
            val msg: Message =
                discoveryService.activityHandler.obtainMessage(Constants.MESSAGE_DEVICE_FOUND)
            val bundle = Bundle()
            bundle.putInt(Constants.PENDING_DEVICES, discoveredDevices.size)
            msg.data = bundle
            discoveryService.activityHandler.sendMessage(msg)
            Log.d(Constants.TAG, "[CommunityManager] Device $deviceName found and stored to connect later")
        }
    }

    fun discoveryFinished(){
        Log.d(Constants.TAG, "[CommunityManager] Discovery finished")
        if(discoveredDevices.isEmpty()){
            Log.d(Constants.TAG, "[CommunityManager] No device found")
        }else{
            Log.d(Constants.TAG,
                "${discoveredDevices.size} devices found")
            connectDevices(discoveredDevices)
        }
    }

    /**
     * Ask connection service to establish connection with other devices
     *
     * @param devices   A list of [BluetoothDevice] to be connected to.
     */
    private fun connectDevices(devices: List<BluetoothDevice>) {
        // Attempt to connect to the device
        Log.d(Constants.TAG, "Asking connection service to connect to  devices found")
        discoveryService.connect(devices)
    }

    fun addNeighbour(bluetoothDevice: BluetoothDevice, handshake: String): Boolean{
        // Avoid adding the same device twice
        if(currentNeighbours.any { it.device.address == bluetoothDevice.address } ){
            return true
        }

        val neighbourInterests  = processInterests(handshake)
        val neighbourCompetence = processCompetence(handshake)
        val neighbourTrustLevel = processTrustLevel(neighbourCompetence, neighbourInterests)
        if(neighbourTrustLevel*10 <= 0.0){
            Log.d(Constants.TAG, "[CommunityManager] refused neighbour: ${bluetoothDevice.address}")
            return false
        }
        currentNeighbours.add(Neighbour((neighbourTrustLevel*100).toInt(), bluetoothDevice, neighbourCompetence, neighbourInterests))
        // Notify activity
        val msg: Message =
            discoveryService.activityHandler.obtainMessage(Constants.MESSAGE_DATASET_CHANGED)
        discoveryService.activityHandler.sendMessage(msg)
        Log.d(Constants.TAG, "[CommunityManager] Added new neighbour: ${bluetoothDevice.address}")
        Log.d(Constants.TAG, "${getBestNeighbour()!!.device.name} Trust: ${getBestNeighbour()!!.trustLevel}")
        return true
    }

    fun removeNeighbour(bluetoothDevice: BluetoothDevice): Boolean{
        val deviceAddress = bluetoothDevice.address
        for(i in currentNeighbours){
            if( i.device.address == deviceAddress){
                Log.d(Constants.TAG, "[StealthManager] Device ${bluetoothDevice.name} removed from list of neighbours")
                currentNeighbours.remove(i)
                // Notify Activity
                val msg: Message =
                    discoveryService.activityHandler.obtainMessage(Constants.MESSAGE_DATASET_CHANGED)
                discoveryService.activityHandler.sendMessage(msg)
                return true
            }
        }
        Log.d(Constants.TAG, "[StealthManager] Device ${bluetoothDevice.name} not found in list of neighbours")
        return false
    }

    fun getBestNeighbour(): Neighbour?{
        if(currentNeighbours.size == 0){
            return null
        }
        var bestNeighbour = currentNeighbours.first()
        for (i in currentNeighbours){
            if(i.trustLevel >= bestNeighbour.trustLevel)
                bestNeighbour = i
        }
        Log.d(Constants.TAG, "[StealthManager] Current best neighbour is ${bestNeighbour.device.name}")
        return bestNeighbour
    }

    fun getCurrentNeighbours() : MutableList<Neighbour>{
        return currentNeighbours
    }

    private fun processTrustLevel(competency: Int, interests: List<Int>): Double {
        if (!interests.contains(HEALTH) || !mInterests.contains(HEALTH)){
            return 0.0
        }
        return (getInterestsTrust(interests) + getCompetencyTrust(competency))/2.0
    }

    data class Neighbour(val trustLevel: Int, val device: BluetoothDevice, val qualification: Int, val interests: List<Int>)

    private fun processCompetence(handshake: String): Int{
        return handshake.substring(0, handshake.indexOf(":")).toInt()
    }

    private fun processInterests(handshake: String): List<Int>{
        val msg = handshake.dropLast(2)
        val interestsIndex = msg.indexOf(":")
        val rawInterests = msg.substring(interestsIndex + 1)

        return rawInterests.split(',').map { it.toInt() }
    }

    private fun getInterestsTrust(interests: List<Int>): Double{
        if(!interests.any { it == 1 }){
            return 0.0
        }
        val intersection = mInterests.intersect(interests)

        return intersection.size/(mInterests.size + 0.0)
    }

    private fun getCompetencyTrust(competency: Int): Double{
        return when(competency) {
            OTHER  -> 0.0
            DOCTOR -> 1.0
            NURSE  -> 0.33
            COP    -> 0.28
            else   -> 0.0
        }
    }

    companion object Trust{
        // Interests
        const val HEALTH = 1
        const val SPORTS = 2

        // Competence constants
        const val OTHER  = 0
        const val DOCTOR = 1
        const val NURSE  = 2
        const val COP    = 3
    }
}