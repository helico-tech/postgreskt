package nl.helico.postgreskt

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.util.Digest
import jdk.jfr.internal.consumer.EventLog.update
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import nl.helico.postgreskt.protocol.Protocol
import nl.helico.postgreskt.protocol.messages.AuthenticationMD5Password
import nl.helico.postgreskt.protocol.messages.AuthenticationOK
import nl.helico.postgreskt.protocol.messages.BackendMessage
import nl.helico.postgreskt.protocol.messages.ErrorResponse
import nl.helico.postgreskt.protocol.messages.FrontendMessage
import nl.helico.postgreskt.protocol.messages.PasswordMessage
import nl.helico.postgreskt.protocol.messages.Query
import nl.helico.postgreskt.protocol.messages.ReadyForQuery
import nl.helico.postgreskt.protocol.messages.StartupMessage

interface Client {
    data class Parameters(
        val host: String,
        val port: Int,
        val database: String,
        val username: String,
        val password: String,
    )

    val isConnected: Boolean
    val isReady: Boolean

    suspend fun connect()

    suspend fun query(sql: String)
}

fun Client(
    host: String,
    port: Int,
    database: String,
    username: String,
    password: String,
): Client {
    val scope = CoroutineScope(Dispatchers.IO + CoroutineName("PostgresClient"))
    return ClientImpl(
        parameters = Client.Parameters(host, port, database, username, password),
        scope = scope,
    )
}

class ClientImpl(
    val parameters: Client.Parameters,
    val scope: CoroutineScope,
) : Client {
    val selectorManager = SelectorManager(Dispatchers.IO)
    val socket = aSocket(selectorManager).tcp()

    override val isConnected: Boolean get() = ::backend.isInitialized && ::frontend.isInitialized
    override var isReady: Boolean = false
        private set

    private lateinit var backend: ReceiveChannel<BackendMessage>
    private lateinit var frontend: SendChannel<FrontendMessage>

    override suspend fun connect() {
        val socket = socket.connect(parameters.host, parameters.port)
        val protocol = Protocol(socket)

        backend = protocol.backendChannel
        frontend = protocol.frontendChannel

        startup()
        authenticate()
    }

    override suspend fun query(sql: String) {
        frontend.send(Query(sql))
        while (true) {
            val msg = backend.receive()
        }
    }

    private suspend fun startup() {
        frontend.send(StartupMessage(parameters = mapOf("user" to parameters.username, "database" to parameters.database)))
    }

    private suspend fun authenticate() {
        when (val msg = backend.receive()) {
            is AuthenticationMD5Password -> frontend.send(PasswordMessage.md5(parameters.username, parameters.password, msg.salt))
            else -> println(msg)
        }

        backend.receive().also { if (it is ErrorResponse) throw Exception(it.toString()) }

        // now it is time to wait for ReadyForQuery
        while (true) {
            val msg = backend.receive()
            if (msg is ReadyForQuery) {
                isReady = true
                break
            }
        }
    }
}
