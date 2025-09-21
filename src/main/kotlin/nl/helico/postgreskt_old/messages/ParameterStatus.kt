package nl.helico.postgreskt_old.messages

data class ParameterStatus(
    val name: String,
    val value: String,
) : BackendMessage

fun MessageRegistry.Builder.parameterStatus() {
    backend('S') {
        ParameterStatus(readCString(), readCString())
    }
}
