package nl.helico.postgreskt

import nl.helico.postgreskt.messages.BackendMessage
import nl.helico.postgreskt.messages.FrontendMessage
import nl.helico.postgreskt.messages.Message
import nl.helico.postgreskt.messages.StartupMessage
import nl.helico.postgreskt_old.protocol.messages.ReadyForQuery

typealias Send = (FrontendMessage) -> Unit
typealias Transition = (State) -> Unit

fun interface State {
    fun handle(
        message: Message,
        send: Send,
        transition: Transition,
    )
}

val Disconnected =
    State { message, send, transition ->
        when (message) {
            is StartupMessage -> {
                send(message)
                transition(Connecting)
            }
            else -> throw IllegalStateException()
        }
    }

val Connecting =
    State { message, _, _ ->
        when (message) {
            is BackendMessage.Unhandled -> {}
            else -> throw IllegalStateException()
        }
    }

val ReadyForQuery = State { message, send, transition -> }
