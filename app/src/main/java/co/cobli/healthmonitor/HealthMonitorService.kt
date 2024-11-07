package co.cobli.healthmonitor

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.provider.Settings
import android.util.Log
import androidx.room.Room
import co.cobli.cameraMessage.protos.EventTypePB
import co.cobli.cameraMessage.protos.MessagePB
import co.cobli.cameraMessage.protos.MessageTypePB
import co.cobli.cameraMessage.protos.SettingsType
import co.cobli.healthmonitor.model.broadcasts.BroadcastListener
import co.cobli.healthmonitor.model.broadcasts.SystemBroadcastReceiver
import co.cobli.healthmonitor.model.DataReader
import co.cobli.healthmonitor.model.IDataReader
import co.cobli.healthmonitor.model.commands.CommandExecutionListener
import co.cobli.healthmonitor.model.commands.CommandExecutor
import co.cobli.healthmonitor.model.commands.CommandExecutorStrategy
import co.cobli.healthmonitor.model.data.HealthData
import co.cobli.healthmonitor.model.database.AppDatabase
import co.cobli.healthmonitor.model.database.entities.CommandEntity
import co.cobli.healthmonitor.model.messageProtocol.MessageProtocolParser
import co.cobli.healthmonitor.model.network.tcp.TcpClient
import co.cobli.healthmonitor.view.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Date


class HealthMonitorService : Service(), BroadcastListener, TcpClient.Listener, CommandExecutionListener {

    private val tag = "[$TAG] ${this.javaClass.name.split(".").last()}"

    private lateinit var db: AppDatabase
    private lateinit var dataReader: IDataReader
    private lateinit var systemBroadcastReceiver: SystemBroadcastReceiver
    private lateinit var serviceLooper: Looper
    private lateinit var serviceHandler: ServiceHandler
    private lateinit var tcpClient: TcpClient
    private var reconnectAttempts = 0
    private var shouldReceiveAck = MutableStateFlow(false)
    private var executor: CommandExecutor? = null

    override fun onCreate() {
        Log.d(tag, "onCreate")

        dataReader = DataReader(this)
        initDatabase()
        initTcpClient()
        initBroadcastReceiver()
        showNotification()

        HandlerThread(this.javaClass.name, android.os.Process.THREAD_PRIORITY_BACKGROUND).apply {
            start()
            serviceLooper = looper
            serviceHandler = ServiceHandler(looper)
        }

        checkLastBootTimestamp()
        Log.d(tag, "App version: ${dataReader.getAppVersion()}")
    }

    private fun initDatabase() {
        db = Room
            .databaseBuilder(this, AppDatabase::class.java, "health_monitor.db")
            .allowMainThreadQueries()
            .build()
    }

    private fun initTcpClient() {
        tcpClient = TcpClient(
            this,
            SERVER_DNS,
            SERVER_PORT,
            LISTEN_INTERVAL,
        )
        CoroutineScope(Dispatchers.IO).launch { tcpClient.start() }
    }

    private fun restartTcpClient() {
        tcpClient.stop()
        initTcpClient()
    }

    private fun initBroadcastReceiver() {
        systemBroadcastReceiver = SystemBroadcastReceiver(this)
        val intentFilter = IntentFilter()
        val sdCardIntentFilter = IntentFilter()
        SystemBroadcastReceiver.BROADCAST_LIST.forEach{ intentFilter.addAction(it) }
        SystemBroadcastReceiver.SD_CARD_BROADCAST_LIST.forEach{ sdCardIntentFilter.addAction(it) }
        sdCardIntentFilter.addDataScheme("file")
        registerReceiver(systemBroadcastReceiver, intentFilter)
        registerReceiver(systemBroadcastReceiver, sdCardIntentFilter)
    }

    private fun checkLastBootTimestamp() {
        val sharedPrefs = getSharedPreferences(SHARED_PREFS_FILE_NAME, Context.MODE_PRIVATE)
        val lastBootTimestamp = sharedPrefs.getLong(SHARED_PREFS_LAST_BOOT_TIMESTAMP, 0L)
        val currentBootTimestamp = dataReader.getLastBootTimestamp()

        if (currentBootTimestamp != lastBootTimestamp) {
            Log.d(tag, "Last boot timestamp changed")
            val editor = sharedPrefs.edit()
            editor.putLong(SHARED_PREFS_LAST_BOOT_TIMESTAMP, currentBootTimestamp)
            editor.apply()
            this.onBroadcastReceived(SystemBroadcastReceiver.LAST_BOOT_TIMESTAMP_CHANGED)
        }
    }

    private inner class ServiceHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            Log.d(tag, "Running handleMessage startId=${msg.arg1}")
            Thread(healthMonitor).start()
            Thread(commandMonitor).start()
        }

        val healthMonitor = Runnable {
            while (true) {
                try {
                    if (dataReader.getNetworkStatus().connected) {
                        sendMessagesToServer()
                    }
                    await()
                } catch (e: Exception) {
                    Log.e(tag, "HealthMonitor runnable error", e)
                }
            }
        }

        val commandMonitor = Runnable {
            while(true) {
                try {
                    Thread.sleep(1000)
                    if (db.commandsDao().getCount() > 0) {
                        val cmd = db.commandsDao().getNext()
                        executor = CommandExecutorStrategy.getExecutor(cmd,
                            this@HealthMonitorService, this@HealthMonitorService)
                        executor?.let {
                            it.run()
                            executor = null
                        } ?: db.commandsDao().deleteById(cmd.id)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "commandMonitor runnable error", e)
                }
            }
        }
    }

    private fun await() {
        val reconnectIntervalSeconds = when (reconnectAttempts) {
            in 0..3 -> 15
            in 4..6 -> 30
            in 7..9 -> 60
            in 10..12 -> 300
            else -> 600
        }
        Thread.sleep(reconnectIntervalSeconds * 1000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceHandler.obtainMessage().also { msg ->
            msg.arg1 = startId
            serviceHandler.sendMessage(msg)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.d(tag, "onDestroy")
        tcpClient.stop()
    }

    private fun getInstantHealthData(): HealthData =
        HealthData(
            dataReader.getFirmwareVersion(),
            dataReader.getAppVersion(),
            dataReader.getInternalStorageUsage(),
            dataReader.getExternalStorageUsage(),
            dataReader.isExternalStorageMounted(),
            dataReader.getCpuUsage(),
            dataReader.getMemoryUsage(),
            dataReader.getNetworkStatus(),
            dataReader.getWifiDataConsumption(),
            dataReader.getMobileDataConsumption(),
            dataReader.getSimStatus(),
            dataReader.getIccid(),
            dataReader.getIgnitionStatus(),
            dataReader.getLastBootTimestamp(),
            dataReader.getBootNumber(),
            dataReader.getPowerVoltage(),
            dataReader.getMacAddress(),
        )

    private fun getLastIgnitionStatus(): Boolean {
        val sharedPrefs = getSharedPreferences(SHARED_PREFS_FILE_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(SHARED_PREFS_LAST_IGNITION_STATUS, false)
    }

    @SuppressLint("ApplySharedPref")
    private fun setLastIgnitionStatus(status: Boolean) {
        val sharedPrefs = getSharedPreferences(SHARED_PREFS_FILE_NAME, Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        editor.putBoolean(SHARED_PREFS_LAST_IGNITION_STATUS, status)
        editor.commit()
    }

    private fun isDuplicateIgnitionEvent(): Boolean = runBlocking {
        Log.d(tag, "Checking for duplicate ignition event")
        val lastIgnitionStatus = getLastIgnitionStatus()
        delay(2000)
        val currentIgnitionStatus = dataReader.getIgnitionStatus()
        return@runBlocking currentIgnitionStatus == lastIgnitionStatus
    }

    override fun onBroadcastReceived(action: String) {
        try {
            val eventType = SystemBroadcastReceiver.getEventTypeFromIntent(action)
            if ((eventType == EventTypePB.IGNITION_ON || eventType == EventTypePB.IGNITION_OFF)
                && isDuplicateIgnitionEvent()) {
                Log.d(tag, "Ignoring duplicate event $action")
                return
            }

            Log.d(tag, "Reading new health data")
            val healthData = getInstantHealthData()
            db.healthDataDao().save(healthData.toEntitity(eventType.number))
            setLastIgnitionStatus(healthData.ignitionStatus)

            if (eventType == EventTypePB.NETWORK_STATUS_CHANGED && dataReader.getNetworkStatus().connected) {
                reconnectAttempts = 0
                restartTcpClient()
            }
        } catch (e: Exception) {
            Log.e(tag, "onBroadcastReceived error", e)
        }
    }

    @SuppressLint("ForegroundServiceType")
    private fun showNotification() {
        val notificationChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            enableLights(true)
            lightColor = Color.RED
            setShowBadge(true)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(notificationChannel)

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent: PendingIntent? = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val icon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        val notification: Notification = Notification.Builder(applicationContext, NOTIFICATION_CHANNEL_ID).apply {
            setContentTitle(getString(R.string.notification_title))
            setContentText(getString(R.string.notification_text))
            setSmallIcon(R.mipmap.ic_launcher)
            setLargeIcon(icon)
            setContentIntent(pendingIntent)
        }.build()

        startForeground(1, notification)
    }

    private fun sendMessagesToServer() {
        runBlocking {
            if (shouldReceiveAck.value) {
                Log.d(tag, "Last sent message didn't receive ack, restarting TCP client")
                shouldReceiveAck.value = false
                reconnectAttempts++
                restartTcpClient()
                return@runBlocking
            }

            val pendingMessages = db.healthDataDao().getAll()
            Log.d(tag, "There are ${pendingMessages.size} pending messages to send to server")

            for (message in pendingMessages) {
                if (message.retries >= MAX_SENDING_RETRIES) {
                    Log.d(tag, "Message with id ${message.id} has reached max sent retries, deleting message.")
                    db.healthDataDao().deleteById(message.id)
                } else {
                    val encodedMessage = MessageProtocolParser.encodeHealthMessage(message, dataReader.getImei())

                    if (tcpClient.send(encodedMessage)) {
                        shouldReceiveAck.value = true
                        message.retries++
                        db.healthDataDao().save(message)
                        delay(SEND_MESSAGES_INTERVAL)
                        reconnectAttempts = 0
                    } else {
                        Log.d(tag, "Couldn't send message to server, restarting TCP client")
                        reconnectAttempts++
                        restartTcpClient()
                        break
                    }
                }
            }
        }
    }

    override fun onMessageReceived(message: ByteArray) {
        try {
            val messages = MessageProtocolParser.findValidMessages(message)
            messages.forEach {
                val messagePB = MessageProtocolParser.decodeMessage(it)
                when (messagePB.messageType) {
                    MessageTypePB.ACK_MESSAGE -> processAckMessage(messagePB)
                    MessageTypePB.SETTINGS_MESSAGE -> processSettingsMessage(messagePB)
                    MessageTypePB.COMMAND_MESSAGE -> processCommandMessage(messagePB)
                    else -> Log.d(tag, "onMessageReceived unknown message type ${messagePB.messageType}")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "onMessageReceived error", e)
        }
    }

    private fun processAckMessage(message: MessagePB) {
        Log.d(tag, "processing ack message: ${Utils.toHexString(message.toByteArray())}")
        try {
            val id = MessageProtocolParser.decodeAckMessage(message).sequence
            db.healthDataDao().deleteById(id)
            this.shouldReceiveAck.value = false
        } catch (e: Exception) {
            Log.e(tag, "error processing ack message", e)
        }
    }

    private fun processSettingsMessage(message: MessagePB) {
        Log.d(tag, "processing settings message: ${Utils.toHexString(message.toByteArray())}")
        try {
            val settingsPB = MessageProtocolParser.decodeSettingsMessage(message)
            Log.d(tag, "saving ${settingsPB.key}=${settingsPB.value} on device settings")
            when (settingsPB.type) {
                SettingsType.BOOLEAN -> Settings.Global.putInt(
                    contentResolver,
                    settingsPB.key,
                    if (settingsPB.value == "true") 1 else 0
                )
                SettingsType.STRING -> Settings.Global.putString(
                    contentResolver,
                    settingsPB.key,
                    settingsPB.value
                )
                SettingsType.FLOAT -> Settings.Global.putFloat(
                    contentResolver,
                    settingsPB.key,
                    settingsPB.value.toFloat()
                )
                SettingsType.DOUBLE -> Settings.Global.putFloat(
                    contentResolver,
                    settingsPB.key,
                    settingsPB.value.toFloat()
                )
                SettingsType.INTEGER -> Settings.Global.putInt(
                    contentResolver,
                    settingsPB.key,
                    settingsPB.value.toInt()
                )
                SettingsType.LONG -> Settings.Global.putLong(
                    contentResolver,
                    settingsPB.key,
                    settingsPB.value.toLong()
                )
                else -> Log.d(tag, "invalid type ${settingsPB.type}")
            }
        } catch (e: Exception) {
            Log.e(tag, "error processing settings message", e)
        }
    }

    private fun processCommandMessage(message: MessagePB) {
        Log.d(tag, "processing command message: ${Utils.toHexString(message.toByteArray())}")
        try {
            val commandPB = MessageProtocolParser.decodeCommandMessage(message)
            db.commandsDao().save(CommandEntity(commandPB, Date()))
        } catch (e: Exception) {
            Log.e(tag, "error processing command message", e)
        }
    }

    override fun onCommandSuccess(command: CommandEntity) {
        Log.d(tag, "onCommandSuccess: ${command.type}")
        db.commandsDao().deleteById(command.id)
    }

    override fun onCommandError(command: CommandEntity, error: String) {
        Log.d(tag, "onCommandError ${command.type}: $error")
        if (command.retries >= MAX_RETRIES_COMMAND) {
            Log.d(tag, "deleting command ${command.type}")
            db.commandsDao().deleteById(command.id)
        } else {
            command.retries++
            db.commandsDao().save(command)
        }
    }

    override fun onCommandInvalid(command: CommandEntity, error: String) {
        Log.d(tag, "onCommandInvalid ${command.type}: $error")
        db.commandsDao().deleteById(command.id)
    }

    companion object {
        const val TAG = "COBLI_HEALTH_MONITOR"
        const val SHARED_PREFS_FILE_NAME = "health_data"
        const val SHARED_PREFS_LAST_BOOT_TIMESTAMP = "lastBootTimestamp"
        const val SHARED_PREFS_LAST_IGNITION_STATUS = "lastIgnitionStatus"
        const val NOTIFICATION_CHANNEL_ID = "co.cobli.healthmonitor"
        const val NOTIFICATION_CHANNEL_NAME = "ch1"
        const val SERVER_DNS = "goctupus-gateway-tcp.cobli.co"
        const val SERVER_PORT = 21103
        const val LISTEN_INTERVAL: Long = 100
        const val SEND_MESSAGES_INTERVAL: Long = 1000
        const val MAX_SENDING_RETRIES = 10
        const val MAX_RETRIES_COMMAND = 5
    }
}