package co.cobli.healthmonitor.model.commands.executors

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.ParcelFileDescriptor
import android.os.PersistableBundle
import android.util.Log
import co.cobli.cameraMessage.protos.IgnitionStatus
import co.cobli.healthmonitor.AppRestartJobService
import co.cobli.healthmonitor.HealthMonitorService.Companion.TAG
import co.cobli.healthmonitor.model.commands.CommandExecutionListener
import co.cobli.healthmonitor.model.commands.CommandExecutor
import co.cobli.healthmonitor.model.database.entities.CommandEntity
import co.cobli.healthmonitor.model.network.s3.S3Client
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

class AppUpdateCmdExecutor(
    command: CommandEntity,
    listener: CommandExecutionListener,
    context: Context,
) : CommandExecutor(command, listener, context), TransferListener {

    private val tag = "[$TAG] ${this.javaClass.name.split(".").last()}"
    private val updateAppIntent = "co.cobli.healthmonitor.UPDATE_APPLICATION"
    private val updateAppCompletedIntent = "co.cobli.healthmonitor.UPDATE_APPLICATION_COMPLETED"

    private lateinit var bucket: String
    private lateinit var fileKey: String
    private lateinit var fileDestination: String
    private lateinit var fileName: String
    private lateinit var s3Client: S3Client
    private lateinit var apkFile: File

    private var isSelfUpdate = true

    override fun parseParameters(): Boolean {
        try {
            if (command.parameters.size == 7) {
                bucket = command.parameters[0]
                fileKey = command.parameters[1]
                fileDestination = command.parameters[2]
                fileName = command.parameters[3]
                isSelfUpdate = command.parameters[4] == "true"
                timeout = command.parameters[5].toLong()
                ignitionStatus = IgnitionStatus.forNumber(command.parameters[6].toInt())
                apkFile = File(fileDestination, fileName)
                shouldWaitNetworkStatus = true
                return true
            }
        } catch (e: Exception) {
            Log.e(tag, "parseParameters error", e)
        }
        return false
    }

    override fun execute() {
        if (isSelfUpdate) {
            selfUpdateApp()
        } else {
            broadcastUpdateApp()
        }
    }

    private fun selfUpdateApp() {
        if (apkFile.exists()) {
            updateApp()
        } else {
            Log.d(tag, "downloading file $fileKey from bucket $bucket to $fileDestination/${fileName}")
            s3Client = S3Client.getClient(context)
            s3Client.download(bucket, fileKey, fileDestination, fileName, this)
        }
    }

    private fun broadcastUpdateApp() {
        Log.d(tag, "sending UPDATE_APPLICATION broadcast")
        val intent = Intent(updateAppIntent)
        intent.putExtra("bucket", bucket)
        intent.putExtra("fileKey", fileKey)
        intent.putExtra("fileDestination", fileDestination)
        intent.putExtra("fileName", fileName)
        intent.putExtra("timeout", timeout)
        intent.putExtra("ignitionStatus", ignitionStatus.number)
        context.sendBroadcast(intent)
        finishExecutor(true, null)
    }

    override fun onStateChanged(id: Int, state: TransferState?) {
        when (state) {
            TransferState.COMPLETED -> updateApp()
            TransferState.FAILED -> finishExecutor(false, "onStateChanged failed")
            else -> {}
        }
    }

    override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
        val percentage = (bytesCurrent.toDouble() / bytesTotal.toDouble()) * 100
        Log.d(tag, "download progress: ${String.format("%.2f", percentage)}%")
    }

    override fun onError(id: Int, e: Exception?) {
        finishExecutor(false, e.toString())
    }

    override fun onTimeout() {
        s3Client.cancelDownloads()
    }

    private fun updateApp() {
        try {
            updateApp(apkFile)
            finishExecutor(true, null)
        } catch (e: Exception) {
            finishExecutor(false, e.toString())
        }
        if (apkFile.exists()) apkFile.delete()
    }

    private fun updateApp(apkFile: File) {
        val packageManager = context.packageManager
        val packageInstaller = packageManager.packageInstaller

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        val outputStream: OutputStream = session.openWrite("update", 0, apkFile.length())
        val inputStream = FileInputStream(apkFile)
        val buffer = ByteArray(65536)
        var bytesRead: Int

        while (inputStream.read(buffer).also { bytesRead = it } >= 0) {
            outputStream.write(buffer, 0, bytesRead)
        }

        session.fsync(outputStream)
        outputStream.close()
        inputStream.close()
        session.commit(PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(updateAppCompletedIntent),
            PendingIntent.FLAG_IMMUTABLE
        ).intentSender)

        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val jobInfo = JobInfo.Builder(1, ComponentName(context, AppRestartJobService::class.java))
            .setMinimumLatency(30000)
            .setOverrideDeadline(60000)
            .build()
        jobScheduler.schedule(jobInfo)
    }
}