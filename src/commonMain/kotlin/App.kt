@file:Suppress("ktlint:standard:no-wildcard-imports")

import kotlinx.coroutines.*
import kotlinx.io.readString
import nl.helico.postgreskt.ConnectionParameters
import nl.helico.postgreskt.DefaultClient

expect fun suspendingScope(block: suspend () -> Unit)

fun main() {
    suspendingScope {
        val client =
            DefaultClient(
                connectionParameters =
                    ConnectionParameters(
                        host = "localhost",
                        port = 5432,
                        username = "postgres",
                        password = "postgres",
                        database = "postgres",
                    ),
            )

        client.connect()

        val query =
            """
            SELECT
                id,
                FLOOR(RANDOM() * 100) AS random_number,
                MD5(RANDOM()::TEXT) AS random_string
            FROM
                generate_series(1, 10) AS id;
            """.trimIndent()

        val (metadata, data) = client.query(query)
        data.collect { row ->
            metadata.fields.zip(row.fields).forEach { (field, value) ->
                println("${field.field}: ${value?.readString()}")
            }
            println()
        }

        client.disconnect()
    }
}
