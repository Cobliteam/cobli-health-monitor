package co.cobli.healthmonitor.view

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.cobli.healthmonitor.HealthMonitorService.Companion.TAG
import co.cobli.healthmonitor.model.DataReader


class MainActivity : AppCompatActivity() {

    private val tag = "[$TAG] ${this.javaClass.name.split(".").last()}"

    lateinit var setResult: (String) -> Unit
    lateinit var dataReader: DataReader
    lateinit var appVersion: String

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate")
        dataReader = DataReader(this)
        appVersion = dataReader.getAppVersion()
        enableEdgeToEdge()
        setContent { ScreenView() }
    }

    private fun getFirmwareVersion() {
        setResult("FW Version: ${dataReader.getFirmwareVersion()}")
    }

    private fun getCpuUsage() {
        setResult("CPU Usage: ${dataReader.getCpuUsage()}%")
    }

    private fun getMemoryUsage() {
        val usage = dataReader.getMemoryUsage()
        val result = "RAM:\n" +
                "Total: ${usage.total} bytes\n" +
                "Avail: ${usage.available} bytes\n" +
                "Usage: ${usage.usagePercentage}%"
        setResult(result)
    }

    private fun getInternalStorageUsage() {
        val usage = dataReader.getInternalStorageUsage()
        val result = "Internal Storage:\n" +
                "Total: ${usage.total} bytes\n" +
                "Avail: ${usage.available} bytes\n" +
                "Usage: ${usage.usagePercentage}%"
        setResult(result)
    }

    private fun getExternalStorageUsage() =
        if (dataReader.isExternalStorageMounted()) {
            val usage = dataReader.getExternalStorageUsage()
            val result = "SD Card:\n" +
                    "Total: ${usage.total} bytes\n" +
                    "Avail: ${usage.available} bytes\n" +
                    "Usage: ${usage.usagePercentage}%"
            setResult(result)
        } else {
            setResult("No SD Card available")
        }

    private fun getNetworkUsage() {
        val wifiConsumption = dataReader.getWifiDataConsumption()
        val mobileConsumption = dataReader.getMobileDataConsumption()
        val result = "Wi-Fi:\n" +
                "RX: ${wifiConsumption.received} bytes\n" +
                "TX: ${wifiConsumption.transmitted} bytes\n" +
                "Total: ${wifiConsumption.total} bytes\n" +
                "Mobile:\n" +
                "RX: ${mobileConsumption.received} bytes\n" +
                "TX: ${mobileConsumption.transmitted} bytes\n" +
                "Total: ${mobileConsumption.total} bytes\n"
        setResult(result)
    }

    private fun getAccStatus() {
        setResult("Ignition status: ${dataReader.getIgnitionStatus()}")
    }

    private fun getNetworkStatus() {
        val networkStatus = dataReader.getNetworkStatus()
        val result = "Network status: ${networkStatus.connected}\n" +
                "Network type: ${networkStatus.networkType.name}"
        setResult(result)
    }

    private fun getSimState() {
        setResult("SIM status: ${dataReader.getSimStatus()}")
    }

    private fun getBootTimestamp() {
        setResult("Boot timestamp: ${dataReader.getLastBootTimestamp()}\n" +
                "Boot number: ${dataReader.getBootNumber()}")
    }

    private fun getIMEI() {
        setResult("IMEI: ${dataReader.getImei()}")
    }

    private fun getICCID() {
        setResult("ICCID: ${dataReader.getIccid()}")
    }

    private fun getPowerVoltage() {
        setResult("Voltage: ${dataReader.getPowerVoltage()} V")
    }

    private fun getMACAddress() {
        setResult("MAC ${dataReader.getMacAddress()}")
    }

    private fun runTest(test: MenuTest) {
        Log.d(tag, "Running ${test.name} test")
        try {
            when (test) {
                MenuTest.FIRMWARE_VERSION -> getFirmwareVersion()
                MenuTest.CPU_USAGE -> getCpuUsage()
                MenuTest.MEMORY_USAGE -> getMemoryUsage()
                MenuTest.INTERNAL_STORAGE_USAGE -> getInternalStorageUsage()
                MenuTest.SD_CARD_USAGE -> getExternalStorageUsage()
                MenuTest.NETWORK_STATUS -> getNetworkStatus()
                MenuTest.NETWORK_DATA_USAGE -> getNetworkUsage()
                MenuTest.SIM_STATUS -> getSimState()
                MenuTest.IGNITION_STATUS -> getAccStatus()
                MenuTest.BOOT_INFORMATION -> getBootTimestamp()
                MenuTest.IMEI -> getIMEI()
                MenuTest.ICCID -> getICCID()
                MenuTest.POWER_VOLTAGE -> getPowerVoltage()
                MenuTest.MAC_ADDRESS -> getMACAddress()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error running ${test.name} test", e)
        }
    }

    @Preview
    @Composable
    fun DefaultPreview() {
        ScreenView()
    }

    @Composable
    fun ScreenView() {
        val (result, setResult) = remember { mutableStateOf (String()) }
        this.setResult = setResult
        MaterialTheme {
            Column(
                modifier = Modifier.systemBarsPadding()
            ) {
                HeaderView()
                ResultView(result)
                ButtonListView()
            }
        }
    }

    @Composable
    fun HeaderView() {
        Box(
            Modifier
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .border(width = 2.dp, color = Color.Black, shape = RoundedCornerShape(10.dp))
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "COBLI HEALTH MONITOR ($appVersion)",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }
    }

    @Composable
    fun ResultView(result: String) {
        Text(
            text = result.ifBlank { "Select option below:" },
            modifier = Modifier.padding(start = 10.dp),
            textAlign = TextAlign.Left,
        )
    }

    @Composable
    fun ButtonListView() {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 30.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MenuTest.values().forEach { test ->
                Button(
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { runTest(test) }
                ) {
                    Text(test.name
                        .uppercase()
                        .replace("_", " "))
                }
            }
        }
    }
}