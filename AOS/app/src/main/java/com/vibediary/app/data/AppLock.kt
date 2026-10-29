package com.vibediary.app.data

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 6-digit app PIN. Only a salted PBKDF2 hash is stored, never the digits. There is no
 * recovery (no server): forgetting it means clearing app data and restoring from Drive.
 */
class AppLock(context: Context) {
    private val sp = context.getSharedPreferences("vibediary_lock", Context.MODE_PRIVATE)

    val isEnabled: Boolean get() = sp.contains(KEY_HASH)

    fun setPin(pin: String) {
        require(isValid(pin))
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp.edit {
            putString(KEY_SALT, salt.b64())
            putString(KEY_HASH, hash(pin, salt).b64())
            remove(KEY_FAILS); remove(KEY_LOCKED_UNTIL)
        }
    }

    fun clear() = sp.edit { clear() }

    /** Milliseconds until another attempt is allowed (0 = may try now). */
    fun waitMillis(now: Long = System.currentTimeMillis()): Long =
        (sp.getLong(KEY_LOCKED_UNTIL, 0) - now).coerceAtLeast(0)

    /** Checks a PIN, counting failures: every [MAX_FAILS] wrong tries block input for 30 s. */
    fun verify(pin: String): Boolean {
        if (waitMillis() > 0) return false
        val salt = sp.getString(KEY_SALT, null)?.unb64() ?: return false
        val expected = sp.getString(KEY_HASH, null)?.unb64() ?: return false
        val ok = MessageDigest.isEqual(hash(pin, salt), expected)
        sp.edit {
            if (ok) {
                remove(KEY_FAILS)
            } else {
                val fails = sp.getInt(KEY_FAILS, 0) + 1
                if (fails >= MAX_FAILS) {
                    putInt(KEY_FAILS, 0)
                    putLong(KEY_LOCKED_UNTIL, System.currentTimeMillis() + LOCKOUT_MS)
                } else {
                    putInt(KEY_FAILS, fails)
                }
            }
        }
        return ok
    }

    companion object {
        const val LENGTH = 6
        /** Coming back after this long in the background asks for the PIN again. */
        const val RELOCK_AFTER_MS = 5 * 60_000L

        private const val MAX_FAILS = 5
        private const val LOCKOUT_MS = 30_000L
        private const val KEY_HASH = "pinHash"
        private const val KEY_SALT = "pinSalt"
        private const val KEY_FAILS = "pinFails"
        private const val KEY_LOCKED_UNTIL = "pinLockedUntil"

        fun isValid(pin: String) = pin.length == LENGTH && pin.all { it in '0'..'9' }

        private fun hash(pin: String, salt: ByteArray): ByteArray {
            val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        }

        private fun ByteArray.b64() = Base64.encodeToString(this, Base64.NO_WRAP)
        private fun String.unb64() = Base64.decode(this, Base64.NO_WRAP)
    }
}
