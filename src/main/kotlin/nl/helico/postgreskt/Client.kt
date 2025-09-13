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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.io.readString
import kotlinx.io.readUShort
import nl.helico.postgreskt.protocol.Protocol
import nl.helico.postgreskt.protocol.messages.AuthenticationMD5Password
import nl.helico.postgreskt.protocol.messages.AuthenticationOK
import nl.helico.postgreskt.protocol.messages.BackendMessage
import nl.helico.postgreskt.protocol.messages.DataRow
import nl.helico.postgreskt.protocol.messages.ErrorResponse
import nl.helico.postgreskt.protocol.messages.FrontendMessage
import nl.helico.postgreskt.protocol.messages.PasswordMessage
import nl.helico.postgreskt.protocol.messages.Query
import nl.helico.postgreskt.protocol.messages.ReadyForQuery
import nl.helico.postgreskt.protocol.messages.RowDescription
import nl.helico.postgreskt.protocol.messages.StartupMessage
import nl.helico.postgreskt.protocol.messages.padded

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

    suspend fun query(sql: String): QueryResult
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

    private val types = mutableMapOf<Short, String>()

    override suspend fun connect() {
        val socket = socket.connect(parameters.host, parameters.port)
        val protocol = Protocol(socket)

        backend = protocol.backendChannel
        frontend = protocol.frontendChannel

        startup()
        authenticate()
        queryDataTypes()
    }

    override suspend fun query(sql: String): QueryResult {
        val (rowDescription, data) = rawQuery(sql)
        return QueryResult(
            columns =
                rowDescription.fields.map {
                    QueryResult.Column(
                        it.dataTypeObjectId,
                        it.name,
                        types[it.dataTypeObjectId.toShort()] ?: "unknown",
                    )
                },
            data = data.map { QueryResult.Row(it.values) },
        )
    }

    private suspend fun rawQuery(sql: String): Pair<RowDescription, Flow<DataRow>> {
        if (!isReady) awaitReadyForQuery()

        isReady = false
        frontend.send(Query(sql))

        val rowDescription = backend.receive() as RowDescription

        val data =
            flow {
                var isComplete = false
                while (!isComplete) {
                    val msg = backend.receive()
                    if (msg !is DataRow) {
                        isComplete = true
                    } else {
                        emit(msg)
                    }
                }
            }

        return rowDescription to data
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

        awaitReadyForQuery()
    }

    private suspend fun awaitReadyForQuery() {
        while (true) {
            val msg = backend.receive()
            if (msg is ReadyForQuery) {
                isReady = true
                break
            }
        }
    }

    private suspend fun queryDataTypes() {
        val (description, data) = rawQuery("SELECT t.oid, t.typname FROM pg_type t")
        data.collect { row ->
            /*val oid = row.values[0]?.readShort() ?: throw Exception("Could not read oid from row")
            val typname: String = row.values[1]?.readString() ?: throw Exception("Could not read typname from row")
            types[oid] = typname*/

            val padded = row.values[0]?.padded(description.fields[0].dataTypeSize)

            val typName = row.values[1]?.readString() ?: throw Exception("Could not read typname from row")
            // val oid = row.values[0]?() ?: throw Exception("Could not read oid from row")
            // println("$typName: $oid")
        }
    }
}
