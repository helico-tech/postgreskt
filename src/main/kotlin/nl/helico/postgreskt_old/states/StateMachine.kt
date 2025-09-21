package nl.helico.postgreskt_old.states

import io.ktor.util.Attributes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout
import nl.helico.postgreskt_old.messages.FrontendMessage
import nl.helico.postgreskt_old.messages.Message
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

    private val _events = MutableSharedFlow<Message>(replay = 256)
    val events: SharedFlow<Message> = _events

    suspend fun handle(message: Message) {
        _events.emit(message)
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

    suspend inline fun <reified T : Message> waitForMessage(timeout: Duration = 10.seconds) =
        withTimeout(timeout) {
            events.filterIsInstance<T>().first()
        }
}
