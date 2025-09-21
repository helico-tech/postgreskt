import kotlinx.io.readString
import nl.helico.postgreskt_old.Client

suspend fun main() {
    val client =
        Client(
            host = "localhost",
            port = 5432,
            username = "postgres",
            password = "postgres",
            database = "postgres",
        )

    client.connect()

    val query = "SELECT $1 as \"bar\", $2 as \"foo\";"

    val preparedStatement = client.prepare(query)

    val (rowDescription, data) = client.execute(preparedStatement, listOf("It is working!!", "For reals"))

    data.collect { row ->
        val cells = row.fields.map { buffer -> buffer?.readString() }
        val mapped =
            rowDescription.fields
                .map { it.field }
                .zip(cells)
                .toMap()
        println(mapped)
    }

    client.disconnect()
}
