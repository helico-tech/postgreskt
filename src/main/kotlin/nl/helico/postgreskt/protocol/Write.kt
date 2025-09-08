package nl.helico.postgreskt.protocol

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeInt
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import nl.helico.postgreskt.protocol.messages.FrontendMessage

suspend fun <T : FrontendMessage> ByteWriteChannel.writeMessage(message: T) {
    val buffer = Buffer()
    message.write(buffer)

    var messageLength = buffer.size.toInt()
    messageLength += Int.SIZE_BYTES // length of self

    if (message.type != NULL_BYTE) {
        writeByte(message.type)
    }

    writeInt(messageLength)
    writeFully(buffer.readByteArray())
    writeByte(0)
    flush()
}
