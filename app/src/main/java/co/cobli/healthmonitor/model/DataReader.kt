package co.cobli.healthmonitor.model

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.usage.NetworkStatsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Context.NETWORK_STATS_SERVICE
import android.content.Context.STORAGE_SERVICE
import android.content.Context.STORAGE_STATS_SERVICE
import android.content.Context.TELEPHONY_SERVICE
import android.content.Context.TELEPHONY_SUBSCRIPTION_SERVICE
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Environment
import android.os.storage.StorageManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import co.cobli.healthmonitor.HealthMonitorService.Companion.TAG
import co.cobli.healthmonitor.model.data.NetworkConsumption
import co.cobli.healthmonitor.model.data.NetworkStatus
import co.cobli.healthmonitor.model.data.StorageUsage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.NetworkInterface
import java.util.Calendar
import java.util.Date

class DataReader(
    context: Context
) : IDataReader {

    private val tag = "[$TAG] ${this.javaClass.name.split(".").last()}"
    private val activityManager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
    private val storageStatsManager = context.getSystemService(STORAGE_STATS_SERVICE) as StorageStatsManager
    private val storageManager = context.getSystemService(STORAGE_SERVICE) as StorageManager
    private val networkStatsManager = context.getSystemService(NETWORK_STATS_SERVICE) as NetworkStatsManager
    private val telephonyManager = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
    private val subscriptionManager = context.getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
    private val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    private val packageManager = context.packageManager
    private val packageName = context.packageName

    override fun getImei(): Long {
        return try {
            telephonyManager.deviceId.toLong()
        } catch (e: Exception) {
            UNKNOWN_LONG
        }
    }

    override fun getMacAddress(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .firstOrNull { it.name.equals("wlan0", ignoreCase = true) }
                ?.hardwareAddress
                ?.joinToString(separator = ":") { byte -> String.format("%02X", byte) }
                ?.uppercase() ?: UNKNOWN_STRING
        } catch (e: Exception) {
            UNKNOWN_STRING
        }
    }

    override fun getAppVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            UNKNOWN_STRING
        }
    }

    override fun getFirmwareVersion(): String {
        return try {
            runCommand(GET_FW_VERSION_CMD).trim()
        } catch (e: Exception) {
            UNKNOWN_STRING
        }
    }

    override fun getInternalStorageUsage(): StorageUsage {
        return getStorageUsage(INTERNAL_STORAGE_DIR)
    }

    override fun getExternalStorageUsage(): StorageUsage {
        return if (isExternalStorageMounted())
            getStorageUsage(EXTERNAL_STORAGE_DIR)
        else StorageUsage(UNKNOWN_LONG, UNKNOWN_LONG)
    }

    override fun isExternalStorageMounted(): Boolean {
        return try {
            runCommand(GET_SD_CARD_STATUS_CMD) == Environment.MEDIA_MOUNTED
        } catch (e: Exception) {
            false
        }
    }

    override fun getCpuUsage(): Float = runBlocking {
        try {
            val cpuData1 = getInstantCpuUsage()
            delay(CPU_READ_INTERVAL)
            val cpuData2 = getInstantCpuUsage()
            val diff = cpuData1.zip(cpuData2) { a, b -> b - a }
            val totalCpu = diff.sum().toBigDecimal()
            val idleCpu = diff[3].toBigDecimal()
            val cpuUsage = if (totalCpu.toFloat() > 0) {
                (totalCpu - idleCpu)
                    .multiply(BigDecimal(100))
                    .divide(totalCpu, 2, RoundingMode.HALF_UP)
                    .toFloat()
            } else 0F
            return@runBlocking cpuUsage
        } catch (e: Exception) {
            Log.e(tag, "getCpuUsage error", e)
            return@runBlocking UNKNOWN_FLOAT
        }
    }

    override fun getMemoryUsage(): StorageUsage {
        try {
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            return StorageUsage(memoryInfo.totalMem, memoryInfo.availMem)
        } catch (e: Exception) {
            Log.e(tag, "getMemoryUsage error", e)
            return StorageUsage(UNKNOWN_LONG, UNKNOWN_LONG)
        }
    }

    override fun getNetworkStatus(): NetworkStatus {
        try {
            val networkTypes = listOf(ConnectivityManager.TYPE_MOBILE, ConnectivityManager.TYPE_WIFI)
            networkTypes.forEach { type ->
                connectivityManager.getNetworkInfo(type)?.let { networkInfo ->
                    if (networkInfo.state == NetworkInfo.State.CONNECTED) {
                        return NetworkStatus(true, networkInfo.typeName)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "getNetworkStatus error", e)
        }
        return NetworkStatus(false, UNKNOWN_STRING)
    }

    override fun getWifiDataConsumption(): NetworkConsumption =
        getNetworkConsumption(ConnectivityManager.TYPE_WIFI, "")

    @SuppressLint("HardwareIds")
    override fun getMobileDataConsumption(): NetworkConsumption =
        if (telephonyManager.subscriberId != null)
            getNetworkConsumption(ConnectivityManager.TYPE_MOBILE, telephonyManager.subscriberId)
        else NetworkConsumption(UNKNOWN_LONG, UNKNOWN_LONG)

    override fun getSimStatus(): Boolean {
        return try {
            telephonyManager.getSimState(0) == TelephonyManager.SIM_STATE_READY
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    override fun getIccid(): String {
        try {
            val subscriptionInfos = subscriptionManager.activeSubscriptionInfoList
            if (subscriptionInfos != null && subscriptionInfos.size > 0) {
                val subscriptionInfo = subscriptionInfos[0]
                return subscriptionInfo.iccId
            }
        } catch (e: Exception) {
            Log.e(tag, "getIccid error", e)
        }
        return UNKNOWN_STRING
    }

    override fun getIgnitionStatus(): Boolean {
        return try {
            runCommand(GET_ACC_STATUS_CMD).trim() == "1"
        } catch (e: Exception) {
            false
        }
    }

    override fun getLastBootTimestamp(): Long {
        return try {
            runCommand(GET_BOOT_TIMESTAMP_CMD).trim().toLong()
        } catch (e: Exception) {
            UNKNOWN_LONG
        }
    }

    override fun getBootNumber(): Long {
        return try {
            runCommand(GET_BOOT_NUMBER_CMD).trim().toLong()
        } catch (e: Exception) {
            UNKNOWN_LONG
        }
    }

    override fun getPowerVoltage(): Float {
        return try {
            runCommand(GET_POWER_VOLTAGE_CMD)
                .trim()
                .toBigDecimal()
                .divide(BigDecimal(1000), 3, RoundingMode.HALF_UP)
                .toFloat()
        } catch (e: Exception) {
            UNKNOWN_FLOAT
        }
    }

    private fun runCommand(cmd: String): String {
        var response: String? = null
        try {
            val process = Runtime.getRuntime().exec(cmd)
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
            response = bufferedReader.readLine()
            process.destroy()
        } catch (e: Throwable) {
            Log.e(tag, "runCommand error: $cmd", e)
        }

        if (response.isNullOrBlank()) {
            Log.d(tag, "command $cmd returned null or blank")
            throw IOException("Error running command")
        } else {
            return response
        }
    }

    private fun getStorageUsage(storageDir: String): StorageUsage {
        try {
            val storageUuid = storageManager.getUuidForPath(File(storageDir))
            val total = storageStatsManager.getTotalBytes(storageUuid)
            val available = storageStatsManager.getFreeBytes(storageUuid)
            return StorageUsage(total, available)
        } catch (e: Exception) {
            Log.e(tag, "getStorageUsage error", e)
            return StorageUsage(UNKNOWN_LONG, UNKNOWN_LONG)
        }
    }

    private fun getFirstDayOfCurrentMonth(): Date {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    private fun getNetworkConsumption(networkType: Int, subscriberId: String): NetworkConsumption {
        try {
            val startTime = getFirstDayOfCurrentMonth().time
            val endTime = Date().time
            val bucket = networkStatsManager.querySummaryForDevice(networkType, subscriberId, startTime, endTime)
            return NetworkConsumption(bucket.rxBytes, bucket.txBytes)
        } catch (e: Exception) {
            Log.e(tag, "getNetworkConsumption error", e)
            return NetworkConsumption(UNKNOWN_LONG, UNKNOWN_LONG)
        }
    }

    private fun getInstantCpuUsage(): List<Long> {
        return runCommand(GET_CPU_USAGE_CMD)
            .replace("cpu", "")
            .trim()
            .split(" ")
            .map { it.toLong() }
    }

    companion object {
        const val UNKNOWN_LONG = -1L
        const val UNKNOWN_FLOAT = -1F
        const val UNKNOWN_STRING = "UNKNOWN"

        const val CPU_READ_INTERVAL = 1000L

        const val GET_FW_VERSION_CMD = "getprop ro.build.display.id"
        const val GET_CPU_USAGE_CMD = "head -1 /proc/stat"
        const val GET_SD_CARD_STATUS_CMD = "getprop vold.sdcard0.state"
        const val GET_ACC_STATUS_CMD = "cat /sys/class/jimi_misc/jimi_misc_ctrl/acc_status"
        const val GET_BOOT_TIMESTAMP_CMD = "getprop ro.runtime.firstboot"
        const val GET_BOOT_NUMBER_CMD = "getprop persist.boot.num"
        const val GET_IMEI_CMD = "getprop persist.sys.globalimei"
        const val GET_POWER_VOLTAGE_CMD = "cat /sys/class/jimi_misc/jimi_misc_ctrl/adc_voltage"

        const val INTERNAL_STORAGE_DIR = "/storage/emulated/0"
        const val EXTERNAL_STORAGE_DIR = "/storage/sdcard0"
    }
}