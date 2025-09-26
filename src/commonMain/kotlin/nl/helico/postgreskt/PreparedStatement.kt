package nl.helico.postgreskt

import nl.helico.postgreskt.messages.ParameterDescription
import nl.helico.postgreskt.messages.RowDescription

data class PreparedStatement(
    val identifier: String,
    val query: String,
    val parameterDescription: ParameterDescription,
    val rowDescription: RowDescription,
)
