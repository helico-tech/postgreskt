package nl.helico.postgreskt.messages

import io.ktor.util.Digest
import io.ktor.utils.io.core.toByteArray
import kotlinx.io.readByteArray
import kotlinx.io.readString

// Backend authentication messages
data object AuthenticationOk : BackendMessage

data class AuthenticationMD5(
    val salt: ByteArray,
) : BackendMessage

data class AuthenticationSASL(
    val mechanisms: List<String>,
) : BackendMessage

data class AuthenticationSASLContinue(
    val data: String,
) : BackendMessage

data class AuthenticationSASLFinal(
    val data: String,
) : BackendMessage

// Frontend auth messages

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

data class SASLInitialResponse(
    val mechanism: String,
    val data: ByteArray,
) : FrontendMessage


data class SASLResponse(
    val data: ByteArray,
) : FrontendMessage

fun MessageRegistry.Builder.authentication() {
    // Frontend serializers
    frontend<PasswordMessage> { message ->
        writeSized('p') {
            writeCString(message.password)
        }
    }

    frontend<SASLInitialResponse> { message ->
        writeSized('p') {
            writeCString(message.mechanism)
            if (message.data.isEmpty()) {
                writeInt(-1)
            } else {
                writeInt(message.data.size)
                write(message.data)
            }
        }
    }

    frontend<SASLResponse> { message ->
        writeSized('p') {
            write(message.data)
        }
    }

    // Backend deserializer for Authentication family
    backend('R') { type ->
        when (readInt()) {
            0 -> AuthenticationOk
            5 -> AuthenticationMD5(readByteArray())
            10 -> {
                val mechs = mutableListOf<String>()
                while (true) {
                    val mech = readCString()
                    if (mech.isEmpty()) break
                    mechs.add(mech)
                }
                AuthenticationSASL(mechs)
            }
            11 -> {
                // Remainder is a string payload (SASL continue)
                val s = readString(size)
                AuthenticationSASLContinue(s)
            }
            12 -> {
                val s = readString(size)
                AuthenticationSASLFinal(s)
            }
            else -> BackendMessage.Unhandled(type, this)
        }
    }
}
