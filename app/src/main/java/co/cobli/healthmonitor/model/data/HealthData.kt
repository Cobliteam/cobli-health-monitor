package co.cobli.healthmonitor.model.data

import co.cobli.healthmonitor.model.database.entities.HealthDataEntity

data class HealthData(
    val firmwareVersion: String,
    val appVersion: String,
    val internalStorageUsage: StorageUsage,
    val externalStorageUsage: StorageUsage,
    val isExternalStorageMounted: Boolean,
    val cpuUsage: Float,
    val memoryUsage: StorageUsage,
    val networkStatus: NetworkStatus,
    val wifiDataConsumption: NetworkConsumption,
    val mobileDataConsumption: NetworkConsumption,
    val simStatus: Boolean,
    val iccid: String,
    val ignitionStatus: Boolean,
    val lastBootTimestamp: Long,
    val bootNumber: Long,
    val powerVoltage: Float,
    val macAddress: String,
) {
    fun toEntitity(eventType: Int): HealthDataEntity {
        return HealthDataEntity(
            eventType = eventType,
            firmwareVersion = firmwareVersion,
            appVersion = appVersion,
            internalStorageCapacity = internalStorageUsage.total,
            internalStorageUsage = internalStorageUsage.usagePercentage,
            sdCardStatus = isExternalStorageMounted,
            sdCardCapacity = externalStorageUsage.total,
            sdCardUsage = externalStorageUsage.usagePercentage,
            cpuUsage = cpuUsage,
            memoryCapacity = memoryUsage.total,
            memoryUsage = memoryUsage.usagePercentage,
            networkStatus = networkStatus.connected,
            networkType = networkStatus.networkType.number,
            networkName = networkStatus.networkName,
            wifiRxBytes = wifiDataConsumption.received,
            wifiTxBytes = wifiDataConsumption.transmitted,
            mobileRxBytes = mobileDataConsumption.received,
            mobileTxBytes = mobileDataConsumption.transmitted,
            simStatus = simStatus,
            iccid = iccid,
            ignitionStatus = ignitionStatus,
            lastBootTimestamp = lastBootTimestamp,
            bootNumber = bootNumber,
            powerVoltage = powerVoltage,
            macAddress = macAddress,
        )
    }
}