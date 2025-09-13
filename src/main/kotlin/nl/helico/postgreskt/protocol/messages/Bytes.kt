package nl.helico.postgreskt.protocol.messages

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.core.writePacket
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeString
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readString
import kotlinx.io.writeString

suspend fun ByteWriteChannel.writeNullByte() {
    writeByte(0)
}

fun Sink.writeNullByte() {
    writeByte(0)
}

fun Source.readCString(): String {
    val buffer = Buffer()
    var byte = readByte()
    while (byte != 0.toByte()) {
        buffer.writeByte(byte)
        byte = readByte()
    }
    return buffer.readString(buffer.size)
}

suspend fun ByteWriteChannel.writeCString(text: String) {
    writeString(text)
    writeNullByte()
}

fun Sink.writeCString(text: String) {
    writeString(text)
    writeNullByte()
}

fun buildFrontendMessage(
    type: Char,
    body: Buffer.() -> Unit,
): Buffer {
    val bodyBuffer = buffered(body)
    val size = bodyBuffer.size.toInt() + Int.SIZE_BYTES
    return buffered {
        writeByte(type.code.toByte())
        writeInt(size)
        writePacket(bodyBuffer)
    }
}

fun Buffer.padded(size: Short): Buffer {
    val padded = Buffer()
    while (padded.size < size - this.size) padded.writeByte(0)
    padded.writePacket(this)
    return padded
}

fun buffered(body: Buffer.() -> Unit) = Buffer().apply { body() }
