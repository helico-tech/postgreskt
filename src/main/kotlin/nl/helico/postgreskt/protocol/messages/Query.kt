package nl.helico.postgreskt.protocol.messages

import kotlinx.io.Buffer

data class Query(
    val query: String,
) : FrontendMessage {
    override fun asBuffer(): Buffer = buildFrontendMessage('Q') { writeCString(query) }
}
