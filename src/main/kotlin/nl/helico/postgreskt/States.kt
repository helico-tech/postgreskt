package nl.helico.postgreskt

import nl.helico.postgreskt.messages.FrontendMessage
import nl.helico.postgreskt.messages.Message
import nl.helico.postgreskt.messages.StartupMessage
import kotlin.reflect.KClass

typealias Send = (FrontendMessage) -> Unit
typealias Transition = (StateBuilder) -> Unit

data class HandleScope<T : Message>(
    val message: T,
    val send: Send,
    val transition: Transition,
)
typealias Handler<T> = HandleScope<T>.() -> Unit

interface State {
    fun handle(
        message: Message,
        send: Send,
        transition: Transition,
    )
}

abstract class StateBuilder(
    builder: Builder.() -> Unit,
) : State {
    private val build = Builder().apply(builder)

    override fun handle(
        message: Message,
        send: Send,
        transition: Transition,
    ) {
        val handler =
            build.handlers[message::class] ?: build.unhandledHandler
                ?: throw IllegalStateException("No handler for message type ${message::class} in state $this")

        val scope = HandleScope(message, send, transition)
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

data object Disconnected : StateBuilder({
    on<StartupMessage> {
        send(message)
        transition(Connecting)
    }
})

data object Connecting : StateBuilder({
    allowUnhandled()
})

data object ReadyForQuery : StateBuilder({})
