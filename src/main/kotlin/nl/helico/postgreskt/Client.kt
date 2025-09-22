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

    companion object {
        operator fun invoke(connectionParameters: ConnectionParameters): Client = DefaultClient(connectionParameters)
    }
}
