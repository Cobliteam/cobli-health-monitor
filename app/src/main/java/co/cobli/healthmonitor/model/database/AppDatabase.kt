package co.cobli.healthmonitor.model.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import co.cobli.healthmonitor.model.database.dao.CommandsDao
import co.cobli.healthmonitor.model.database.dao.HealthDataDao
import co.cobli.healthmonitor.model.database.entities.CommandEntity
import co.cobli.healthmonitor.model.database.entities.HealthDataEntity

@Database(entities = [HealthDataEntity::class, CommandEntity::class], version = 1)
@TypeConverters(Converter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun healthDataDao(): HealthDataDao
    abstract fun commandsDao(): CommandsDao
}