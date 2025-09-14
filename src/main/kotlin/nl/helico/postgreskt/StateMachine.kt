package nl.helico.postgreskt

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout
import nl.helico.postgreskt.messages.BackendMessage
import nl.helico.postgreskt.messages.FrontendMessage
import nl.helico.postgreskt.messages.Message
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class StateMachine(
    val initialState: State = Disconnected,
    private val send: suspend (FrontendMessage) -> Unit = {},
    private val onStateChanged: (State) -> Unit = {},
) {
    private val _currentState = MutableStateFlow<State>(initialState)
    val currentState: StateFlow<State> = _currentState

    suspend fun handle(message: Message) {
        val messages = mutableListOf<FrontendMessage>()
        var state = currentState.value

        state.handle(
            message = message,
            send = { messages.add(it) },
            transition = { state = it },
        )

        _currentState.update { state }
        messages.forEach { send(it) }
    }

    suspend fun waitForState(
        vararg targetStates: State,
        timeout: Duration = 10.seconds,
    ): State =
        withTimeout(timeout) {
            currentState.first { it in targetStates }
        }

    private fun transition(newState: State) {
        _currentState.value = newState
        onStateChanged(newState)
    }
}
