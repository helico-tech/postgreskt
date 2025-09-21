package nl.helico.postgreskt

interface Client {
    val isConnected: Boolean

    suspend fun connect()

    suspend fun disconnect()

    companion object {
        operator fun invoke(connectionParameters: ConnectionParameters): Client = DefaultClient(connectionParameters)
    }
}
