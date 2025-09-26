package nl.helico.postgreskt

data class ConnectionParameters(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
)
