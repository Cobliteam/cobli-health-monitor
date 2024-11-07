package co.cobli.healthmonitor.model.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import co.cobli.cameraMessage.protos.CommandMessagePB
import java.util.Date

@Entity(tableName = "commands")
data class CommandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    var type: String,
    var parameters: List<String>,
    var createdAt: Date,
    var updatedAt: Date,
    var retries: Int,
) {
    constructor(commandPB: CommandMessagePB, currentDate: Date) : this (
        type = commandPB.type.name,
        parameters = commandPB.parameters.split(";"),
        createdAt = currentDate,
        updatedAt = currentDate,
        retries = 0,
    )
}
