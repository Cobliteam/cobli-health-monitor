package co.cobli.healthmonitor.model.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import co.cobli.cameraMessage.protos.EventTypePB
import co.cobli.cameraMessage.protos.HealthDataPB
import co.cobli.cameraMessage.protos.NetworkTypePB
import java.util.Date

@Entity(tableName = "health_data")
data class HealthDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    var retries: Int = 0,
    val timestamp: Long = Date().time,
    val eventType: Int,
    val firmwareVersion: String,
    val appVersion: String,
    val internalStorageCapacity: Long,
    val internalStorageUsage: Float,
    val sdCardStatus: Boolean,
    val sdCardCapacity: Long,
    val sdCardUsage: Float,
    val cpuUsage: Float,
    val memoryCapacity: Long,
    val memoryUsage: Float,
    val networkStatus: Boolean,
    val networkType: Int,
    val networkName: String,
    val wifiRxBytes: Long,
    val wifiTxBytes: Long,
    val mobileRxBytes: Long,
    val mobileTxBytes: Long,
    val simStatus: Boolean,
    val iccid: String,
    val ignitionStatus: Boolean,
    val lastBootTimestamp: Long,
    val bootNumber: Long,
    val powerVoltage: Float,
    val macAddress: String,
) {
    fun toPB(): HealthDataPB {
        return HealthDataPB.newBuilder().apply {
            timestamp = this@HealthDataEntity.timestamp
            eventType = EventTypePB.forNumber(this@HealthDataEntity.eventType)
            firmwareVersion = this@HealthDataEntity.firmwareVersion
            appVersion = this@HealthDataEntity.appVersion
            internalStorageCapacity = this@HealthDataEntity.internalStorageCapacity
            internalStorageUsage = this@HealthDataEntity.internalStorageUsage
            sdCardStatus = this@HealthDataEntity.sdCardStatus
            sdCardCapacity = this@HealthDataEntity.sdCardCapacity
            sdCardUsage = this@HealthDataEntity.sdCardUsage
            cpuUsage = this@HealthDataEntity.cpuUsage
            ramUsage = this@HealthDataEntity.memoryUsage
            networkStatus = this@HealthDataEntity.networkStatus
            networkType = NetworkTypePB.forNumber(this@HealthDataEntity.networkType)
            wifiDataUsageRx = this@HealthDataEntity.wifiRxBytes
            wifiDataUsageTx = this@HealthDataEntity.wifiTxBytes
            mobileDataUsageRx = this@HealthDataEntity.mobileRxBytes
            mobileDataUsageTx = this@HealthDataEntity.mobileTxBytes
            simCardStatus = this@HealthDataEntity.simStatus
            iccid = this@HealthDataEntity.iccid
            ignitionStatus = this@HealthDataEntity.ignitionStatus
            bootTimestamp = this@HealthDataEntity.lastBootTimestamp
            bootNumber = this@HealthDataEntity.bootNumber
            powerVoltage = this@HealthDataEntity.powerVoltage
            macAddress = this@HealthDataEntity.macAddress
        }.build()
    }
}
