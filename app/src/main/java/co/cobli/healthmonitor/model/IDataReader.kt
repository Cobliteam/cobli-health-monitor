package co.cobli.healthmonitor.model

import co.cobli.healthmonitor.model.data.NetworkConsumption
import co.cobli.healthmonitor.model.data.NetworkStatus
import co.cobli.healthmonitor.model.data.StorageUsage
import java.math.BigDecimal

interface IDataReader {
    fun getImei(): Long
    fun getMacAddress(): String
    fun getFirmwareVersion(): String
    fun getAppVersion(): String
    fun getInternalStorageUsage(): StorageUsage
    fun getExternalStorageUsage(): StorageUsage
    fun isExternalStorageMounted(): Boolean
    fun getCpuUsage(): Float
    fun getMemoryUsage(): StorageUsage
    fun getNetworkStatus(): NetworkStatus
    fun getWifiDataConsumption(): NetworkConsumption
    fun getMobileDataConsumption(): NetworkConsumption
    fun getSimStatus(): Boolean
    fun getIccid(): String
    fun getIgnitionStatus(): Boolean
    fun getLastBootTimestamp(): Long
    fun getBootNumber(): Long
    fun getPowerVoltage(): Float
}