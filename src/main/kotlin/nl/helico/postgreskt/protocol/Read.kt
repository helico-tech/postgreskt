package nl.helico.postgreskt.protocol

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readInt
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import nl.helico.postgreskt.protocol.messages.BackendMessage
import nl.helico.postgreskt.protocol.messages.Message
import nl.helico.postgreskt.protocol.messages.MessageReader

// suspend inline fun <reified T : BackendMessage> ByteReadChannel.expectMessage() {
//    val message = readMessage()
//    check(message is T) { "Expected message to be ${T::class.simpleName}, but was ${message::class.simpleName}" }
// }

/*suspend fun ByteReadChannel.readMessage(): BackendMessage {
    val type = readByte()
    val length = readInt()
    val remaining = length - Int.SIZE_BYTES

    return when (type) {
        AuthenticationMessage.TYPE -> readAuthenticationMessage(remaining)
        else -> throw IllegalArgumentException("Unknown message type: $type")
    }
}

suspend fun ByteReadChannel.readAuthenticationMessage(remaining: Int): AuthenticationMessage =
    when (val authType = readInt()) {
        AuthenticationSASLMessage.AUTH_TYPE -> authenticationSASL(remaining - Int.SIZE_BYTES)
        else -> throw IllegalArgumentException("Unknown authentication type: $authType")
    }

suspend fun ByteReadChannel.authenticationSASL(remaining: Int): AuthenticationSASLMessage {
    val saslMechanism = readRemaining(remaining.toLong()).readByteArray()
    return AuthenticationSASLMessage(saslMechanism.toString(Charsets.UTF_8))
}*/

object MessageReader : MessageReader<Message> {
    override suspend fun ByteReadChannel.invoke(remaining: Int?): Message {
    }
}
