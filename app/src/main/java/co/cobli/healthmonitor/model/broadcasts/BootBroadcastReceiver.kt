package co.cobli.healthmonitor.model.broadcasts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import co.cobli.healthmonitor.HealthMonitorService
import co.cobli.healthmonitor.HealthMonitorService.Companion.TAG

class BootBroadcastReceiver : BroadcastReceiver() {

    private val tag = "[$TAG] ${this.javaClass.name.split(".").last()}"

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let {
             if (intent.action == ACTION_BOOT_COMPLETED) {
                 Log.d(tag, "Received broadcast $ACTION_BOOT_COMPLETED")
                 val serviceIntent = Intent(context, HealthMonitorService::class.java)
                 context?.startForegroundService(serviceIntent)
            }
        }
    }

    companion object {
        const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
    }
}