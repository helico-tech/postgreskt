package nl.helico.postgreskt_old.messages

import kotlinx.io.Buffer
import kotlin.reflect.KClass

typealias Serializers = Map<KClass<out FrontendMessage>, Serializer<FrontendMessage>>
typealias Deserializers = Map<Char, Deserializer<out BackendMessage>>

class MessageRegistry(
    val serializers: Serializers,
    val deserializers: Deserializers,
) {
    fun deserialize(
        type: Char,
        buffer: Buffer,
    ): BackendMessage = deserializers[type]?.deserialize(type, buffer) ?: Deserializer.Unhandled.deserialize(type, buffer)

    @Suppress("UNCHECKED_CAST")
    fun <T : FrontendMessage> serialize(message: T): Buffer =
        (serializers[message::class] as? Serializer<T>)?.serialize(message)
            ?: throw NoSuchElementException("No serializer for message type '${message::class}'.")

    companion object {
        operator fun invoke(body: Builder.() -> Unit): MessageRegistry = Builder().apply(body).build()
    }

    class Builder {
        private val serializers = mutableMapOf<KClass<out FrontendMessage>, Serializer<FrontendMessage>>()
        private val deserializers = mutableMapOf<Char, Deserializer<out BackendMessage>>()

        fun backend(
            type: Char,
            deserializer: Deserializer<out BackendMessage>,
        ) {
            deserializers[type] = deserializer
        }

        fun frontend(
            message: KClass<out FrontendMessage>,
            serializer: Serializer<out FrontendMessage>,
        ) {
            @Suppress("UNCHECKED_CAST")
            serializers[message] = serializer as Serializer<FrontendMessage>
        }

        inline fun <reified T : BackendMessage> backend(
            type: Char,
            crossinline body: Buffer.(Char) -> T,
        ) = backend(
            type,
            Deserializer { type, buffer ->
                body(buffer, type)
            },
        )

        inline fun <reified T : FrontendMessage> frontend(crossinline body: Buffer.(T) -> Unit) =
            frontend(
                T::class,
                Serializer { message ->
                    Buffer().apply {
                        body(message as T)
                    }
                },
            )

        fun build(): MessageRegistry = MessageRegistry(serializers, deserializers)
    }
}

val DefaultMessageRegistry =
    MessageRegistry {
        commonResponses()
        parameterStatus()
        backendKeyData()

        startupMessage()
        authentication()
        query()
        terminate()
    }
