package nl.helico.postgreskt.protocol

import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writePacket
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nl.helico.postgreskt.protocol.messages.BackendMessage
import nl.helico.postgreskt.protocol.messages.FrontendMessage
import java.lang.AutoCloseable

interface PostgresWireProtocol {
    val backendChannel: ReceiveChannel<BackendMessage>
    val frontendChannel: SendChannel<FrontendMessage>
}

fun PostgresWireProtocol(socket: Socket): PostgresWireProtocol =
    PostgresWireProtocolImpl(
        socket.openReadChannel(),
        socket.openWriteChannel(),
        CoroutineScope(socket.socketContext + CoroutineName("PostgresWireProtocol")),
    )

internal class PostgresWireProtocolImpl(
    val input: ByteReadChannel,
    val output: ByteWriteChannel,
    val scope: CoroutineScope,
) : PostgresWireProtocol,
    AutoCloseable {
    override val frontendChannel: Channel<FrontendMessage> = Channel(Channel.UNLIMITED)
    override val backendChannel: Channel<BackendMessage> = Channel(Channel.UNLIMITED)

    init {
        scope.launch {
            consumeBackendMessages(input)
        }

        scope.launch {
            produceFrontendMessages(output)
        }
    }

    private suspend fun produceFrontendMessages(byteChannel: ByteWriteChannel) {
        for (message in frontendChannel) {
            val buffer = message.asBuffer()
            byteChannel.writePacket(buffer)
            byteChannel.flush()
        }
    }

    private suspend fun consumeBackendMessages(byteChannel: ByteReadChannel) {
    }

    override fun close() {
        frontendChannel.close()
        backendChannel.close()
        runBlocking {
            output.flushAndClose()
        }
    }
}
