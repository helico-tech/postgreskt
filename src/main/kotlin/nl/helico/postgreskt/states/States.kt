package nl.helico.postgreskt.states

import kotlinx.coroutines.runBlocking
import nl.helico.postgreskt.ConnectionParameters
import nl.helico.postgreskt.ConnectionParametersKey
import nl.helico.postgreskt.messages.AuthenticationMD5
import nl.helico.postgreskt.messages.BackendKeyData
import nl.helico.postgreskt.messages.NotificationResponse
import nl.helico.postgreskt.messages.ParameterStatus
import nl.helico.postgreskt.messages.PasswordMessage
import nl.helico.postgreskt.messages.ReadyForQuery
import nl.helico.postgreskt.messages.StartupMessage
import nl.helico.postgreskt.messages.Terminate

fun StateDSL.Builder.common() {
    on<Terminate> {
        transition(Disconnected)
    }

    on<ParameterStatus> {
        val parameters = context.computeIfAbsent(RuntimeParameters) { mutableMapOf() }
        parameters[message.name] = message.value
    }

    on<BackendKeyData> {
        val keys = context.computeIfAbsent(CancellationKeys) { mutableMapOf() }
        keys[message.processId] = message.secretKey
    }

    on<NotificationResponse> {
        val handlers = context.computeIfAbsent(NotificationHandlers) { mutableListOf() }
        handlers.forEach { it(message) }
    }
}

data object Disconnected : StateDSL({
    common()

    on<StartupMessage> {
        send(message)
        transition(Connecting)
    }
})

data object Connecting : StateDSL({
    common()

    on<AuthenticationMD5> {
        val params = context[ConnectionParametersKey]
        val passwordMessage =
            runBlocking {
                PasswordMessage.md5(
                    username = params.username,
                    password = params.password,
                    salt = message.salt,
                )
            }

        send(passwordMessage)
    }

    on<ReadyForQuery> {
        transition(ReadyForQuery)
    }

    allowUnhandled()
})

data object ReadyForQuery : StateDSL({
    common()
})
