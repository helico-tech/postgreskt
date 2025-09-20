import kotlinx.coroutines.delay
import nl.helico.postgreskt.Client

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

    val query =
        """SELECT 'foo' as "bar";""".trimIndent()

    // val (fields, data) = client.query(query)

    val preparedStatement = client.prepare("test", query)

    /*data.collect {
        delay(1000)
        println("FOO $it")
    }*/

    delay(2000)

    client.disconnect()

    /*val (fields, data) = client.query("SELECT 1 AS \"test\", 'foo' AS \"bar\";")
    println(fields)
    data.collect {
        println(it)
    }*/
}
