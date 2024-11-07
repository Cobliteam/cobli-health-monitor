package co.cobli.healthmonitor.model.commands

import co.cobli.healthmonitor.model.database.entities.CommandEntity

interface CommandExecutionListener {
    fun onCommandSuccess(command: CommandEntity)
    fun onCommandError(command: CommandEntity, error: String)
    fun onCommandInvalid(command: CommandEntity, error: String)
}