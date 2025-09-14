package nl.helico.postgreskt.states

import io.ktor.util.Attributes
import io.ktor.util.putAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout
import nl.helico.postgreskt.messages.FrontendMessage
import nl.helico.postgreskt.messages.Message
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class StateMachine(
    val initialState: State = Disconnected,
    private val send: suspend (FrontendMessage) -> Unit = {},
    private val onStateChanged: (State, State) -> Unit = { _, _ -> },
    private val context: Attributes = Attributes(),
) {
    private val _currentState = MutableStateFlow(initialState)
    val currentState: StateFlow<State> = _currentState

    suspend fun handle(message: Message) {
        val messages = mutableListOf<FrontendMessage>()
        var state = currentState.value

        state.handle(
            message = message,
            send = { messages.add(it) },
            transition = { state = it },
            context = context,
        )
        _currentState.update { old ->
            state.also {
                if (it != old) onStateChanged(old, it)
            }
        }
        messages.forEach { send(it) }
    }

    suspend fun waitForState(
        vararg targetStates: State,
        timeout: Duration = 10.seconds,
    ): State =
        withTimeout(timeout) {
            currentState.first { it in targetStates }
        }
}
