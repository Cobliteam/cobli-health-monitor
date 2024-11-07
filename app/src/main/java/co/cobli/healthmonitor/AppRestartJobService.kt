package co.cobli.healthmonitor

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import co.cobli.healthmonitor.HealthMonitorService.Companion.TAG

class AppRestartJobService : JobService() {

    private val tag = "[$TAG] ${this.javaClass.name.split(".").last()}"

    override fun onStartJob(params: JobParameters?): Boolean {
        val extras = params?.extras
        val reboot = extras?.getBoolean("reboot", false) ?: false
        Log.d(tag, "running job service after app update. reboot=$reboot")

        if (reboot) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.reboot(null)
        } else {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        }

        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }
}