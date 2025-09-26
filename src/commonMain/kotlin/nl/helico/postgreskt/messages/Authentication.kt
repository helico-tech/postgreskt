package nl.helico.postgreskt.messages

import io.ktor.util.Digest
import io.ktor.utils.io.core.toByteArray
import kotlinx.io.readByteArray

data object AuthenticationOk : BackendMessage

data class AuthenticationMD5(
    val salt: ByteArray,
) : BackendMessage

data class PasswordMessage(
    val password: String,
) : FrontendMessage {
    companion object {
        suspend fun md5(
            username: String,
            password: String,
            salt: ByteArray,
        ): PasswordMessage {
            // First MD5: md5(password + username) - convert to hex string
            val firstHash =
                Digest("MD5").let { digest ->
                    digest += password.toByteArray()
                    digest += username.toByteArray()
                    digest.build().toHexString()
                }

            // Second MD5: md5(firstHashHex + salt) - convert to hex string
            val secondHash =
                Digest("MD5").let { digest ->
                    digest += firstHash.toByteArray()
                    digest += salt
                    digest.build().toHexString()
                }

            return PasswordMessage("md5$secondHash")
        }
    }
}

fun MessageRegistry.Builder.authentication() {
    frontend<PasswordMessage> { message ->
        writeSized('p') {
            writeCString(message.password)
        }
    }

    backend('R') { type ->
        when (readInt()) {
            0 -> AuthenticationOk
            5 -> AuthenticationMD5(readByteArray())
            else -> BackendMessage.Unhandled(type, this)
        }
    }
}
