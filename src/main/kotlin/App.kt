import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import nl.helico.postgreskt.protocol.PostgresWireProtocol
import nl.helico.postgreskt.protocol.messages.StartupMessage

fun main() {
    runBlocking {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val socket = aSocket(selectorManager).tcp().connect(hostname = "localhost", port = 5432)

        val (send, receive) = PostgresWireProtocol(socket)

        send.send(
            StartupMessage(
                parameters =
                    mapOf(
                        "user" to "postgres",
                        "database" to "postgres",
                    ),
            ),
        )

        launch {
            for (message in receive) {
            }
        }
    }
}
