@file:Suppress("ktlint:standard:no-wildcard-imports")

package nl.helico.postgreskt

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import nl.helico.postgreskt.messages.*
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class DefaultClient(
    val connectionParameters: ConnectionParameters,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + CoroutineName("PostgresClient")),
    private val messageRegistry: MessageRegistry = DefaultMessageRegistry,
) : Client {
    private val selectorManager = SelectorManager(scope.coroutineContext + Dispatchers.Default + CoroutineName("PostgresSelectorManager"))

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
        waitForState<State.ReadyForQuery>()

        val resultChannel = Channel<DataRow>()
        transition(State.Collecting(resultChannel))
        send(Query(queryString))

        val rowDescription = waitForMessage<RowDescription>()
        return QueryResult(rowDescription, resultChannel.consumeAsFlow())
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun prepare(queryString: String): PreparedStatement {
        waitForState<State.ReadyForQuery>()

        val identifier = Uuid.random().toString()

        send(Parse(name = identifier, queryString))
        send(Describe('S', identifier))
        send(Sync)

        val parameterDescription = waitForMessage<ParameterDescription>()
        val rowDescription = waitForMessage<RowDescription>()

        return PreparedStatement(identifier, queryString, parameterDescription, rowDescription)
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun execute(
        preparedStatement: PreparedStatement,
        values: List<String?>,
    ): QueryResult {
        waitForState<State.ReadyForQuery>()

        val resultChannel = Channel<DataRow>()

        val portalId = Uuid.random().toString()
        transition(State.Collecting(resultChannel))
        send(Bind(portalId, preparedStatement.identifier, values))
        send(Execute(portalId))
        send(Close('P', portalId))
        send(Sync)

        return QueryResult(preparedStatement.rowDescription, resultChannel.consumeAsFlow())
    }

    override suspend fun listen(channel: String): Flow<NotificationResponse> {
        waitForState<State.ReadyForQuery>()

        val resultChannel = Channel<NotificationResponse>()

        transition(State.Listening(resultChannel))

        send(Query("LISTEN $channel"))

        return resultChannel
            .consumeAsFlow()
            .onCompletion {
                send(Query("UNLISTEN $channel"))
                transition(State.ReadyForQuery)
                resultChannel.close()
            }
    }

    override suspend fun notify(
        channel: String,
        payload: String,
    ) {
        send(Query("NOTIFY $channel, '$payload'"))
        waitForMessage<CommandComplete>()
    }

    private suspend fun handle() {
        backendMessages.collect { message ->
            if (message is ErrorResponse) throw IllegalStateException("An error response was received: $message")

            when (val state = currentState.value) {
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

                        else -> unhandled(message)
                    }

                is State.Collecting ->
                    when (message) {
                        is ReadyForQuery -> {
                            state.resultChannel.close()
                            transition(State.ReadyForQuery)
                        }

                        is CommandComplete -> {
                            state.resultChannel.close()
                            transition(State.ReadyForQuery)
                        }

                        is DataRow -> state.resultChannel.send(message)

                        else -> unhandled(message)
                    }

                is State.Listening -> {
                    when (message) {
                        is NotificationResponse -> state.resultChannel.send(message)
                        else -> unhandled(message)
                    }
                }

                else -> unhandled(message)
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

    private fun unhandled(message: Message) {
        println("Unhandled: $message")
    }

    sealed interface State {
        data object Disconnected : State

        data object Connecting : State

        data object ReadyForQuery : State

        data class Collecting(
            val resultChannel: Channel<DataRow>,
        ) : State

        data class Listening(
            val resultChannel: Channel<NotificationResponse>,
        ) : State
    }
}
