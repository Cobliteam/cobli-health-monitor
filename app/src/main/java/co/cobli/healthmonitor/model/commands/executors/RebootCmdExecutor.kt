package co.cobli.healthmonitor.model.commands.executors

import android.content.Context
import android.util.Log
import co.cobli.cameraMessage.protos.IgnitionStatus
import co.cobli.healthmonitor.HealthMonitorService.Companion.TAG
import co.cobli.healthmonitor.model.commands.CommandExecutionListener
import co.cobli.healthmonitor.model.commands.CommandExecutor
import co.cobli.healthmonitor.model.database.entities.CommandEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class RebootCmdExecutor(
    command: CommandEntity,
    listener: CommandExecutionListener,
    context: Context,
) : CommandExecutor(command, listener, context) {

    private val tag = "[$TAG] ${this.javaClass.name.split(".").last()}"
    private var delay = 0L

    override fun parseParameters(): Boolean {
        try {
            if (command.parameters.size == 2) {
                delay = command.parameters[0].toLong()
                ignitionStatus = IgnitionStatus.forNumber(command.parameters[1].toInt())
                return true
            }
        } catch (e: Exception) {
            Log.e(tag, "parseParameters error", e)
        }
        return false
    }

    override fun execute() {
        try {
            if (delay > 0) {
                Log.d(tag, "sleeping for $delay ms before rebooting")
                runBlocking { delay(delay) }
            }

            Log.d(tag, "rebooting")
            finishExecutor(true, null)
            reboot()
        } catch (e: Exception) {
            finishExecutor(false, e.toString())
        }
    }

    override fun onTimeout() {
    }

    private fun reboot() = Runtime.getRuntime().exec("reboot")
}