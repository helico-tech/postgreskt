package nl.helico.postgreskt.states

import io.ktor.util.AttributeKey
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import kotlinx.io.readString
import nl.helico.postgreskt.ConnectionParametersKey
import nl.helico.postgreskt.messages.AuthenticationMD5
import nl.helico.postgreskt.messages.AuthenticationOk
import nl.helico.postgreskt.messages.BackendKeyData
import nl.helico.postgreskt.messages.CommandComplete
import nl.helico.postgreskt.messages.DataRow
import nl.helico.postgreskt.messages.Describe
import nl.helico.postgreskt.messages.ErrorResponse
import nl.helico.postgreskt.messages.NotificationResponse
import nl.helico.postgreskt.messages.ParameterDescription
import nl.helico.postgreskt.messages.ParameterStatus
import nl.helico.postgreskt.messages.Parse
import nl.helico.postgreskt.messages.ParseComplete
import nl.helico.postgreskt.messages.PasswordMessage
import nl.helico.postgreskt.messages.Query
import nl.helico.postgreskt.messages.ReadyForQuery
import nl.helico.postgreskt.messages.RowDescription
import nl.helico.postgreskt.messages.StartupMessage
import nl.helico.postgreskt.messages.Sync
import nl.helico.postgreskt.messages.Terminate
import nl.helico.postgreskt.types.TypeDef
import nl.helico.postgreskt.types.TypeDefsKey

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

    ignore<ParseComplete>()
    ignore<ParameterDescription>()
    ignore<RowDescription>()
    ignore<ReadyForQuery>()
})

data class Querying(
    val resultChannel: SendChannel<DataRow>,
) : StateDSL({

        @Suppress("ktlint:standard:property-naming")
        val RowDescriptionKey = AttributeKey<RowDescription>("RowDescription")

        common()

        on<RowDescription> {
            context.put(RowDescriptionKey, message)
        }

        on<DataRow> {
            resultChannel.send(message)
        }

        ignore<CommandComplete>()

        on<ReadyForQuery> {
            resultChannel.close()
            context.remove(RowDescriptionKey)
            transition(ReadyForQuery)
        }
    })
