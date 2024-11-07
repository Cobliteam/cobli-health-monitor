package co.cobli.healthmonitor.view

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import co.cobli.healthmonitor.HealthMonitorService
import co.cobli.healthmonitor.HealthMonitorService.Companion.TAG

class LauncherActivity : AppCompatActivity() {

    private val tag = "[$TAG] ${this.javaClass.name.split(".").last()}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate")
        val intent = Intent(this, HealthMonitorService::class.java)
        startForegroundService(intent)
        finish()
    }
}