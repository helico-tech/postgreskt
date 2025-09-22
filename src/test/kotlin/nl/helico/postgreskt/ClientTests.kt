package nl.helico.postgreskt

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.io.readString
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.Test

@Testcontainers
class ClientTests {
    companion object {
        val DATABASE = "postgres"
        val USERNAME = "postgres"
        val PASSWORD = "postgres"

        @Container
        val POSTGRES_CONTAINER =
            PostgreSQLContainer("postgres:17-alpine")
                .withUsername(USERNAME)
                .withPassword(PASSWORD)
                .withDatabaseName(DATABASE)
                .withEnv("POSTGRES_HOST_AUTH_METHOD", "md5")
                .withEnv("POSTGRES_INITDB_ARGS", "--auth-host=md5")
    }

    @Test
    fun connectAndDisconnect() =
        runBlocking {
            val client = client(connect = false)
            assert(!client.isConnected)
            client.connect()
            assert(client.isConnected)
            client.disconnect()
            assert(!client.isConnected)
        }

    @Test
    fun simpleQuery(): Unit =
        testWithClient {
            val (_, rows) = query("SELECT 'one' AS one, 2 AS two;")
            val data = rows.map { data -> data.fields.map { it?.readString() } }.toList()
            assert(data == listOf(listOf("one", "2")))
        }

    internal suspend fun client(connect: Boolean = true) =
        Client(
            connectionParameters =
                ConnectionParameters(
                    host = POSTGRES_CONTAINER.host,
                    port = POSTGRES_CONTAINER.firstMappedPort,
                    username = USERNAME,
                    password = PASSWORD,
                    database = DATABASE,
                ),
        ).also { if (connect) it.connect() }

    internal fun testWithClient(block: suspend Client.() -> Unit) =
        runBlocking {
            val client = client()
            block(client)
            client.disconnect()
        }
}
