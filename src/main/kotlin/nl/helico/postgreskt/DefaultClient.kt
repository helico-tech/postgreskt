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
import io.ktor.utils.io.writePacket
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.helico.postgreskt.messages.AuthenticationMD5
import nl.helico.postgreskt.messages.BackendMessage
import nl.helico.postgreskt.messages.DefaultMessageRegistry
import nl.helico.postgreskt.messages.ErrorResponse
import nl.helico.postgreskt.messages.FrontendMessage
import nl.helico.postgreskt.messages.MessageRegistry
import nl.helico.postgreskt.messages.NotificationResponse
import nl.helico.postgreskt.messages.PasswordMessage
import nl.helico.postgreskt.messages.ReadyForQuery
import nl.helico.postgreskt.messages.StartupMessage
import nl.helico.postgreskt.messages.Terminate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class DefaultClient(
    val connectionParameters: ConnectionParameters,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + CoroutineName("PostgresClient")),
    private val messageRegistry: MessageRegistry = DefaultMessageRegistry,
) : Client,
    AutoCloseable {
    private val selectorManager = SelectorManager(scope.coroutineContext + Dispatchers.IO + CoroutineName("PostgresSelectorManager"))

    private var currentSocket: Socket? = null
    private var readChannel: ByteReadChannel? = null
    private var writeChannel: ByteWriteChannel? = null

    private val currentState: MutableStateFlow<State> = MutableStateFlow(State.Disconnected)

    override val isConnected: Boolean
        get() = currentState.value == State.ReadyForQuery

    override suspend fun connect() {
        currentSocket = aSocket(selectorManager).tcp().connect(connectionParameters.host, connectionParameters.port)
        readChannel = currentSocket?.openReadChannel()
        writeChannel = currentSocket?.openWriteChannel(autoFlush = true)
        scope.launch { receive() }

        transition(State.Connecting)
        send(
            StartupMessage(
                parameters =
                    mapOf(
                        "user" to connectionParameters.username,
                        "database" to connectionParameters.database,
                    ),
            ),
        )

        waitForState(State.ReadyForQuery)
    }

    override suspend fun disconnect() {
        send(Terminate)
        scope.cancel()
        currentSocket?.close()
        transition(State.Disconnected)
    }

    override fun close() {
        runBlocking { disconnect() }
    }

    private suspend fun handle(message: BackendMessage) {
        if (message is ErrorResponse) throw IllegalStateException("An error response was received: $message")
        if (message is NotificationResponse) {
            println("Notification: $message")
            return
        }

        when (currentState.value) {
            State.Connecting ->
                when (message) {
                    is AuthenticationMD5 ->
                        send(
                            PasswordMessage.md5(connectionParameters.username, connectionParameters.password, message.salt),
                        )

                    is ReadyForQuery -> transition(State.ReadyForQuery)

                    else -> {
                    }
                }
            else -> {}
        }
    }

    private suspend fun transition(newState: State) {
        currentState.emit(newState)
    }

    suspend fun waitForState(
        vararg targetStates: State,
        timeout: Duration = 10.seconds,
    ): State =
        withTimeout(timeout) {
            currentState.first { it in targetStates }
        }

    private suspend fun receive() {
        while (readChannel?.isClosedForRead != true) {
            readChannel?.also {
                val type = it.readByte().toInt().toChar()
                val length = it.readInt()
                val remaining = length - Int.SIZE_BYTES
                val buffer = it.readBuffer(remaining)
                handle(messageRegistry.deserialize(type, buffer))
            }
        }
    }

    private suspend fun send(message: FrontendMessage) {
        writeChannel?.also {
            it.writePacket(messageRegistry.serialize(message))
        }
    }

    sealed interface State {
        data object Disconnected : State

        data object Connecting : State

        data object ReadyForQuery : State
    }
}
