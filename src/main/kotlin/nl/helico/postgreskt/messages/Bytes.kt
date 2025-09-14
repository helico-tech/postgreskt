package nl.helico.postgreskt.messages

import io.ktor.utils.io.core.writePacket
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readString
import kotlinx.io.writeString
import nl.helico.postgreskt_old.protocol.messages.writeNullByte

fun Sink.writeNullByte() {
    writeByte(0)
}

fun Sink.writeCString(text: String) {
    writeString(text)
    writeNullByte()
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

fun buffered(body: Buffer.() -> Unit) = Buffer().apply { body() }

fun Buffer.writeSized(body: Buffer.() -> Unit) {
    val packet = buffered(body)
    val size = packet.size.toInt() + Int.SIZE_BYTES
    writeInt(size)
    writePacket(packet)
}
