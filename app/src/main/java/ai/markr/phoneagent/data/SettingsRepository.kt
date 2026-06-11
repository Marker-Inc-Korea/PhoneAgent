package ai.markr.phoneagent.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "phoneagent_settings")

/** Persists [AgentSettings]; the API key is encrypted via [KeystoreCrypto]. */
class SettingsRepository(
    private val context: Context,
    private val crypto: KeystoreCrypto = KeystoreCrypto(),
) {
    private object Keys {
        val PROVIDER = stringPreferencesKey("provider")
        val API_KEY = stringPreferencesKey("api_key_enc")
        val BASE_URL = stringPreferencesKey("base_url")
        val TEXT_MODEL = stringPreferencesKey("text_model")
        val VISION_MODEL = stringPreferencesKey("vision_model")
        val MAX_STEPS = intPreferencesKey("max_steps")
    }

    val settings: Flow<AgentSettings> = context.dataStore.data.map { prefs ->
        val provider = prefs[Keys.PROVIDER]
            ?.let { runCatching { LlmProvider.valueOf(it) }.getOrNull() }
            ?: LlmProvider.ANTHROPIC
        AgentSettings(
            provider = provider,
            apiKey = prefs[Keys.API_KEY]?.let { crypto.decrypt(it) }.orEmpty(),
            baseUrl = prefs[Keys.BASE_URL] ?: AgentSettings.defaultBaseUrl(provider),
            textModel = prefs[Keys.TEXT_MODEL].orEmpty(),
            visionModel = prefs[Keys.VISION_MODEL].orEmpty(),
            maxSteps = prefs[Keys.MAX_STEPS] ?: 20,
        )
    }

    suspend fun current(): AgentSettings = settings.first()

    suspend fun save(s: AgentSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROVIDER] = s.provider.name
            prefs[Keys.API_KEY] = crypto.encrypt(s.apiKey)
            prefs[Keys.BASE_URL] = s.baseUrl
            prefs[Keys.TEXT_MODEL] = s.textModel
            prefs[Keys.VISION_MODEL] = s.visionModel
            prefs[Keys.MAX_STEPS] = s.maxSteps
        }
    }
}
