package nl.helico.postgreskt.protocol.messages

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeString
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.writeString

suspend fun ByteWriteChannel.writeNullByte() {
    writeByte(0)
}

fun Sink.writeNullByte() {
    writeByte(0)
}

suspend fun ByteWriteChannel.writeCString(text: String) {
    writeString(text)
    writeNullByte()
}

fun Sink.writeCString(text: String) {
    writeString(text)
    writeNullByte()
}

fun buffered(body: Buffer.() -> Unit) = Buffer().apply { body() }
