package co.cobli.healthmonitor.model.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import co.cobli.healthmonitor.model.database.entities.HealthDataEntity

@Dao
interface HealthDataDao {
    @Query("SELECT * FROM health_data ORDER BY timestamp")
    fun getAll(): List<HealthDataEntity>

    @Query("SELECT COUNT(*) FROM health_data")
    fun getCount(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(vararg healthData: HealthDataEntity)

    @Query("DELETE FROM health_data WHERE id = :id")
    fun deleteById(id: Long)
}