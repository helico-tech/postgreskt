package nl.helico.postgreskt

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
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
            val client = client()
            client.connect()
            assert(client.isConnected)
            client.disconnect()
            assert(!client.isConnected)
        }

    internal fun client() =
        Client(
            connectionParameters =
                ConnectionParameters(
                    host = POSTGRES_CONTAINER.host,
                    port = POSTGRES_CONTAINER.firstMappedPort,
                    username = USERNAME,
                    password = PASSWORD,
                    database = DATABASE,
                ),
        )
}
