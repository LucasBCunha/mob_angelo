package br.ufpr.nr2.mobangelo.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.util.Log
import br.ufpr.nr2.mobangelo.MainActivity
import java.util.*


class CommunicationManager(val mHandler: Handler, val context: Context) {

    var mAdapter: BluetoothAdapter? = null

    private val serviceUuid: UUID = UUID.fromString("b25a2b8d-022e-459c-a6f0-c9daab1ec92e")
    private var emergencyManager: EmergencyManager
    private var communityManager: CommunityManager
    private var operationStatus : Boolean = false

    companion object{
        const val ACK_SIGNAL = "ACK"
    }

    init {
        mAdapter = BluetoothAdapter.getDefaultAdapter()
        if(mAdapter == null){
            throw ExceptionInInitializerError("Bluetooth not available")
        }

        emergencyManager = EmergencyManager(
            mHandler,
            mAdapter!!,
            serviceUuid,
            this)

        communityManager =  CommunityManager(
            buildInterestsList(),
            ThreadsManager(mHandler, mAdapter!!, serviceUuid,
            this))
    }


    fun canRun(): Boolean{
        return mAdapter != null
    }

    fun isDiscovering() : Boolean{
//      TODO: Improve check (add check of sub classes running: community manager or emergency manager)
        return mAdapter!!.isEnabled && mAdapter!!.isDiscovering
    }

    fun isOperating() : Boolean {
        return operationStatus
    }

    fun startOperation(){
//      When this method is called bt is already enabled!
//      It only has to start the operation of the subclasses
//      start emergency manager
        emergencyManager.start()

//      Execute search in every x seconds
//      Start listening to incoming requests
        communityManager.start()
        mAdapter!!.startDiscovery()
        operationStatus = true
    }

    fun discoveryStarted(){
        communityManager.discoveryStarted()
        scheduleEndOfDiscovery()
    }

    fun deviceFound(device: BluetoothDevice){
        communityManager.deviceFound(device)
    }

    fun discoveryFinished(){
        communityManager.discoveryFinished()
        cleanNeighbours()
    }

    private fun scheduleEndOfDiscovery(){
        Log.d(Constants.TAG, "[Recurring] Entrei no método callStartDiscovery")

        if(!operationStatus){
            return
        }
        Timer().schedule(object : TimerTask() {
            override fun run() {
                if(!operationStatus){
                    return
                }
                if(mAdapter!!.isDiscovering){
                    mAdapter!!.cancelDiscovery()
                }
                Log.d(Constants.TAG, "[Recurring] Começando a busca e agendando a execução da limpeza")
            }
        }, 15000)
    }

    private fun cleanNeighbours(){
        Log.d(Constants.TAG, "[Recurring] Entrei no método cleanNeighbours")
        if(!operationStatus){
            return
        }
        Timer().schedule(object : TimerTask() {
            override fun run() {
                Log.d(Constants.TAG, "[Recurring] Limpando lista de vizinhos e chamando .startDiscovery()")
                if(!operationStatus){
                    return
                }
                communityManager.resetCommunity()

                if(mAdapter!!.startDiscovery()){
                    Log.d(Constants.TAG, "[Recurring] Discovery started")
                }else{
                    Log.e(Constants.TAG, "[Recurring] Discovery FAILED TO START")
                }
            }
        }, 50000)
    }

    fun addNeighbour(bluetoothDevice: BluetoothDevice, handshake: String){
        communityManager.addNeighbour(bluetoothDevice, handshake)
    }

    fun stopOperation(){
        communityManager.stop()
        emergencyManager.stop()
        mAdapter!!.disable()
        operationStatus = false
        Log.d(Constants.TAG, "[StealthManager] Bluetooth off")
    }

    fun getState(): Int {
        return 0
    }

    fun handleEmergencyReceived(socket: BluetoothSocket){
        emergencyManager.handleEmergencyReceived(socket)
    }

    fun handleLocalEmergency(){
        Log.d(Constants.TAG, "[StealthManager] Calling handleEmergency")
        emergencyManager.handleEmergency()
    }

    fun getBestNeighbour() : CommunityManager.Neighbour?{
        return communityManager.getBestNeighbour()
    }

    fun neighbourTimeout(bluetoothDevice: BluetoothDevice){
        communityManager.removeNeighbour(bluetoothDevice)
    }

    fun getNeighboursList() : MutableList<CommunityManager.Neighbour>{
        return communityManager.getCurrentNeighbours()
    }

    /**
     * Handshake format:
     * competence:interest_1,interest_2,interest_3
     *
     * competences (example):
     * 0 = Non health related competence
     * 1 = Doctors
     * 2 = Nurses
     * 3 = Cops
     *
     * Mapping of  interests to integer:
     * health = 1
     * movies = 2
     * sports = 3
     *
     */
    fun encodeHandshake() : String{
        val sp = getSharedPreference()
        val interests = getRawInterests(sp)
        val competency = getRawCompetency(sp)
        var handshake = "$competency:"

        if (interests[0] == '1'){
            handshake += "1,"
        }

        if (interests[1] == '1'){
            handshake += "2,"
        }

        if (interests[2] == '1'){
            handshake += "3,"
        }

        if(handshake.last() == ','){
            handshake = handshake.dropLast(1)
        }

        return handshake
    }


    private fun buildInterestsList() : MutableList<Int> {
        val sp = getSharedPreference()
        val interests = getRawInterests(sp)
        val mInterests = mutableListOf<Int>()

        if (interests[0] == '1'){
            mInterests.add(1)
        }
        if (interests[1] == '1'){
            mInterests.add(2)
        }
        if (interests[2] == '1'){
            mInterests.add(3)
        }
        return mInterests
    }

    private fun getSharedPreference() : SharedPreferences {
        return context.getSharedPreferences(MainActivity.CONFIG_FILE, 0)
    }

    private fun getRawInterests(sp : SharedPreferences) : String {
        return sp.getString(Constants.CONFIG_INTERESTS, "  ")!!
    }

    private fun getRawCompetency(sp: SharedPreferences) : Int {
        return sp.getInt(Constants.CONFIG_COMPETENCY, 0)
    }
}