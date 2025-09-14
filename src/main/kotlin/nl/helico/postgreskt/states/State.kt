package nl.helico.postgreskt.states

import io.ktor.util.Attributes
import nl.helico.postgreskt.messages.Message
import kotlin.reflect.KClass

data class HandleScope<T : Message>(
    val message: T,
    val context: Attributes,
)
typealias Handler<T> = HandleScope<T>.() -> Unit

interface State {
    fun handle(
        message: Message,
        context: Attributes,
    )
}

abstract class StateBuilder(
    builder: Builder.() -> Unit,
) : State {
    private val build = Builder().apply(builder)

    override fun handle(
        message: Message,
        context: Attributes,
    ) {
        val a: Attributes
        val handler =
            build.handlers[message::class] ?: build.unhandledHandler
                ?: throw IllegalStateException("No handler for message type ${message::class} in state $this")

        val scope = HandleScope(message, context)
        (handler as Handler<Message>).invoke(scope)
    }

    class Builder {
        val handlers = mutableMapOf<KClass<out Message>, Handler<*>>()
        var unhandledHandler: Handler<*>? = null

        fun <T : Message> on(
            messageType: KClass<T>,
            handler: Handler<T>,
        ) {
            @Suppress("UNCHECKED_CAST")
            handlers[messageType] = handler as Handler<*>
        }

        fun unhandled(handler: Handler<*>) {
            unhandledHandler = handler
        }

        fun allowUnhandled() {
            unhandledHandler = {}
        }

        inline fun <reified T : Message> on(noinline handler: HandleScope<T>.() -> Unit) = on(T::class, handler)
    }
}
