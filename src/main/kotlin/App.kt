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

    val (fields, data) =
        client.query(
            """
            SELECT 
                generate_series(1, 100) as id,
                random() as random_value,
                floor(random() * 1000)::int as random_int
            FROM generate_series(1, 100);
            """.trimIndent(),
        )
    data.collect {
        delay(1000)
        println("FOO $it")
    }

    client.disconnect()

    /*val (fields, data) = client.query("SELECT 1 AS \"test\", 'foo' AS \"bar\";")
    println(fields)
    data.collect {
        println(it)
    }*/
}
