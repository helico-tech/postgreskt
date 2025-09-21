package nl.helico.postgreskt_old.states

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import kotlinx.io.readString
import nl.helico.postgreskt_old.ConnectionParametersKey
import nl.helico.postgreskt_old.messages.AuthenticationMD5
import nl.helico.postgreskt_old.messages.AuthenticationOk
import nl.helico.postgreskt_old.messages.BackendKeyData
import nl.helico.postgreskt_old.messages.Bind
import nl.helico.postgreskt_old.messages.BindComplete
import nl.helico.postgreskt_old.messages.Close
import nl.helico.postgreskt_old.messages.CloseComplete
import nl.helico.postgreskt_old.messages.CommandComplete
import nl.helico.postgreskt_old.messages.DataRow
import nl.helico.postgreskt_old.messages.Describe
import nl.helico.postgreskt_old.messages.ErrorResponse
import nl.helico.postgreskt_old.messages.Execute
import nl.helico.postgreskt_old.messages.NotificationResponse
import nl.helico.postgreskt_old.messages.ParameterDescription
import nl.helico.postgreskt_old.messages.ParameterStatus
import nl.helico.postgreskt_old.messages.Parse
import nl.helico.postgreskt_old.messages.ParseComplete
import nl.helico.postgreskt_old.messages.PasswordMessage
import nl.helico.postgreskt_old.messages.Query
import nl.helico.postgreskt_old.messages.ReadyForQuery
import nl.helico.postgreskt_old.messages.RowDescription
import nl.helico.postgreskt_old.messages.StartupMessage
import nl.helico.postgreskt_old.messages.Sync
import nl.helico.postgreskt_old.messages.Terminate
import nl.helico.postgreskt_old.types.TypeDef
import nl.helico.postgreskt_old.types.TypeDefsKey

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

    on<ErrorResponse> {
        throw IllegalStateException("An error response was received: $message")
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
        send(Query("SELECT t.oid, t.typname FROM pg_type t;", Channel(onBufferOverflow = BufferOverflow.DROP_OLDEST)))
        transition(RetrieveTypeDefinitions)
    }

    ignore<AuthenticationOk>()
})

data object RetrieveTypeDefinitions : StateDSL({
    val types = mutableListOf<TypeDef>()

    common()

    on<DataRow> {
        val (oid, typeName) = message.fields
        types.add(
            TypeDef(
                oid = requireNotNull(oid).readString().toInt(),
                name = requireNotNull(typeName).readString(),
            ),
        )
    }

    on<ReadyForQuery> {
        context[TypeDefsKey] = types
        transition(ReadyForQuery)
    }

    ignore<CommandComplete>()
    ignore<RowDescription>()
})

data object ReadyForQuery : StateDSL({
    common()

    on<Query> {
        send(message)
        transition(Querying(message.resultChannel))
    }

    on<Parse> {
        send(message)
        send(Describe('S', message.name))
        send(Sync)
    }

    on<Bind> {
        send(message)
    }

    on<Execute> {
        send(message)
        send(Close('P', message.name))
        send(Sync)
        transition(Querying(message.resultChannel))
    }

    ignore<ParseComplete>()
    ignore<ParameterDescription>()
    ignore<RowDescription>()
    ignore<ReadyForQuery>()
})

data class Querying(
    val resultChannel: SendChannel<DataRow>,
) : StateDSL({

        common()

        on<DataRow> {
            resultChannel.send(message)
        }

        on<ReadyForQuery> {
            resultChannel.close()
            transition(ReadyForQuery)
        }

        ignore<CloseComplete>()
        ignore<BindComplete>()
        ignore<RowDescription>()
        ignore<CommandComplete>()
    })
