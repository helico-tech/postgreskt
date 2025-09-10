package nl.helico.postgreskt.protocol

import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writePacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import nl.helico.postgreskt.protocol.messages.BackendMessage
import nl.helico.postgreskt.protocol.messages.FrontendMessage

interface PostgresWireProtocol {
    val backendChannel: Channel<BackendMessage>
    val frontendChannel: Channel<FrontendMessage>
}

internal class PostgresWireProtocolImpl(
    val socket: Socket,
) : PostgresWireProtocol,
    CoroutineScope by socket,
    AutoCloseable by socket {
    override val backendChannel: Channel<BackendMessage> = Channel(Channel.UNLIMITED)
    override val frontendChannel: Channel<FrontendMessage> = Channel(Channel.UNLIMITED)

    init {
        launch {
            consumeBackendMessages(socket.openReadChannel())
        }

        launch {
            produceFrontendMessages(socket.openWriteChannel())
        }
    }

    private suspend fun consumeBackendMessages(byteChannel: ByteReadChannel) {
    }

    private suspend fun produceFrontendMessages(byteChannel: ByteWriteChannel) {
        for (message in frontendChannel) {
            val buffer = message.asBuffer()
            byteChannel.writePacket(buffer)
            byteChannel.flush()
        }
    }
}
