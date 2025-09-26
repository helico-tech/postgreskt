import kotlinx.coroutines.runBlocking

actual fun suspendingScope(block: suspend () -> Unit) {
    runBlocking { block() }
}
