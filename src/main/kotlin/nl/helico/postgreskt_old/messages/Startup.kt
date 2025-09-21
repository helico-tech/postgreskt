package nl.helico.postgreskt_old.messages

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
