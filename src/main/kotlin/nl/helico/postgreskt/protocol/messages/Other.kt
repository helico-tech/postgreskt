package nl.helico.postgreskt.protocol.messages

import kotlinx.io.Buffer

data class NoticeResponse(
    val fields: List<IdentifiedField>,
) : BackendMessage {
    companion object : Deserializer<NoticeResponse> {
        override fun deserialize(
            type: Char,
            buffer: Buffer,
        ): NoticeResponse {
            val fields = IdentifiedFieldListDeserializer.deserialize(type, buffer)
            return NoticeResponse(fields)
        }
    }
}

data class ErrorResponse(
    val fields: List<IdentifiedField>,
) : BackendMessage {
    companion object : Deserializer<ErrorResponse> {
        override fun deserialize(
            type: Char,
            buffer: Buffer,
        ): ErrorResponse {
            val fields = IdentifiedFieldListDeserializer.deserialize(type, buffer)
            return ErrorResponse(fields)
        }
    }
}

data class ParameterStatus(
    val name: String,
    val value: String,
) : BackendMessage {
    companion object : Deserializer<ParameterStatus> {
        override fun deserialize(
            type: Char,
            buffer: Buffer,
        ): ParameterStatus {
            val name = buffer.readCString()
            val value = buffer.readCString()
            return ParameterStatus(name, value)
        }
    }
}

object IdentifiedFieldListDeserializer : Deserializer<List<IdentifiedField>> {
    override fun deserialize(
        type: Char,
        buffer: Buffer,
    ): List<IdentifiedField> =
        buildList {
            while (!buffer.exhausted()) {
                when (val type = buffer.readByte()) {
                    0.toByte() -> return this
                    else -> add(IdentifiedField(type.toInt().toChar(), buffer.readCString()))
                }
            }
        }
}

data class IdentifiedField(
    val type: Char,
    val value: String,
) : BackendMessage
