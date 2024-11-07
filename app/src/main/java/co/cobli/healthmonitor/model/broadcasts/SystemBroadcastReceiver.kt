package co.cobli.healthmonitor.model.broadcasts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import co.cobli.cameraMessage.protos.EventTypePB
import co.cobli.healthmonitor.HealthMonitorService.Companion.TAG

class SystemBroadcastReceiver(
    private val broadcastListener: BroadcastListener?
) : BroadcastReceiver() {

    private val tag = "[$TAG] ${this.javaClass.name.split(".").last()}"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (isInitialStickyBroadcast) {
            Log.d(tag, "Sticky broadcast received and ignored ${intent?.action}")
            return
        }

        intent?.let {
            val action = intent.action ?: return
            if (action in BROADCAST_LIST + SD_CARD_BROADCAST_LIST) {
                Log.d(tag, "Received broadcast $action")
                broadcastListener?.onBroadcastReceived(action)
            }
        }
    }

    companion object {
        const val LAST_BOOT_TIMESTAMP_CHANGED = "LAST_BOOT_TIMESTAMP_CHANGED"
        const val BROADCAST_CONNECTIVITY_CHANGE = "android.net.conn.CONNECTIVITY_CHANGE"
        const val BROADCAST_ACTION_EXTERNAL_POWER_INSERT = "android.intent.action.EXTERNAL_POWER_INSERT"
        const val BROADCAST_ACTION_EXTERNAL_POWER_REMOVED = "android.intent.action.EXTERNAL_POWER_REMOVED"
        const val BROADCAST_ACTION_SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED"
        const val BROADCAST_ACTION_MEDIA_MOUNTED = "android.intent.action.MEDIA_MOUNTED"
        const val BROADCAST_ACTION_MEDIA_UNMOUNTED = "android.intent.action.MEDIA_UNMOUNTED"

        val BROADCAST_LIST = listOf(
             BROADCAST_CONNECTIVITY_CHANGE,
             BROADCAST_ACTION_EXTERNAL_POWER_INSERT,
             BROADCAST_ACTION_EXTERNAL_POWER_REMOVED,
             BROADCAST_ACTION_SIM_STATE_CHANGED,
        )
        val SD_CARD_BROADCAST_LIST = listOf(
            BROADCAST_ACTION_MEDIA_MOUNTED,
            BROADCAST_ACTION_MEDIA_UNMOUNTED,
        )

        fun getEventTypeFromIntent(intent: String): EventTypePB {
            return when (intent) {
                LAST_BOOT_TIMESTAMP_CHANGED -> EventTypePB.BOOT_TIMESTAMP_CHANGED
                BROADCAST_CONNECTIVITY_CHANGE -> EventTypePB.NETWORK_STATUS_CHANGED
                BROADCAST_ACTION_EXTERNAL_POWER_INSERT -> EventTypePB.IGNITION_ON
                BROADCAST_ACTION_EXTERNAL_POWER_REMOVED -> EventTypePB.IGNITION_OFF
                BROADCAST_ACTION_SIM_STATE_CHANGED -> EventTypePB.SIM_CARD_REMOVED
                BROADCAST_ACTION_MEDIA_MOUNTED -> EventTypePB.SD_CARD_INSERTED
                BROADCAST_ACTION_MEDIA_UNMOUNTED -> EventTypePB.SD_CARD_REMOVED
                else -> EventTypePB.UNKNOWN_EVENT
            }
        }
    }
}