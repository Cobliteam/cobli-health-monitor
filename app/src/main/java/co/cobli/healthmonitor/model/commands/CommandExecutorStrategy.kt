package co.cobli.healthmonitor.model.commands

import android.content.Context
import co.cobli.cameraMessage.protos.CommandType
import co.cobli.healthmonitor.model.commands.executors.AppUpdateCmdExecutor
import co.cobli.healthmonitor.model.commands.executors.RebootCmdExecutor
import co.cobli.healthmonitor.model.database.entities.CommandEntity

object CommandExecutorStrategy {
    fun getExecutor(
        command: CommandEntity,
        listener: CommandExecutionListener,
        context: Context
    ): CommandExecutor? {
        val commandType = CommandType.valueOf(command.type)
        return when (commandType) {
            CommandType.REBOOT -> RebootCmdExecutor(command, listener, context)
            CommandType.APP_UPDATE -> AppUpdateCmdExecutor(command, listener, context)
            else -> null
        }
    }
}