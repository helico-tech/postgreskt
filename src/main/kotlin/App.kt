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
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeString

fun main() {
    runBlocking {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val socket = aSocket(selectorManager).tcp().connect(hostname = "localhost", port = 5432)

        val readChannel = socket.openReadChannel()
        val writeChannel = socket.openWriteChannel()
    }
}

suspend fun ByteWriteChannel.startupMessage(
    user: String,
    database: String,
) {
    val buffer = Buffer()
    buffer.writeInt(196608)
    buffer.writeString("user", Charsets.UTF_8)
    buffer.writeByte(0x0)
    buffer.writeString(user, Charsets.UTF_8)
    buffer.writeByte(0x0)
    buffer.writeString("database", Charsets.UTF_8)
    buffer.writeByte(0x0)
    buffer.writeString(database, Charsets.UTF_8)
    buffer.writeByte(0x0)

    writeInt(buffer.size.toInt() + 5)
    writeFully(buffer.readByteArray())
    writeByte(0)
    flush()
}

suspend fun ByteReadChannel.receiveMessage() {
//    val type = readByte().toInt().toChar()
//    val length = readInt()
//    println(type)
//    println(length)
//    val remaining = readRemaining(max = length - 4L - 1L).readByteArray()
    println(readUTF8Line())
}
