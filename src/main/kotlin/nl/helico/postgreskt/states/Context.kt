package nl.helico.postgreskt.states

import io.ktor.util.AttributeKey
import nl.helico.postgreskt.messages.FrontendMessage

typealias Send = (FrontendMessage) -> Unit
typealias Transition = (State) -> Unit

val SendAttributeKey = AttributeKey<Send>("Send")
val TransitionAttributeKey = AttributeKey<Transition>("Transition")

fun HandleScope<*>.send(message: FrontendMessage) {
    context[SendAttributeKey].invoke(message)
}

fun HandleScope<*>.transition(state: State) {
    context[TransitionAttributeKey].invoke(state)
}
