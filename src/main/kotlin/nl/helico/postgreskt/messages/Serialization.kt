package nl.helico.postgreskt.messages

import kotlinx.io.Buffer

fun interface Serializer<T : FrontendMessage> {
    fun serialize(message: T): Buffer
}

fun interface Deserializer<T : BackendMessage> {
    fun deserialize(buffer: Buffer): T
}
