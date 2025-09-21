package nl.helico.postgreskt.messages

import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.core.writeFully
import kotlinx.coroutines.channels.SendChannel
import kotlinx.io.Buffer

data class ReadyForQuery(
    val status: Char,
) : BackendMessage

data class Query(
    val query: String,
    val resultChannel: SendChannel<DataRow>,
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

data class ParameterDescription(
    val parameterOid: List<Int>,
) : BackendMessage

data class Parse(
    val name: String,
    val query: String,
) : FrontendMessage

data object ParseComplete : BackendMessage

data class Describe(
    val type: Char,
    val name: String,
) : FrontendMessage

data class DataRow(
    val fields: List<Buffer?>,
) : BackendMessage

data class CommandComplete(
    val tag: String,
) : BackendMessage

data class Bind(
    val name: String,
    val preparedStatement: String,
    val values: List<String?>,
) : FrontendMessage

data class Execute(
    val name: String,
    val resultChannel: SendChannel<DataRow>,
) : FrontendMessage

data class Close(
    val type: Char,
    val name: String,
) : FrontendMessage

data object BindComplete : BackendMessage

data object CloseComplete : BackendMessage

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

    backend('1') {
        ParseComplete
    }

    backend('t') {
        ParameterDescription(
            parameterOid = (0 until readShort()).map { readInt() },
        )
    }

    frontend<Query> { message ->
        writeSized('Q') {
            writeCString(message.query)
        }
    }

    frontend<Parse> { message ->
        writeSized('P') {
            writeCString(message.name)
            writeCString(message.query)
            writeShort(0)
        }
    }

    frontend<Describe> { message ->
        writeSized('D') {
            writeByte(message.type.code.toByte())
            writeCString(message.name)
        }
    }

    frontend<Sync> {
        writeByte('S'.code.toByte())
        writeInt(4) // Length is exactly 4 bytes (just the length field)
    }

    frontend<Bind> { message ->
        writeSized('B') {
            writeCString(message.name)
            writeCString(message.preparedStatement)

            // Parameter format codes
            writeShort(0) // Number of parameter format codes (0 = use default text format for all)

            // Parameter values
            writeShort(message.values.size.toShort())
            message.values.forEach { value ->
                val bytes = value?.toByteArray()
                if (bytes == null) {
                    writeInt(-1)
                } else {
                    writeInt(bytes.size)
                    writeFully(bytes)
                }
            }

            // Result format codes
            writeShort(0) // Number of result format codes (0 = use default text format for all)
        }
    }

    frontend<Execute> { message ->
        writeSized('E') {
            writeCString(message.name)
            writeInt(0)
        }
    }

    frontend<Close> { message ->
        writeSized('C') {
            writeByte(message.type.code.toByte())
            writeCString(message.name)
        }
    }

    backend('2') {
        BindComplete
    }

    backend('3') {
        CloseComplete
    }
}
