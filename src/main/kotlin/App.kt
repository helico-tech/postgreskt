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

    /*val (fields, data) = client.query("SELECT 1 AS \"test\", 'foo' AS \"bar\";")
    println(fields)
    data.collect {
        println(it)
    }*/
}
