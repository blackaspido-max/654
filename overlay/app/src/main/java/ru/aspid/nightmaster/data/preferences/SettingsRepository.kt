package ru.aspid.nightmaster.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.nightMasterSettings by preferencesDataStore(name = "night_master_settings")

class SettingsRepository(private val context: Context) {
    val autoLoadSelectedModel: Flow<Boolean> = values { preferences ->
        preferences[AUTO_LOAD_SELECTED_MODEL] ?: true
    }

    val darkThemeEnabled: Flow<Boolean> = values { preferences ->
        preferences[DARK_THEME_ENABLED] ?: true
    }

    suspend fun setAutoLoadSelectedModel(enabled: Boolean) {
        context.nightMasterSettings.edit { it[AUTO_LOAD_SELECTED_MODEL] = enabled }
    }

    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        context.nightMasterSettings.edit { it[DARK_THEME_ENABLED] = enabled }
    }

    private fun <T> values(transform: (Preferences) -> T): Flow<T> =
        context.nightMasterSettings.data
            .catch { error ->
                if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
                else throw error
            }
            .map(transform)

    private companion object {
        val AUTO_LOAD_SELECTED_MODEL = booleanPreferencesKey("auto_load_selected_model")
        val DARK_THEME_ENABLED = booleanPreferencesKey("dark_theme_enabled")
    }
}
