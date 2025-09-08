package nl.helico.postgreskt.protocol.messages

import io.ktor.utils.io.ByteReadChannel

sealed interface AuthenticationMessage : BackendMessage {
    companion object : MessageReader<AuthenticationMessage> {
        override suspend fun ByteReadChannel.invoke(remaining: Int?): AuthenticationMessage {
            TODO()
        }
    }

    data class SASL(
        val mechanisms: List<String>,
    ) : AuthenticationMessage
}
