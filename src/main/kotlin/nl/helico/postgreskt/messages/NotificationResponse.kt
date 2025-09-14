package nl.helico.postgreskt.messages

data class NotificationResponse(
    val backendPID: Int,
    val channel: String,
    val payload: String,
) : BackendMessage

fun MessageRegistry.Builder.notificationResponse() {
    backend('A') {
        NotificationResponse(readInt(), readCString(), readCString())
    }
}
