package nl.helico.postgreskt.messages

data class BackendKeyData(
    val processId: Int,
    val secretKey: Int,
) : BackendMessage

fun MessageRegistry.Builder.backendKeyData() {
    backend('K') {
        BackendKeyData(readInt(), readInt())
    }
}
