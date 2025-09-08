package nl.helico.postgreskt.protocol

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeString

const val NULL_BYTE = 0.toByte()

suspend fun ByteWriteChannel.writeTerminator() {
    writeByte(NULL_BYTE)
}

suspend fun ByteWriteChannel.writeTerminatedString(string: String) {
    writeString(string)
    writeTerminator()
}
