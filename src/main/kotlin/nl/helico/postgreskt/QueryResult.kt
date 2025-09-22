package nl.helico.postgreskt

import kotlinx.coroutines.flow.Flow
import nl.helico.postgreskt.messages.DataRow
import nl.helico.postgreskt.messages.RowDescription

data class QueryResult(
    val rowDescription: RowDescription,
    val data: Flow<DataRow>,
)
