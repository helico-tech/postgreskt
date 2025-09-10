package nl.helico.postgreskt.protocol.messages

import kotlinx.io.Buffer

sealed interface PostgresMessage

sealed interface FrontendMessage : PostgresMessage {
    fun asBuffer(): Buffer
}

sealed interface BackendMessage : PostgresMessage
