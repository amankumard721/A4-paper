package com.a4sizepaper.typing.texttopdf.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.a4sizepaper.typing.texttopdf.domain.PageSettings
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
    val documentSort: String = "modified",
    val documentGrid: Boolean = true,
)

@Singleton
class PreferenceRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val language = stringPreferencesKey("language")
    private val theme = stringPreferencesKey("theme")
    private val onboarded = booleanPreferencesKey("onboarded")
    private val autosave = booleanPreferencesKey("autosave")
    private val defaults = stringPreferencesKey("defaults")
    private val documentSort = stringPreferencesKey("document_sort")
    private val documentGrid = booleanPreferencesKey("document_grid")
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
                    p[documentSort] ?: "modified",
                    p[documentGrid] ?: true,
                )
            }

    suspend fun updateLibrary(sort: String? = null, grid: Boolean? = null) {
        context.preferences.edit { prefs ->
            sort?.let { prefs[documentSort] = it }
            grid?.let { prefs[documentGrid] = it }
        }
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
