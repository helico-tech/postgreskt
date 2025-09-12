package nl.helico.postgreskt.protocol.messages

import kotlinx.io.Buffer

fun interface Deserializer<T> {
    fun deserialize(
        type: Char,
        buffer: Buffer,
    ): T
}

object BackendMessageSerializer : Deserializer<BackendMessage> {
    override fun deserialize(
        type: Char,
        buffer: Buffer,
    ): BackendMessage =
        when (type) {
            'R' -> Authentication.deserialize(type, buffer)
            'E' -> ErrorResponse.deserialize(type, buffer)
            'N' -> NoticeResponse.deserialize(type, buffer)
            'Z' -> ReadyForQuery.deserialize(type, buffer)
            'S' -> ParameterStatus.deserialize(type, buffer)
            'T' -> RowDescription.deserialize(type, buffer)
            'D' -> DataRow.deserialize(type, buffer)
            'C' -> CommandComplete.deserialize(type, buffer)
            else -> BackendMessage.Unhandled(type, buffer)
        }
}
