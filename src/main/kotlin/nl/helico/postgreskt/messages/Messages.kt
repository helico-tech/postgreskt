package nl.helico.postgreskt.messages

sealed interface Message

sealed interface FrontendMessage : Message

sealed interface BackendMessage : Message
