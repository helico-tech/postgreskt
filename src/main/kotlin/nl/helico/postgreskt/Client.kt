package nl.helico.postgreskt

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.util.AttributeKey
import io.ktor.util.Attributes
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readBuffer
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readInt
import io.ktor.utils.io.writePacket
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import nl.helico.postgreskt.messages.DataRow
import nl.helico.postgreskt.messages.DefaultMessageRegistry
import nl.helico.postgreskt.messages.FrontendMessage
import nl.helico.postgreskt.messages.MessageRegistry
import nl.helico.postgreskt.messages.Query
import nl.helico.postgreskt.messages.RowDescription
import nl.helico.postgreskt.messages.StartupMessage
import nl.helico.postgreskt.messages.Terminate
import nl.helico.postgreskt.states.Disconnected
import nl.helico.postgreskt.states.ReadyForQuery
import nl.helico.postgreskt.states.StateMachine
import org.intellij.lang.annotations.Language

data class ConnectionParameters(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
)

data class Result(
    val rowDescription: RowDescription,
    val data: Flow<DataRow>,
)

val ConnectionParametersKey = AttributeKey<ConnectionParameters>("ConnectionParameters")

class Client(
    val parameters: ConnectionParameters,
    private val messageRegistry: MessageRegistry = DefaultMessageRegistry,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + CoroutineName("PostgresClient")),
) {
    constructor(host: String, port: Int, username: String, database: String, password: String) : this(
        parameters = ConnectionParameters(host, port, username, database, password),
    )

    private val selectorManager = SelectorManager(scope.coroutineContext + Dispatchers.IO + CoroutineName("PostgresSelectorManager"))

    private var currentSocket: Socket? = null
    private var readChannel: ByteReadChannel? = null
    private var writeChannel: ByteWriteChannel? = null

    private val context =
        Attributes().apply {
            put(ConnectionParametersKey, parameters)
        }

    private val stateMachine =
        StateMachine(
            initialState = Disconnected,
            send = ::send,
            onStateChanged = { old, new -> println("State changed from $old to $new") },
            context = context,
        )

    suspend fun connect() {
        currentSocket = aSocket(selectorManager).tcp().connect(parameters.host, parameters.port)
        readChannel = currentSocket?.openReadChannel()
        writeChannel = currentSocket?.openWriteChannel(autoFlush = true)

        scope.launch { receive() }

        stateMachine.handle(
            StartupMessage(
                parameters =
                    mapOf(
                        "user" to parameters.username,
                        "database" to parameters.database,
                    ),
            ),
        )

        stateMachine.waitForState(ReadyForQuery)
    }

    suspend fun disconnect() {
        stateMachine.handle(Terminate)
        scope.cancel()
        currentSocket?.close()
    }

    suspend fun query(
        @Language("sql") query: String,
    ): Result {
        val (channel, rowDescription) =
            coroutineScope {
                val rowDescription = async { stateMachine.waitForMessage<RowDescription>() }
                val channel = Channel<DataRow>()
                stateMachine.handle(Query(query, channel))

                channel to rowDescription.await()
            }

        return Result(rowDescription, channel.consumeAsFlow())
    }

    private suspend fun receive() {
        while (readChannel?.isClosedForRead != true) {
            readChannel?.also {
                val type = it.readByte().toInt().toChar()
                val length = it.readInt()
                val remaining = length - Int.SIZE_BYTES
                val buffer = it.readBuffer(remaining)

                val msg = messageRegistry.deserialize(type, buffer)
                println("Received message: $msg")
                stateMachine.handle(msg)
            }
        }
    }

    private suspend fun send(message: FrontendMessage) {
        writeChannel?.also {
            println("Sending message: $message")
            it.writePacket(messageRegistry.serialize(message))
        }
    }
}
