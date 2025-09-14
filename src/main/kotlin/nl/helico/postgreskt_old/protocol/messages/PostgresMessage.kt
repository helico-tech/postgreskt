package nl.helico.postgreskt_old.protocol.messages

import kotlinx.io.Buffer

sealed interface PostgresMessage

sealed interface FrontendMessage : PostgresMessage {
    fun asBuffer(): Buffer
}

sealed interface BackendMessage : PostgresMessage {
    data class Unhandled(
        val type: Char,
        val body: Buffer,
    ) : BackendMessage
}
