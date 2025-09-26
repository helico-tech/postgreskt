package nl.helico.postgreskt.messages

import io.ktor.util.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/** Minimal SCRAM-SHA-256 implementation (RFC 5802/7677) for PostgreSQL SASL */
internal object Scram {
    data class ClientFirst(val gs2Header: String, val clientFirstBare: String, val full: String, val nonce: String)
    data class ClientFinal(val withoutProof: String, val proof: ByteArray, val full: String, val expectedServerSignature: ByteArray)

    fun clientFirstMessage(username: String): ClientFirst {
        val gs2 = "n,," // no channel binding, no authzid
        val nonce = generateNonce()
        val bare = "n=${username},r=${nonce}"
        val full = gs2 + bare
        return ClientFirst(gs2, bare, full, nonce)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun parseServerFirst(data: String): Triple<String, ByteArray, Int> {
        // r=combinedNonce,s=base64(i),i=4096
        var r: String? = null
        var s: ByteArray? = null
        var i: Int? = null
        data.split(',').forEach { part ->
            val idx = part.indexOf('=')
            if (idx > 0) {
                val k = part.substring(0, idx)
                val v = part.substring(idx + 1)
                when (k) {
                    "r" -> r = v
                    "s" -> s = Base64.decode(v)
                    "i" -> i = v.toInt()
                }
            }
        }
        return Triple(r ?: error("Server first message missing r"), s ?: error("Missing s"), i ?: error("Missing i"))
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun buildClientFinal(
        gs2Header: String,
        clientFirstBare: String,
        password: String,
        serverFirst: String,
        combinedNonce: String,
        salt: ByteArray,
        iterations: Int,
        username: String,
    ): ClientFinal {
        val c = Base64.encode(gs2Header.encodeToByteArray())
        val withoutProof = "c=${c},r=${combinedNonce}"

        val salted = pbkdf2HmacSha256(password.encodeToByteArray(), salt, iterations, 32)

        val clientKey = hmacSha256(salted, "Client Key".encodeToByteArray())
        val storedKey = sha256(clientKey)

        val authMessage = "${clientFirstBare},${serverFirst},${withoutProof}"

        val clientSignature = hmacSha256(storedKey, authMessage.encodeToByteArray())
        val clientProof = xor(clientKey, clientSignature)

        val serverKey = hmacSha256(salted, "Server Key".encodeToByteArray())
        val serverSignature = hmacSha256(serverKey, authMessage.encodeToByteArray())

        val full = withoutProof + ",p=" + Base64.encode(clientProof)
        return ClientFinal(withoutProof, clientProof, full, serverSignature)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun verifyServerFinal(data: String, expectedServerSignature: ByteArray) {
        val sigB64 = data.split(',').firstOrNull { it.startsWith("v=") }?.substring(2)
            ?: error("Server final message missing v")
        val sig = Base64.decode(sigB64)
        if (!sig.contentEquals(expectedServerSignature)) error("SCRAM server signature mismatch")
    }

    // Utilities
    private fun generateNonce(): String {
        val bytes = Random.Default.nextBytes(18)
        return bytes.joinToString(separator = "") { b ->
            val c = (b.toInt() and 0x7F)
            val ch = when {
                c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code -> c.toChar()
                c == '-'.code || c == '_'.code -> c.toChar()
                else -> (c % 26 + 'a'.code).toChar()
            }
            ch.toString()
        }
    }

    private fun xor(a: ByteArray, b: ByteArray): ByteArray {
        val out = ByteArray(minOf(a.size, b.size))
        for (i in out.indices) out[i] = (a[i].toInt() xor b[i].toInt()).toByte()
        return out
    }

    private suspend fun sha256(data: ByteArray): ByteArray {
        val d = Digest("SHA-256")
        d += data
        return d.build()
    }

    private suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val blockSize = 64
        var k = key
        if (k.size > blockSize) k = sha256(k)
        if (k.size < blockSize) k = k + ByteArray(blockSize - k.size) { 0 }
        val oKeyPad = ByteArray(blockSize) { (0x5c).toByte() }
        val iKeyPad = ByteArray(blockSize) { (0x36).toByte() }
        for (i in 0 until blockSize) {
            oKeyPad[i] = (oKeyPad[i].toInt() xor k[i].toInt()).toByte()
            iKeyPad[i] = (iKeyPad[i].toInt() xor k[i].toInt()).toByte()
        }
        val inner = sha256(iKeyPad + data)
        return sha256(oKeyPad + inner)
    }

    private fun intToBigEndian(i: Int): ByteArray = byteArrayOf(
        ((i ushr 24) and 0xFF).toByte(),
        ((i ushr 16) and 0xFF).toByte(),
        ((i ushr 8) and 0xFF).toByte(),
        (i and 0xFF).toByte(),
    )

    private suspend fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, dkLen: Int): ByteArray {
        val hLen = 32
        val l = (dkLen + hLen - 1) / hLen
        val t = ByteArray(l * hLen)
        var offset = 0
        for (block in 1..l) {
            val u1 = hmacSha256(password, salt + intToBigEndian(block))
            var u = u1.copyOf()
            var f = u1.copyOf()
            for (i in 2..iterations) {
                u = hmacSha256(password, u)
                for (j in f.indices) f[j] = (f[j].toInt() xor u[j].toInt()).toByte()
            }
            for (b in f) {
                t[offset++] = b
            }
        }
        return t.copyOf(dkLen)
    }
}
