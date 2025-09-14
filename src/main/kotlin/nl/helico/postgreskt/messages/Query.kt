package nl.helico.postgreskt.messages

data class ReadyForQuery(
    val status: Char,
) : BackendMessage

fun MessageRegistry.Builder.query() {
    backend('Z') {
        ReadyForQuery(readByte().toInt().toChar())
    }
}
