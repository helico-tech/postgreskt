package nl.helico.postgreskt.protocol.messages

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeInt
import nl.helico.postgreskt.protocol.NULL_BYTE
import nl.helico.postgreskt.protocol.writeTerminatedString
import nl.helico.postgreskt.protocol.writeTerminator

data class StartupMessage(
    val protocolMajorVersion: Int = 3,
    val protocolMinorVersion: Int = 0,
    val parameters: Map<String, String>,
) : FrontendMessage,
    MessageWriter {
    override suspend fun ByteWriteChannel.invoke() {
        writeInt(protocolMajorVersion shl Short.SIZE_BITS or protocolMinorVersion)
        parameters.forEach { (key, value) ->
            writeTerminatedString(key)
            writeTerminatedString(value)
        }
        writeTerminator()
    }
}
