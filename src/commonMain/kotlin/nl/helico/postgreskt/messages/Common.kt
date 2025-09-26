package nl.helico.postgreskt.messages

data class NotificationResponse(
    val backendPID: Int,
    val channel: String,
    val payload: String,
) : BackendMessage

data class ErrorResponse(
    val fields: List<Field>,
) : BackendMessage {
    data class Field(
        val type: Char,
        val value: String,
    )
}

data object Sync : FrontendMessage

fun MessageRegistry.Builder.commonResponses() {
    backend('A') {
        NotificationResponse(readInt(), readCString(), readCString())
    }

    backend('E') {
        var fields = mutableListOf<ErrorResponse.Field>()
        while (peek().readByte() != 0.toByte()) {
            fields.add(ErrorResponse.Field(readByte().toInt().toChar(), readCString()))
        }
        ErrorResponse(fields)
    }

    frontend<Sync> {
        writeSized('C') {}
    }
}
