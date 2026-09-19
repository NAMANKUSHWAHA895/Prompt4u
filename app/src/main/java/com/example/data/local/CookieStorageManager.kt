package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cookie-based client persistence manager for saved prompts, user audience target,
 * and admin authentication session.
 */
class CookieStorageManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(COOKIE_PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val COOKIE_PREFS_NAME = "ai_prompt_gallery_cookies"
        private const val COOKIE_AUDIENCE = "cookie_target_audience" // "Girls", "Boys", "Both"
        private const val COOKIE_ONBOARDING_DONE = "cookie_onboarding_completed"
        private const val COOKIE_SAVED_PROMPTS = "cookie_saved_prompts_json"
        private const val COOKIE_ADMIN_SESSION = "cookie_admin_session_active"
        private const val COOKIE_ADMIN_PASSKEY = "cookie_admin_master_passkey"
        private const val COOKIE_ADMIN_FAILED_ATTEMPTS = "cookie_admin_failed_attempts"
        private const val COOKIE_ADMIN_LOCKOUT_UNTIL = "cookie_admin_lockout_until"
        private const val COOKIE_LAST_ACTIVE = "cookie_last_active_timestamp"
        const val DEFAULT_ADMIN_PASSKEY = "PromptAdmin2026"
    }

    init {
        // Update session activity cookie
        prefs.edit().putLong(COOKIE_LAST_ACTIVE, System.currentTimeMillis()).apply()
    }

    // --- Audience Target Cookie ---

    fun getTargetAudience(): String {
        return prefs.getString(COOKIE_AUDIENCE, "Both") ?: "Both"
    }

    fun setTargetAudience(audience: String) {
        prefs.edit()
            .putString(COOKIE_AUDIENCE, audience)
            .putBoolean(COOKIE_ONBOARDING_DONE, true)
            .apply()
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(COOKIE_ONBOARDING_DONE, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(COOKIE_ONBOARDING_DONE, completed).apply()
    }

    // --- Saved Prompts Cookie ---

    fun getSavedPromptIds(): Set<Long> {
        val jsonStr = prefs.getString(COOKIE_SAVED_PROMPTS, "[]") ?: "[]"
        val ids = mutableSetOf<Long>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                ids.add(obj.getLong("postId"))
            }
        } catch (e: Exception) {
            // fallback
        }
        return ids
    }

    fun savePromptCookie(postId: Long, title: String, prompt: String, category: String) {
        val jsonStr = prefs.getString(COOKIE_SAVED_PROMPTS, "[]") ?: "[]"
        try {
            val array = JSONArray(jsonStr)
            var exists = false
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.getLong("postId") == postId) {
                    exists = true
                    break
                }
            }
            if (!exists) {
                val newObj = JSONObject().apply {
                    put("postId", postId)
                    put("title", title)
                    put("prompt", prompt)
                    put("category", category)
                    put("savedAt", System.currentTimeMillis())
                }
                array.put(newObj)
                prefs.edit().putString(COOKIE_SAVED_PROMPTS, array.toString()).apply()
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun removeSavedPromptCookie(postId: Long) {
        val jsonStr = prefs.getString(COOKIE_SAVED_PROMPTS, "[]") ?: "[]"
        try {
            val array = JSONArray(jsonStr)
            val newArray = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.getLong("postId") != postId) {
                    newArray.put(obj)
                }
            }
            prefs.edit().putString(COOKIE_SAVED_PROMPTS, newArray.toString()).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun getSavedPromptsCookieJson(): String {
        return prefs.getString(COOKIE_SAVED_PROMPTS, "[]") ?: "[]"
    }

    fun clearAllCookies() {
        prefs.edit().clear().apply()
    }

    // --- Admin Session Cookie & Security ---

    fun isAdminSessionActive(): Boolean {
        return prefs.getBoolean(COOKIE_ADMIN_SESSION, false)
    }

    fun setAdminSessionActive(active: Boolean) {
        prefs.edit().putBoolean(COOKIE_ADMIN_SESSION, active).apply()
    }

    fun getAdminPasskey(): String {
        return prefs.getString(COOKIE_ADMIN_PASSKEY, DEFAULT_ADMIN_PASSKEY) ?: DEFAULT_ADMIN_PASSKEY
    }

    fun setAdminPasskey(newPasskey: String) {
        prefs.edit().putString(COOKIE_ADMIN_PASSKEY, newPasskey).apply()
    }

    fun isCustomPasskeySet(): Boolean {
        return prefs.contains(COOKIE_ADMIN_PASSKEY)
    }

    fun getFailedAttempts(): Int {
        return prefs.getInt(COOKIE_ADMIN_FAILED_ATTEMPTS, 0)
    }

    fun recordFailedAttempt() {
        val attempts = getFailedAttempts() + 1
        val editor = prefs.edit().putInt(COOKIE_ADMIN_FAILED_ATTEMPTS, attempts)
        if (attempts >= 5) {
            // Lock out for 60 seconds
            editor.putLong(COOKIE_ADMIN_LOCKOUT_UNTIL, System.currentTimeMillis() + 60_000L)
        }
        editor.apply()
    }

    fun resetFailedAttempts() {
        prefs.edit()
            .putInt(COOKIE_ADMIN_FAILED_ATTEMPTS, 0)
            .putLong(COOKIE_ADMIN_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    fun getLockoutRemainingSeconds(): Long {
        val lockoutUntil = prefs.getLong(COOKIE_ADMIN_LOCKOUT_UNTIL, 0L)
        val now = System.currentTimeMillis()
        return if (lockoutUntil > now) {
            (lockoutUntil - now) / 1000L
        } else {
            0L
        }
    }
}
