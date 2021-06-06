package br.ufpr.nr2.mobangelo.bluetooth

object Constants {
    const val REQUEST_ENABLE_BT = 0x1
    const val REQUEST_DISCOVERABLE_BT = 0x2
    const val TAG = "BluetoothInfo"

    // Defines several constants used when transmitting messages between the
    // service and the UI.
    const val MESSAGE_READ:            Int = 0 // used in bluetooth handler to identify message read
    const val MESSAGE_WRITE:           Int = 1 // used in bluetooth handler to identify message write
    const val MESSAGE_TOAST:           Int = 2 // used in bluetooth handler to identify message to be shown as toast
    const val MESSAGE_HANDSHAKE:       Int = 3 // used in bluetooth handler to identify neighbour data message
    const val MESSAGE_NAME:            Int = 4 // used in bluetooth handler to identify device name message
    const val MESSAGE_CONNECTION_LOST: Int = 6
    const val MESSAGE_EMERGENCY_READ:  Int = 7
    const val MESSAGE_EMERGENCY_SENT:  Int = 8
    const val MESSAGE_DATASET_CHANGED: Int = 9
    const val MESSAGE_DEVICE_FOUND:    Int = 10
    const val MESSAGE_COMMUNITY_COMPLETE: Int = 11
    const val MESSAGE_CONNECTION_FAILED: Int = 12
    const val MESSAGE_NO_NEIGHBOUR: Int = 13

    // Name for the SDP record when creating server socket
    const val NAME_INSECURE = "UFPR-Stealth-BluetoothChatInsecure"

    // Key names received from the BluetoothChatService Handler
    const val DEVICE_NAME      = "device_name"    // used in message to identify device name in payload
    const val DEVICE_ADDRESS   = "device_address" // used in message to identify device address in payload
    const val TOAST            = "toast"          // used in message to identify toast content to be shown
    const val CONNECTION_STATE = "c_state"        // used in message to identify new state for connection
    const val PENDING_DEVICES = "discovered_devices_list_size"
    const val MY_DEVICE_COMPETENCY = "mdevice_competency"
    const val EMERGENCY_MESSAGE = "intent_open_emergency_activity"

    // configuration keys
    const val CONFIG_COMPETENCY = "user_competency"
    const val CONFIG_MESSAGE = "user_message"
    const val CONFIG_INTERESTS = "user_interests"

    fun getCompetencyLabel(qualification: Int): String{
        when(qualification){
            CommunityManager.DOCTOR -> {
                return "Médico(a)"
            }
            CommunityManager.COP -> {
                return "Policial"
            }
            CommunityManager.NURSE -> {
                return "Enfermeiro(a)"
            }
            CommunityManager.OTHER -> {
                return "Outra"
            }
            else -> {
                return "Desconhecida"
            }
        }
    }
}