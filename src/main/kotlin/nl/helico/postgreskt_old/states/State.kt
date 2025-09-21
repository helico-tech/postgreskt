package nl.helico.postgreskt_old.states

import io.ktor.util.Attributes
import nl.helico.postgreskt_old.messages.FrontendMessage
import nl.helico.postgreskt_old.messages.Message
import kotlin.reflect.KClass

typealias Send = (FrontendMessage) -> Unit
typealias Transition = (State) -> Unit

data class HandleScope<T : Message>(
    val message: T,
    val send: Send,
    val transition: Transition,
    val context: Attributes,
)

typealias Handler<T> = suspend HandleScope<T>.() -> Unit

interface State {
    suspend fun handle(
        message: Message,
        send: Send,
        transition: Transition,
        context: Attributes,
    )
}

abstract class StateDSL(
    builder: Builder.() -> Unit,
) : State {
    private val build = Builder().apply(builder)

    override suspend fun handle(
        message: Message,
        send: Send,
        transition: Transition,
        context: Attributes,
    ) {
        val handler =
            build.handlers[message::class] ?: build.unhandledHandler
                ?: throw IllegalStateException("No handler for message type ${message::class} in state $this")

        val scope = HandleScope(message, send, transition, context)
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

        inline fun <reified T : Message> on(noinline handler: Handler<T>) = on(T::class, handler)

        inline fun <reified T : Message> ignore() = on(T::class) { }
    }
}
