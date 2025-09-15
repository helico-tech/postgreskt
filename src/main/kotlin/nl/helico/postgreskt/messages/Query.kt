package nl.helico.postgreskt.messages

import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.core.writeFully
import kotlinx.io.Buffer

data class ReadyForQuery(
    val status: Char,
) : BackendMessage

data class Query(
    val query: String,
) : FrontendMessage

data class RowDescription(
    val fields: List<Field>,
) : BackendMessage {
    data class Field(
        val field: String,
        val tableOid: Int,
        val columnAttributeNumber: Short,
        val fieldOid: Int,
        val dataTypeSize: Short,
        val typeModifier: Int,
        val formatCode: Short,
    )
}

data class DataRow(
    val fields: List<Buffer?>,
) : BackendMessage

data class CommandComplete(
    val tag: String,
) : BackendMessage

fun MessageRegistry.Builder.query() {
    backend('Z') {
        ReadyForQuery(readByte().toInt().toChar())
    }

    backend('T') {
        val count = readShort()
        val fields =
            (0 until count).map {
                RowDescription.Field(
                    field = readCString(),
                    tableOid = readInt(),
                    columnAttributeNumber = readShort(),
                    fieldOid = readInt(),
                    dataTypeSize = readShort(),
                    typeModifier = readInt(),
                    formatCode = readShort(),
                )
            }
        RowDescription(fields)
    }

    backend('D') {
        val count = readShort()
        val fields =
            (0 until count).map {
                when (val length = readInt()) {
                    -1 -> null
                    else ->
                        Buffer().also { buffer ->
                            buffer.writeFully(readBytes(length))
                        }
                }
            }
        DataRow(fields)
    }

    backend('C') {
        CommandComplete(
            tag = readCString(),
        )
    }

    frontend<Query> { message ->
        writeSized('Q') {
            writeCString(message.query)
        }
    }
}
