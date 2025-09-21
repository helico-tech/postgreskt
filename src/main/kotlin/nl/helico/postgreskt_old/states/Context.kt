package nl.helico.postgreskt_old.states

import io.ktor.util.AttributeKey
import nl.helico.postgreskt_old.messages.NotificationResponse

val RuntimeParameters = AttributeKey<MutableMap<String, String>>("runtime-parameters")

val CancellationKeys = AttributeKey<MutableMap<Int, Int>>("cancellation-keys")

typealias NotificationHandler = (NotificationResponse) -> Unit

val NotificationHandlers = AttributeKey<MutableList<NotificationHandler>>("notification-handlers")
