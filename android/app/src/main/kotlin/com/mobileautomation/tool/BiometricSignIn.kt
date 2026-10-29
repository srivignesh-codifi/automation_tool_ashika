package com.mobileautomation.tool

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Biometric sign-in to this tool (not to an app under test). After a password sign-in the tester can turn it on: the
 * password is encrypted with an Android Keystore key that only a strong biometric (a fingerprint) unlocks, so it can
 * be read back only after the phone has recognised the tester, and the tool then signs in with it as usual. Enrolling
 * a new fingerprint on the phone invalidates the key, which turns biometric sign-in off until it is turned on again.
 *
 * Every answer is a map for the Flutter side: `ok`, and when not ok an `error` (cancelled, locked_out, invalidated,
 * unavailable, failed) with a `message` for the tester. The password is never logged.
 */
class BiometricSignIn(private val activity: Activity) {

    private companion object {
        const val KEY_ALIAS = "tool_biometric_sign_in"
        const val PREFS = "biometric_sign_in"
        const val USER = "user"
        const val DATA = "data"
        const val IV = "iv"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    private val prefs get() = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Whether this phone can do it, whether it is turned on, and for which user. */
    fun status(): Map<String, Any?> {
        val code = canAuthenticate()
        val enabled = prefs.contains(DATA) && key() != null
        return mapOf(
            "available" to (code == BiometricManager.BIOMETRIC_SUCCESS),
            "enabled" to enabled,
            "userId" to if (enabled) prefs.getString(USER, null) else null,
            "reason" to unavailableReason(code),
        )
    }

    /** Asks for a fingerprint and keeps [password] for [userId], encrypted under it. */
    fun enable(userId: String, password: String, done: (Map<String, Any?>) -> Unit) {
        unavailableReason(canAuthenticate())?.let { return done(error("unavailable", it)) }
        val cipher = try {
            deleteKey()
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, createKey()) }
        } catch (e: Exception) {
            return done(error("failed", "The phone's key store refused a biometric key (${e.javaClass.simpleName})."))
        }
        prompt("Turn on biometric sign-in", "Confirm your fingerprint to sign in with it next time", cipher) { unlocked, failure ->
            if (unlocked == null) return@prompt done(failure!!)
            try {
                val data = unlocked.doFinal(password.toByteArray(Charsets.UTF_8))
                prefs.edit()
                    .putString(USER, userId)
                    .putString(DATA, Base64.encodeToString(data, Base64.NO_WRAP))
                    .putString(IV, Base64.encodeToString(unlocked.iv, Base64.NO_WRAP))
                    .apply()
                done(mapOf("ok" to true, "userId" to userId))
            } catch (e: Exception) {
                disable()
                done(error("failed", "Biometric sign-in could not be turned on (${e.javaClass.simpleName})."))
            }
        }
    }

    /** Asks for a fingerprint and gives back the user id and password kept by [enable]. */
    fun unlock(done: (Map<String, Any?>) -> Unit) {
        val user = prefs.getString(USER, null)
        val data = prefs.getString(DATA, null)
        val iv = prefs.getString(IV, null)
        val key = key()
        if (user == null || data == null || iv == null || key == null) {
            disable()
            return done(error("unavailable", "Biometric sign-in is not turned on. Sign in with your password."))
        }
        val cipher = try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            }
        } catch (_: KeyPermanentlyInvalidatedException) {
            disable()
            return done(
                error(
                    "invalidated",
                    "The fingerprints on this phone have changed, so biometric sign-in was turned off. Sign in with your " +
                        "password and turn it on again.",
                ),
            )
        } catch (e: Exception) {
            disable()
            return done(error("failed", "Biometric sign-in could not be used (${e.javaClass.simpleName}). Sign in with your password."))
        }
        prompt("Sign in to Automation Tool", "Signing in as $user", cipher) { unlocked, failure ->
            if (unlocked == null) return@prompt done(failure!!)
            try {
                val password = String(unlocked.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
                done(mapOf("ok" to true, "userId" to user, "password" to password))
            } catch (e: Exception) {
                disable()
                done(error("failed", "The saved sign-in could not be read (${e.javaClass.simpleName}). Sign in with your password."))
            }
        }
    }

    /** Forgets the kept password and its key. */
    fun disable() {
        prefs.edit().clear().apply()
        deleteKey()
    }

    // ── The prompt ──────────────────────────────────────────────────────────────────────────

    /** Shows Android's biometric prompt for [cipher]; [done] gets the unlocked cipher, or null and why. */
    private fun prompt(title: String, subtitle: String, cipher: Cipher, done: (Cipher?, Map<String, Any?>?) -> Unit) {
        var answered = false
        fun answer(unlocked: Cipher?, failure: Map<String, Any?>?) {
            if (answered) return
            answered = true
            done(unlocked, failure)
        }
        val executor = activity.mainExecutor
        val builder = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setConfirmationRequired(false)
            .setNegativeButton("Use password", executor) { _, _ ->
                answer(null, error("cancelled", "Biometric sign-in was cancelled."))
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) builder.setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val unlocked = result.cryptoObject?.cipher
                answer(unlocked, if (unlocked == null) error("failed", "The phone did not unlock the sign-in key.") else null)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                answer(null, promptError(errorCode, errString))
            }

            // A finger that was not recognised: the prompt says so itself and lets the tester try again.
            override fun onAuthenticationFailed() = Unit
        }
        try {
            builder.build().authenticate(BiometricPrompt.CryptoObject(cipher), CancellationSignal(), executor, callback)
        } catch (e: Exception) {
            answer(null, error("failed", "The biometric prompt could not be shown (${e.javaClass.simpleName})."))
        }
    }

    private fun promptError(code: Int, text: CharSequence): Map<String, Any?> = when (code) {
        // "Use password" is answered by its own listener; these are Back, a tap outside, or the app leaving the screen.
        BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED,
        BiometricPrompt.BIOMETRIC_ERROR_CANCELED,
        -> error("cancelled", "Biometric sign-in was cancelled.")
        BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT,
        BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT,
        -> error("locked_out", "Too many attempts: the fingerprint sensor is locked for now. Sign in with your password.")
        BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS ->
            error("unavailable", "No fingerprint is enrolled on this phone any more. Sign in with your password.")
        else -> error("failed", text.toString().ifBlank { "Biometric sign-in failed (error $code)." })
    }

    private fun error(code: String, message: String): Map<String, Any?> = mapOf("ok" to false, "error" to code, "message" to message)

    // ── The phone's support ─────────────────────────────────────────────────────────────────

    private fun canAuthenticate(): Int {
        val manager = activity.getSystemService(BiometricManager::class.java) ?: return BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.canAuthenticate(Authenticators.BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            manager.canAuthenticate()
        }
    }

    /** Null when the phone can do it; otherwise what the tester has to do about it. */
    private fun unavailableReason(code: Int): String? = when (code) {
        BiometricManager.BIOMETRIC_SUCCESS -> null
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No fingerprint is enrolled on this phone. Add one in Settings > Security first."
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "This phone has no fingerprint sensor that apps can use."
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "The fingerprint sensor is not available right now."
        else -> "This phone's biometrics cannot be used for sign-in (code $code)."
    }

    // ── The key ─────────────────────────────────────────────────────────────────────────────

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey? = try {
        keyStore().getKey(KEY_ALIAS, null) as? SecretKey
    } catch (_: Exception) {
        null
    }

    private fun deleteKey() {
        try {
            keyStore().deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
        }
    }

    /** An AES key usable only right after a strong biometric, and lost when the phone's fingerprints change. */
    private fun createKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(spec)
            generateKey()
        }
    }
}
