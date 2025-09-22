package nl.helico.postgreskt

import nl.helico.postgreskt.QueryResult
import org.intellij.lang.annotations.Language

interface Client {
    val isConnected: Boolean

    suspend fun connect()

    suspend fun disconnect()

    suspend fun query(
        @Language("sql") queryString: String,
    ): QueryResult

    suspend fun prepare(
        @Language("sql") queryString: String,
    ): PreparedStatement

    suspend fun execute(
        preparedStatement: PreparedStatement,
        values: List<String?> = emptyList(),
    ): QueryResult

    companion object {
        operator fun invoke(connectionParameters: ConnectionParameters): Client = DefaultClient(connectionParameters)
    }
}
