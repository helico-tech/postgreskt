package nl.helico.postgreskt.messages

import nl.helico.postgreskt_old.protocol.messages.writeCString
import nl.helico.postgreskt_old.protocol.messages.writeNullByte
import kotlin.collections.component1
import kotlin.collections.component2

data class StartupMessage(
    val protocolMajorVersion: Int = 3,
    val protocolMinorVersion: Int = 0,
    val parameters: Map<String, String>,
) : FrontendMessage

fun MessageRegistry.Builder.startupMessage() {
    frontend<StartupMessage> { message ->
        writeSized {
            writeInt(message.protocolMajorVersion shl Short.SIZE_BITS or message.protocolMinorVersion)
            message.parameters.forEach { (key, value) ->
                writeCString(key)
                writeCString(value)
            }
            writeNullByte()
        }
    }
}
