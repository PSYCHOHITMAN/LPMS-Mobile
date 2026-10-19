package com.pointblank.lpms.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2 password hashing for the local stand-in repositories.
 * Once a hosted auth provider is chosen, the provider stores and hashes credentials
 * and this class is no longer used for real accounts.
 * Stored format: iterations:saltBase64:hashBase64
 */
object PasswordHasher {
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(password.toCharArray(), salt, ITERATIONS)
        val enc = Base64.getEncoder()
        return "$ITERATIONS:${enc.encodeToString(salt)}:${enc.encodeToString(hash)}"
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 3) return false
        val iterations = parts[0].toIntOrNull() ?: return false
        val dec = Base64.getDecoder()
        val salt = runCatching { dec.decode(parts[1]) }.getOrNull() ?: return false
        val expected = runCatching { dec.decode(parts[2]) }.getOrNull() ?: return false
        val actual = derive(password.toCharArray(), salt, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
