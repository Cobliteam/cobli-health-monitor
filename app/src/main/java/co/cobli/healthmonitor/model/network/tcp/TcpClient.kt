package co.cobli.healthmonitor.model.network.tcp

import android.util.Log
import co.cobli.healthmonitor.HealthMonitorService.Companion.TAG
import co.cobli.healthmonitor.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.Socket

class TcpClient(
    private val tcpListener: Listener,
    private val serverDNS: String,
    private val serverPort: Int,
    private val listenInterval: Long,
) {

    private val tag = "[$TAG] ${this.javaClass.name.split(".").last()}"
    private var socket: Socket? = null
    private var isStarted: Boolean = false

    suspend fun send(message: ByteArray): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (isConnected()) {
                    Log.d(tag, "Sending message ${Utils.toHexString(message)}")
                    socket?.outputStream?.run {
                        write(message)
                        flush()
                        Log.d(tag, "Successfully sent message to server")
                        return@withContext true
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error sending message to server", e)
            }
            return@withContext false
        }
    }

    suspend fun start() {
        Log.d(tag, "Starting TCP client")
        withContext(Dispatchers.IO) {
            isStarted = true
            connect()
            while (isStarted && isConnected()) {
                listen()
                delay(listenInterval)
            }
            Log.d(tag, "TCP client stopped")
        }
    }

    fun stop() {
        Log.d(tag, "Stopping TCP client")
        isStarted = false
        disconnect()
    }

    private fun connect() {
        try {
            Log.d(tag, "Connecting to $serverDNS:$serverPort")
            val serverAddress = InetAddress.getByName(serverDNS)
            socket = Socket(serverAddress, serverPort)
            Log.d(tag, "Successfully connected to $serverDNS:$serverPort")
        } catch (e: Exception) {
            Log.e(tag, "Error while connecting to $serverDNS:$serverPort", e)
        }
    }

    private fun disconnect() {
        try {
            Log.d(tag, "Disconnecting from $serverDNS:$serverPort")
            socket?.close().also {
                socket = null
            }
        } catch (e: Exception) {
            Log.e(tag, "Error while disconnecting from $serverDNS:$serverPort", e)
        }
    }

    private fun listen() {
        try {
            val buffer = ByteArray(1024)
            val byteArrayOutputStream = ByteArrayOutputStream()
            val bytesRead = socket?.inputStream?.read(buffer)

            bytesRead?.let {
                byteArrayOutputStream.write(buffer, 0, bytesRead)
                val message = byteArrayOutputStream.toByteArray()
                Log.d(tag, "Received bytes ${Utils.toHexString(message)}")
                tcpListener.onMessageReceived(message)
            }

            byteArrayOutputStream.close()
        } catch (e: Exception) {
            Log.e(tag, "Error while listening to server", e)
            disconnect()
        }
    }

    private fun isConnected() = socket?.isConnected == true

    interface Listener {
        fun onMessageReceived(message: ByteArray)
    }
}