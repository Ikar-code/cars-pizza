package fr.btssio.carspizza

import android.content.Context

/**
 * Réglages vocaux, stockés sur l'appareil uniquement (le dossier du projet ne contient jamais de clé).
 * Sans clé API Gemini, l'application utilise la reconnaissance vocale d'Android.
 */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("carspizza_settings", Context.MODE_PRIVATE)

    var geminiApiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var useGemini: Boolean
        get() = prefs.getBoolean(KEY_USE_GEMINI, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_GEMINI, value).apply()

    private companion object {
        const val KEY_API_KEY = "gemini_api_key"
        const val KEY_USE_GEMINI = "use_gemini"
    }
}
