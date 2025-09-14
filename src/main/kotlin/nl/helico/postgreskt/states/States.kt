package nl.helico.postgreskt.states

import kotlinx.coroutines.runBlocking
import nl.helico.postgreskt.ConnectionParametersKey
import nl.helico.postgreskt.messages.AuthenticationMD5
import nl.helico.postgreskt.messages.PasswordMessage
import nl.helico.postgreskt.messages.ReadyForQuery
import nl.helico.postgreskt.messages.StartupMessage

data object Disconnected : StateBuilder({
    on<StartupMessage> {
        send(message)
        transition(Connecting)
    }
})

data object Connecting : StateBuilder({
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

data object ReadyForQuery : StateBuilder({})
