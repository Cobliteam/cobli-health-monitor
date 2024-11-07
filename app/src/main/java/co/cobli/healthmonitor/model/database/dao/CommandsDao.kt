package co.cobli.healthmonitor.model.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import co.cobli.healthmonitor.model.database.entities.CommandEntity

@Dao
interface CommandsDao {
    @Query("SELECT * FROM commands ORDER BY createdAt LIMIT 1")
    fun getNext(): CommandEntity

    @Query("SELECT COUNT(*) FROM commands")
    fun getCount(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(vararg command: CommandEntity)

    @Query("DELETE FROM commands WHERE id = :id")
    fun deleteById(id: Long)
}