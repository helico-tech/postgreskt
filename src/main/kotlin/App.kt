import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nl.helico.postgreskt.Client
import nl.helico.postgreskt.protocol.Protocol
import nl.helico.postgreskt.protocol.messages.StartupMessage

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

    client.query("SELECT 1")
}
