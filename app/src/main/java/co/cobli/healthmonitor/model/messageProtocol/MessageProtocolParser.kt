package co.cobli.healthmonitor.model.messageProtocol

import co.cobli.cameraMessage.protos.AckMessagePB
import co.cobli.cameraMessage.protos.CommandMessagePB
import co.cobli.cameraMessage.protos.HealthMessagePB
import co.cobli.cameraMessage.protos.MessagePB
import co.cobli.cameraMessage.protos.MessageTypePB
import co.cobli.cameraMessage.protos.SettingsMessagePB
import co.cobli.healthmonitor.Utils
import co.cobli.healthmonitor.model.database.entities.HealthDataEntity
import com.google.protobuf.kotlin.toByteString
import java.util.zip.CRC32

object MessageProtocolParser {
    const val PROTOCOL_VERSION = 1
    const val LENGTH_PACKET_SIZE = 2
    const val CRC32_PACKET_SIZE = 4
    val START_PACKET = byteArrayOf(0xC0.toByte(), 0xB1.toByte())
    val STOP_PACKET = byteArrayOf(0xC0.toByte(), 0xB1.toByte())
    val START_PACKET_HEX = Utils.toHexString(START_PACKET)
    val STOP_PACKET_HEX = Utils.toHexString(STOP_PACKET)
    val MIN_MESSAGE_SIZE = START_PACKET.size + STOP_PACKET.size + LENGTH_PACKET_SIZE + CRC32_PACKET_SIZE + 1

    fun encodeHealthMessage(healthData: HealthDataEntity, deviceId: Long): ByteArray {
        val healthMessages = HealthMessagePB.newBuilder().apply {
            addAllHealthData(listOf(healthData.toPB()))
        }.build()
        val messagePB = encodeMessagePB(
            deviceId,
            healthData.id,
            healthMessages.toByteArray(),
            MessageTypePB.HEALTH_MESSAGE
        )
        return encodeMessage(messagePB)
    }

    private fun encodeMessagePB(
        deviceId: Long,
        sequence: Long,
        message: ByteArray,
        messageTypePB: MessageTypePB
    ): MessagePB {
        return MessagePB.newBuilder().apply {
            this.protocolVersion = PROTOCOL_VERSION
            this.sequence = sequence
            this.deviceId = deviceId
            this.messageType = messageTypePB
            this.message = message.toByteString()
        }.build()
    }

    private fun encodeMessage(message: MessagePB): ByteArray {
        val messageBytes = message.toByteArray()
        return (START_PACKET
                + encodeLengthPacket(messageBytes)
                + messageBytes
                + encodeCRC32Packet(messageBytes)
                + STOP_PACKET)
    }

    private fun encodeLengthPacket(data: ByteArray): ByteArray {
        val size = data.size
        return byteArrayOf(
            (size shr 8 and 0xFF).toByte(),
            (size and 0xFF).toByte()
        )
    }

    private fun encodeCRC32Packet(data: ByteArray): ByteArray {
        val crc = CRC32()
        crc.update(data)
        val checksum = crc.value

        return byteArrayOf(
            (checksum shr 24 and 0xFF).toByte(),
            (checksum shr 16 and 0xFF).toByte(),
            (checksum shr 8 and 0xFF).toByte(),
            (checksum and 0xFF).toByte()
        )
    }

    fun findValidMessages(messages: ByteArray): List<ByteArray> {
        val validMessages = mutableListOf<ByteArray>()
        val regex = Regex("""($START_PACKET_HEX)(.{4})(.+?)(.{8})($STOP_PACKET_HEX)""")
        val matchResults = regex.findAll(Utils.toHexString(messages))

        validMessages.addAll(
            matchResults.filter { isMessageValid(it) }
                .map { Utils.toByteArray(it.value) }
        )

        return validMessages
    }

    fun decodeMessage(message: ByteArray): MessagePB {
        val messageBytes = getMessagePacket(message)
        return MessagePB.parser().parseFrom(messageBytes)
    }

    fun decodeAckMessage(messagePB: MessagePB): AckMessagePB {
        return AckMessagePB.parser().parseFrom(messagePB.message)
    }

    fun decodeSettingsMessage(messagePB: MessagePB): SettingsMessagePB {
        return SettingsMessagePB.parser().parseFrom(messagePB.message)
    }

    fun decodeCommandMessage(messagePB: MessagePB): CommandMessagePB {
        return CommandMessagePB.parser().parseFrom(messagePB.message)
    }

    private fun decodeLengthPacket(lengthPacket: ByteArray): Int {
        return ((lengthPacket[0].toInt() and 0xFF) shl 8
                or (lengthPacket[1].toInt() and 0xFF))
    }

    private fun isMessageValid(matchResult: MatchResult): Boolean {
        val startBytes = matchResult.groups[1]?.value?.let { Utils.toByteArray(it) }
        val lengthBytes = matchResult.groups[2]?.value?.let { Utils.toByteArray(it) }
        val messageBytes = matchResult.groups[3]?.value?.let { Utils.toByteArray(it) }
        val checksumBytes = matchResult.groups[4]?.value?.let { Utils.toByteArray(it) }
        val stopBytes = matchResult.groups[5]?.value?.let { Utils.toByteArray(it) }

        return matchResult.value.length >= MIN_MESSAGE_SIZE * 2
                && lengthBytes != null && messageBytes != null && checksumBytes != null
                && isStartPacketValid(startBytes)
                && isStopPacketValid(stopBytes)
                && isLengthPacketValid(lengthBytes, messageBytes)
                && isCRC32PacketValid(checksumBytes, messageBytes)
    }

    private fun isStartPacketValid(startPacket: ByteArray?) =
        startPacket.contentEquals(START_PACKET)

    private fun isStopPacketValid(stopPacket: ByteArray?) =
        stopPacket.contentEquals(STOP_PACKET)

    private fun isLengthPacketValid(lengthPacket: ByteArray, messagePacket: ByteArray) =
        messagePacket.size == decodeLengthPacket(lengthPacket)

    private fun isCRC32PacketValid(crc32Packet: ByteArray, messagePacket: ByteArray) =
        crc32Packet.contentEquals(encodeCRC32Packet(messagePacket))

    private fun getMessagePacket(message: ByteArray): ByteArray {
        val startIndex = START_PACKET.size + LENGTH_PACKET_SIZE
        val endIndex = message.size - STOP_PACKET.size - CRC32_PACKET_SIZE
        return message.sliceArray(startIndex until endIndex)
    }
}