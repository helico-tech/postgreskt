package nl.helico.postgreskt.protocol.messages

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel

interface MessageReader<T : Message> {
    suspend operator fun ByteReadChannel.invoke(remaining: Int?): T
}

interface MessageWriter {
    suspend operator fun ByteWriteChannel.invoke()
}

sealed interface Message

sealed interface BackendMessage : Message

sealed interface FrontendMessage : Message
