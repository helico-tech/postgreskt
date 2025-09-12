package nl.helico.postgreskt.protocol

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import nl.helico.postgreskt.protocol.messages.StartupMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class PostgresWireProtocolTests {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startupMessage() =
        runTest {
            val input = ByteReadChannel.Empty
            val output = ByteChannel()
            val protocol =
                ProtocolImpl(
                    input,
                    output,
                    this,
                )

            protocol.frontendChannel.send(StartupMessage(parameters = mapOf("user" to "postgres")))

            advanceUntilIdle()
            protocol.close()

            val data = output.readRemaining().readByteArray().toHexString()
            val expected = "00000017000300007573657200706f7374677265730000"
            assertEquals(expected, data)
        }
}
