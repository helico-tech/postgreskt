package nl.helico.postgreskt.protocol.messages

import io.ktor.util.Digest
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

sealed interface Authentication {
    companion object : Deserializer<BackendMessage> {
        override fun deserialize(
            type: Char,
            buffer: Buffer,
        ): BackendMessage =
            when (buffer.readInt()) {
                0 -> AuthenticationOK
                5 -> AuthenticationMD5Password(buffer.readByteArray(4))
                else -> BackendMessage.Unhandled(type, buffer)
            }
    }
}

data object AuthenticationOK : BackendMessage

data class AuthenticationMD5Password(
    val salt: ByteArray,
) : BackendMessage {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AuthenticationMD5Password

        if (!salt.contentEquals(other.salt)) return false

        return true
    }

    override fun hashCode(): Int = salt.contentHashCode()
}

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

    override fun asBuffer(): Buffer =
        buildFrontendMessage('p') {
            writeCString(password)
        }
}
