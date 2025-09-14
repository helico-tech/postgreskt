package nl.helico.postgreskt_old.protocol.messages

import io.ktor.utils.io.core.writePacket
import kotlinx.io.Buffer

data class StartupMessage(
    val protocolMajorVersion: Int = 3,
    val protocolMinorVersion: Int = 0,
    val parameters: Map<String, String>,
) : FrontendMessage {
    override fun asBuffer(): Buffer =
        buffered {
            val body =
                buffered {
                    writeInt(protocolMajorVersion shl Short.SIZE_BITS or protocolMinorVersion)
                    parameters.forEach { (key, value) ->
                        writeCString(key)
                        writeCString(value)
                    }
                    writeNullByte()
                }

            val size = body.size.toInt() + Int.SIZE_BYTES
            writeInt(size)
            writePacket(body)
        }
}

data class ReadyForQuery(
    val status: Char,
) : BackendMessage {
    companion object : Deserializer<ReadyForQuery> {
        override fun deserialize(
            type: Char,
            buffer: Buffer,
        ): ReadyForQuery = ReadyForQuery(buffer.readByte().toInt().toChar())
    }
}
