package nl.helico.postgreskt.protocol

import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readBuffer
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readInt
import io.ktor.utils.io.writePacket
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nl.helico.postgreskt.protocol.messages.BackendMessage
import nl.helico.postgreskt.protocol.messages.BackendMessageSerializer
import nl.helico.postgreskt.protocol.messages.Deserializer
import nl.helico.postgreskt.protocol.messages.FrontendMessage
import java.lang.AutoCloseable

interface Protocol {
    val backendChannel: ReceiveChannel<BackendMessage>
    val frontendChannel: SendChannel<FrontendMessage>

    operator fun component1() = frontendChannel

    operator fun component2() = backendChannel
}

fun Protocol(socket: Socket): Protocol =
    ProtocolImpl(
        socket.openReadChannel(),
        socket.openWriteChannel(),
        CoroutineScope(socket.socketContext + CoroutineName("PostgresWireProtocol")),
    )

internal class ProtocolImpl(
    val input: ByteReadChannel,
    val output: ByteWriteChannel,
    val scope: CoroutineScope,
    val deserializer: Deserializer<BackendMessage> = BackendMessageSerializer,
) : Protocol,
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
            println("Sending message: $message")
            val buffer = message.asBuffer()
            byteChannel.writePacket(buffer)
            byteChannel.flush()
        }
    }

    private suspend fun consumeBackendMessages(byteChannel: ByteReadChannel) {
        while (!byteChannel.isClosedForRead) {
            val type = byteChannel.readByte().toInt().toChar()
            val length = byteChannel.readInt()
            val remaining = length - Int.SIZE_BYTES
            val buffer = byteChannel.readBuffer(remaining)

            val msg = deserializer.deserialize(type, buffer)
            println("Received message: $msg")
            backendChannel.send(msg)
        }
    }

    override fun close() {
        frontendChannel.close()
        backendChannel.close()
        runBlocking {
            output.flushAndClose()
        }
    }
}
