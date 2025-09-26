package nl.helico.postgreskt

import kotlinx.coroutines.flow.Flow
import nl.helico.postgreskt.messages.NotificationResponse

interface Client {
    val isConnected: Boolean

    suspend fun connect()

    suspend fun disconnect()

    suspend fun query(queryString: String): QueryResult

    suspend fun prepare(queryString: String): PreparedStatement

    suspend fun execute(
        preparedStatement: PreparedStatement,
        values: List<String?> = emptyList(),
    ): QueryResult

    suspend fun listen(channel: String): Flow<NotificationResponse>

    suspend fun notify(
        channel: String,
        payload: String,
    )

    companion object {
        operator fun invoke(connectionParameters: ConnectionParameters): Client = DefaultClient(connectionParameters)
    }
}
