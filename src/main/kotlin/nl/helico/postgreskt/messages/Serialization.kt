package nl.helico.postgreskt.messages

import kotlinx.io.Buffer

fun interface Serializer<T : FrontendMessage> {
    fun serialize(message: T): Buffer
}

fun interface Deserializer<T : BackendMessage> {
    fun deserialize(
        type: Char,
        buffer: Buffer,
    ): T

    object Unhandled : Deserializer<BackendMessage.Unhandled> {
        override fun deserialize(
            type: Char,
            buffer: Buffer,
        ): BackendMessage.Unhandled = BackendMessage.Unhandled(type, buffer)
    }
}
