package com.matiassoto.ioslauncher.data

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * PIN, lock-mode and lost-phone settings. Stored in device-protected storage so the
 * SMS commands still work after a reboot before the phone is first unlocked.
 * Secrets (PIN, code word) are stored only as salted SHA-256 hashes.
 */
class SecurityStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.createDeviceProtectedStorageContext()
            .getSharedPreferences("security", Context.MODE_PRIVATE)

    private fun hash(salt: String, value: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest((salt + ":" + value).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun newSalt(): String {
        val b = ByteArray(16); SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    val hasPin: Boolean get() = prefs.contains("pin_hash")

    fun setPin(pin: String) {
        val salt = newSalt()
        prefs.edit().putString("pin_salt", salt).putString("pin_hash", hash(salt, pin)).apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString("pin_salt", null) ?: return false
        return prefs.getString("pin_hash", null) == hash(salt, pin)
    }

    var lockMode: Boolean
        get() = prefs.getBoolean("lock_mode", false)
        set(v) { prefs.edit().putBoolean("lock_mode", v).apply() }

    var kioskMode: Boolean
        get() = prefs.getBoolean("kiosk_mode", false)
        set(v) { prefs.edit().putBoolean("kiosk_mode", v).apply() }

    var lostPhoneEnabled: Boolean
        get() = prefs.getBoolean("lost_enabled", false)
        set(v) { prefs.edit().putBoolean("lost_enabled", v).apply() }

    val hasCodeWord: Boolean get() = prefs.contains("code_hash")

    fun setCodeWord(code: String) {
        val salt = newSalt()
        prefs.edit().putString("code_salt", salt)
            .putString("code_hash", hash(salt, code.trim().lowercase())).apply()
    }

    fun checkCodeWord(code: String): Boolean {
        val salt = prefs.getString("code_salt", null) ?: return false
        return prefs.getString("code_hash", null) == hash(salt, code.trim().lowercase())
    }
}
