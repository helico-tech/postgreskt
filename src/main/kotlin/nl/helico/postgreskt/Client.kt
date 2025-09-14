package nl.helico.postgreskt

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readBuffer
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readInt
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.helico.postgreskt.messages.DefaultMessageRegistry
import nl.helico.postgreskt.messages.FrontendMessage
import nl.helico.postgreskt.messages.MessageRegistry

class Client(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    private val messageRegistry: MessageRegistry = DefaultMessageRegistry,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + CoroutineName("PostgresClient")),
) {
    private val selectorManager = SelectorManager(scope.coroutineContext + Dispatchers.IO + CoroutineName("PostgresSelectorManager"))

    private var currentSocket: Socket? = null
    private var readChannel: ByteReadChannel? = null
    private var writeChannel: ByteWriteChannel? = null

    private val stateMachine =
        StateMachine(
            initialState = Disconnected,
            send = ::send,
        )

    suspend fun connect() {
        currentSocket = aSocket(selectorManager).tcp().connect(host, port)
        readChannel = currentSocket?.openReadChannel()
        writeChannel = currentSocket?.openWriteChannel(autoFlush = true)

        scope.launch { receive() }
    }

    private suspend fun receive() {
        while (readChannel?.isClosedForRead != true) {
            readChannel?.also {
                val type = it.readByte().toInt().toChar()
                val length = it.readInt()
                val remaining = length - Int.SIZE_BYTES
                val buffer = it.readBuffer(remaining)
                stateMachine.handle(messageRegistry.deserialize(type, buffer))
            }
        }
    }

    private suspend fun send(message: FrontendMessage) {}
}
