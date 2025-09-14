package nl.helico.postgreskt.states

import io.ktor.util.AttributeKey
import nl.helico.postgreskt.messages.NotificationResponse

val RuntimeParameters = AttributeKey<MutableMap<String, String>>("runtime-parameters")

val CancellationKeys = AttributeKey<MutableMap<Int, Int>>("cancellation-keys")

typealias NotificationHandler = (NotificationResponse) -> Unit

val NotificationHandlers = AttributeKey<MutableList<NotificationHandler>>("notification-handlers")
