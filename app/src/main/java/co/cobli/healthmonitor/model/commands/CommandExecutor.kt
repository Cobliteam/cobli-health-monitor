package co.cobli.healthmonitor.model.commands

import android.content.Context
import co.cobli.cameraMessage.protos.IgnitionStatus
import co.cobli.healthmonitor.model.DataReader
import co.cobli.healthmonitor.model.database.entities.CommandEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

abstract class CommandExecutor(
    val command: CommandEntity,
    val listener: CommandExecutionListener,
    val context: Context,
) {
    var ignitionStatus = IgnitionStatus.BOTH
    var shouldWaitNetworkStatus = false
    var timeout = 0L

    private var isFinished = false
    private var dataReader = DataReader(context)

    abstract fun parseParameters(): Boolean
    abstract fun execute()
    abstract fun onTimeout()

    fun run() {
        if (parseParameters()) {
            checkPreconditions()
            if (timeout > 0) {
                CoroutineScope(Dispatchers.IO). launch { checkTimeout() }
            }
            execute()
            awaitExecution()
        } else {
            listener.onCommandInvalid(command, "invalid parameters: ${command.parameters}")
        }
    }

    private fun checkPreconditions() {
        while (shouldWaitIgnitionStatus() || shouldWaitNetworkStatus()) {
            Thread.sleep(1000)
        }
    }

    private fun shouldWaitIgnitionStatus(): Boolean =
        when (ignitionStatus) {
            IgnitionStatus.OFF -> dataReader.getIgnitionStatus()
            IgnitionStatus.ON -> !dataReader.getIgnitionStatus()
            else -> false
        }

    private fun shouldWaitNetworkStatus(): Boolean =
        shouldWaitNetworkStatus && !dataReader.getNetworkStatus().connected

    private fun awaitExecution() = runBlocking {
        while (!isFinished) delay(100)
    }

    private suspend fun checkTimeout() {
        delay(timeout)
        if (!isFinished) {
            onTimeout()
            finishExecutor(false, "timeout")
        }
    }

    fun finishExecutor(success: Boolean, message: String?) {
        if (!isFinished) {
            if (success) {
                listener.onCommandSuccess(command)
            } else {
                listener.onCommandError(command, message ?: "failed")
            }
            isFinished = true
        }
    }
}