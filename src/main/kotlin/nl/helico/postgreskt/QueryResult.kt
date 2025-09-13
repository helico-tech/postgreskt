package nl.helico.postgreskt

import kotlinx.coroutines.flow.Flow
import kotlinx.io.Buffer

data class QueryResult(
    val columns: List<Column>,
    val data: Flow<Row>,
) {
    data class Column(
        val oid: Int,
        val name: String,
        val type: String,
    )

    data class Row(
        val values: List<Buffer?>,
    )
}
