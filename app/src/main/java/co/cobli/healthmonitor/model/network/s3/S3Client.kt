package co.cobli.healthmonitor.model.network.s3

import android.content.Context
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferType
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import java.io.File

class S3Client(
    private val transferUtility: TransferUtility
) {
    companion object {
        fun getClient(context: Context): S3Client {
            val credentials = BasicAWSCredentials("ACCESS_KEY", "SECRET_KEY")
            val client = AmazonS3Client(credentials)
            client.setRegion(Region.getRegion(Regions.US_EAST_1))
            val transferUtility = TransferUtility.builder()
                .context(context)
                .s3Client(client)
                .build()
            return S3Client(transferUtility)
        }
    }

    fun download(bucket: String, fileKey: String, fileDestination: String, fileName: String, listener: TransferListener) {
        val file = File(fileDestination, fileName)
        val downloadObserver = transferUtility.download(bucket, fileKey, file)
        downloadObserver.setTransferListener(listener)
    }

    fun cancelDownloads() {
        transferUtility.cancelAllWithType(TransferType.DOWNLOAD)
    }
}