package com.a4lekhak.documentmaker.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.a4lekhak.documentmaker.domain.PageSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.preferences by preferencesDataStore("preferences")

data class AppPreferences(
    val language: String = "en",
    val theme: String = "system",
    val onboarded: Boolean = false,
    val autosave: Boolean = true,
    val defaults: PageSettings = PageSettings(),
)

@Singleton
class PreferenceRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val language = stringPreferencesKey("language")
    private val theme = stringPreferencesKey("theme")
    private val onboarded = booleanPreferencesKey("onboarded")
    private val autosave = booleanPreferencesKey("autosave")
    private val defaults = stringPreferencesKey("defaults")
    val flow =
        context.preferences.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { p ->
                AppPreferences(
                    p[language] ?: "en",
                    p[theme] ?: "system",
                    p[onboarded] ?: false,
                    p[autosave] ?: true,
                    p[defaults]?.let {
                        runCatching { PageSettings.fromJson(JSONObject(it)) }.getOrNull()
                    } ?: PageSettings(),
                )
            }

    suspend fun update(value: AppPreferences) {
        context.preferences.edit { p ->
            p[language] = value.language
            p[theme] = value.theme
            p[onboarded] = value.onboarded
            p[autosave] = value.autosave
            p[defaults] = value.defaults.validate().toJson().toString()
        }
    }
}
