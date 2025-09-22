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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.helico.postgreskt.QueryResult
import nl.helico.postgreskt.messages.AuthenticationMD5
import nl.helico.postgreskt.messages.BackendMessage
import nl.helico.postgreskt.messages.DataRow
import nl.helico.postgreskt.messages.DefaultMessageRegistry
import nl.helico.postgreskt.messages.ErrorResponse
import nl.helico.postgreskt.messages.FrontendMessage
import nl.helico.postgreskt.messages.MessageRegistry
import nl.helico.postgreskt.messages.NotificationResponse
import nl.helico.postgreskt.messages.PasswordMessage
import nl.helico.postgreskt.messages.Query
import nl.helico.postgreskt.messages.ReadyForQuery
import nl.helico.postgreskt.messages.RowDescription
import nl.helico.postgreskt.messages.StartupMessage
import nl.helico.postgreskt.messages.Terminate
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class DefaultClient(
    val connectionParameters: ConnectionParameters,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + CoroutineName("PostgresClient")),
    private val messageRegistry: MessageRegistry = DefaultMessageRegistry,
) : Client {
    private val selectorManager = SelectorManager(scope.coroutineContext + Dispatchers.IO + CoroutineName("PostgresSelectorManager"))

    private var currentSocket: Socket? = null
    private var readChannel: ByteReadChannel? = null
    private var writeChannel: ByteWriteChannel? = null

    private val currentState: MutableStateFlow<State> = MutableStateFlow(State.Disconnected)
    private val backendMessages: MutableSharedFlow<BackendMessage> = MutableSharedFlow()

    override val isConnected: Boolean
        get() = currentState.value == State.ReadyForQuery

    override suspend fun connect() {
        currentSocket = aSocket(selectorManager).tcp().connect(connectionParameters.host, connectionParameters.port)
        readChannel = currentSocket?.openReadChannel()
        writeChannel = currentSocket?.openWriteChannel(autoFlush = true)

        scope.launch { handle() }
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

        waitForState<State.ReadyForQuery>()
    }

    override suspend fun disconnect() {
        send(Terminate)
        scope.cancel()
        currentSocket?.close()
        transition(State.Disconnected)
    }

    override suspend fun query(queryString: String): QueryResult {
        val resultChannel = Channel<DataRow>()
        transition(State.SimpleQuery(resultChannel))
        send(Query(queryString))

        val rowDescription = waitForMessage<RowDescription>()
        return QueryResult(rowDescription, resultChannel.consumeAsFlow())
    }

    private suspend fun handle() {
        backendMessages.collect { message ->
            if (message is ErrorResponse) throw IllegalStateException("An error response was received: $message")
            if (message is NotificationResponse) {
                println("Notification: $message")
                return@collect
            }

            when (val stata = currentState.value) {
                State.Connecting ->
                    when (message) {
                        is AuthenticationMD5 ->
                            send(
                                PasswordMessage.md5(
                                    connectionParameters.username,
                                    connectionParameters.password,
                                    message.salt,
                                ),
                            )

                        is ReadyForQuery -> transition(State.ReadyForQuery)

                        else -> {}
                    }

                is State.SimpleQuery ->
                    when (message) {
                        is ReadyForQuery -> {
                            stata.resultChannel.close()
                            transition(State.ReadyForQuery)
                        }

                        is DataRow -> stata.resultChannel.send(message)

                        else -> {}
                    }
                else -> {}
            }
        }
    }

    private suspend fun transition(newState: State) {
        currentState.emit(newState)
    }

    suspend fun <T : State> waitForState(
        stateClass: KClass<T>,
        timeout: Duration = 10.seconds,
    ): State =
        withTimeout(timeout) {
            currentState.filterIsInstance(stateClass).first()
        }

    suspend fun <T : BackendMessage> waitForMessage(
        messageClass: KClass<T>,
        timeout: Duration = 10.seconds,
    ): T =
        withTimeout(timeout) {
            backendMessages.filterIsInstance(messageClass).first()
        }

    suspend inline fun <reified T : State> waitForState(timeout: Duration = 10.seconds): State = waitForState(T::class, timeout)

    suspend inline fun <reified T : BackendMessage> waitForMessage(timeout: Duration = 10.seconds): T = waitForMessage(T::class, timeout)

    private suspend fun receive() {
        while (readChannel?.isClosedForRead != true) {
            readChannel?.also {
                val type = it.readByte().toInt().toChar()
                val length = it.readInt()
                val remaining = length - Int.SIZE_BYTES
                val buffer = it.readBuffer(remaining)
                val msg = messageRegistry.deserialize(type, buffer)
                backendMessages.emit(msg)
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

        data class SimpleQuery(
            val resultChannel: Channel<DataRow>,
        ) : State
    }
}
