package nl.helico.postgreskt.messages

import kotlinx.io.Buffer

sealed interface Message

sealed interface FrontendMessage : Message

sealed interface BackendMessage : Message {
    data class Unhandled(
        val type: Char,
        val body: Buffer,
    ) : BackendMessage
}
