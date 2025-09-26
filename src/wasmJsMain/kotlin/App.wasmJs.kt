import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

actual fun suspendingScope(block: suspend () -> Unit) {
    GlobalScope.promise { block() }
}
