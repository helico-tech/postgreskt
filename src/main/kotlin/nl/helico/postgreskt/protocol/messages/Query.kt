package nl.helico.postgreskt.protocol.messages

import io.ktor.utils.io.core.readBytes
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

data class Query(
    val query: String,
) : FrontendMessage {
    override fun asBuffer(): Buffer = buildFrontendMessage('Q') { writeCString(query) }
}

data class RowDescription(
    val fields: List<FieldDescriptor>,
) : BackendMessage {
    companion object : Deserializer<RowDescription> {
        override fun deserialize(
            type: Char,
            buffer: Buffer,
        ): RowDescription {
            val fields = mutableListOf<FieldDescriptor>()
            val count = buffer.readShort()
            (0 until count).forEach { _ ->
                fields.add(
                    FieldDescriptor(
                        name = buffer.readCString(),
                        tableObjectId = buffer.readInt(),
                        columnAttributeNumber = buffer.readShort(),
                        dataTypeObjectId = buffer.readInt(),
                        dataTypeSize = buffer.readShort(),
                        dataTypeModifier = buffer.readInt(),
                        formatCode = buffer.readShort(),
                    ),
                )
            }
            return RowDescription(fields)
        }
    }
}

data class FieldDescriptor(
    val name: String,
    val tableObjectId: Int,
    val columnAttributeNumber: Short,
    val dataTypeObjectId: Int,
    val dataTypeSize: Short,
    val dataTypeModifier: Int,
    val formatCode: Short,
)

data class DataRow(
    val values: List<ByteArray?>,
) : BackendMessage {
    companion object : Deserializer<DataRow> {
        override fun deserialize(
            type: Char,
            buffer: Buffer,
        ): DataRow {
            val count = buffer.readShort()
            val values = mutableListOf<ByteArray?>()
            (0 until count).forEach { _ ->
                when (val length = buffer.readInt()) {
                    -1 -> values.add(null)
                    0 -> values.add(ByteArray(0))
                    else -> values.add(buffer.readBytes(length))
                }
            }
            return DataRow(values)
        }
    }
}
