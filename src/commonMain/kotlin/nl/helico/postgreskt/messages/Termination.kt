package nl.helico.postgreskt.messages

data object Terminate : FrontendMessage

fun MessageRegistry.Builder.terminate() {
    frontend<Terminate> {
        writeByte('X'.code.toByte())
        writeInt(4)
    }
}
