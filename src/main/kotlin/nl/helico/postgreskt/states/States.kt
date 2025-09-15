package nl.helico.postgreskt.states

import io.ktor.util.AttributeKey
import kotlinx.coroutines.runBlocking
import kotlinx.io.readString
import nl.helico.postgreskt.ConnectionParametersKey
import nl.helico.postgreskt.messages.AuthenticationMD5
import nl.helico.postgreskt.messages.AuthenticationOk
import nl.helico.postgreskt.messages.BackendKeyData
import nl.helico.postgreskt.messages.CommandComplete
import nl.helico.postgreskt.messages.DataRow
import nl.helico.postgreskt.messages.NotificationResponse
import nl.helico.postgreskt.messages.ParameterStatus
import nl.helico.postgreskt.messages.PasswordMessage
import nl.helico.postgreskt.messages.Query
import nl.helico.postgreskt.messages.ReadyForQuery
import nl.helico.postgreskt.messages.RowDescription
import nl.helico.postgreskt.messages.StartupMessage
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
        send(Query("SELECT t.oid, t.typname FROM pg_type t;"))
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
        transition(Querying)
    }
})

data object Querying : StateDSL({

    @Suppress("ktlint:standard:property-naming")
    val RowDescriptionKey = AttributeKey<RowDescription>("RowDescription")

    common()

    on<RowDescription> {
        context.put(RowDescriptionKey, message)
    }

    on<DataRow> {
        println(message)
    }

    ignore<CommandComplete>()

    on<ReadyForQuery> {
        transition(ReadyForQuery)
    }
})
