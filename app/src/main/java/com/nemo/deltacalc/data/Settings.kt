package com.nemo.deltacalc.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.themeStore: DataStore<Preferences> by preferencesDataStore(name = "delta_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class KeypadStyle { FILLED, OUTLINED, FLAT }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 种子色，ARGB */
    val seedColor: Long = 0xFF6750A4,
    val dynamicColor: Boolean = false,
    /** 键盘与卡片圆角，dp */
    val cornerRadius: Float = 22f,
    val fontScale: Float = 1f,
    val haptic: Boolean = true,
    /** 分数结果是否附带小数近似 */
    val showDecimalApprox: Boolean = true,
    val keypadStyle: KeypadStyle = KeypadStyle.FILLED,
)

/** 主题与偏好的持久化。全部本地，不联网。 */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.themeStore

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                themeMode = runCatching {
                    ThemeMode.valueOf(p[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.name)
                }.getOrDefault(ThemeMode.SYSTEM),
                seedColor = p[KEY_SEED] ?: 0xFF6750A4,
                dynamicColor = p[KEY_DYNAMIC] ?: false,
                cornerRadius = p[KEY_RADIUS] ?: 22f,
                fontScale = p[KEY_FONT_SCALE] ?: 1f,
                haptic = p[KEY_HAPTIC] ?: true,
                showDecimalApprox = p[KEY_APPROX] ?: true,
                keypadStyle = runCatching {
                    KeypadStyle.valueOf(p[KEY_KEYPAD] ?: KeypadStyle.FILLED.name)
                }.getOrDefault(KeypadStyle.FILLED),
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[KEY_THEME_MODE] = mode.name }
    suspend fun setSeedColor(color: Long) = store.edit { it[KEY_SEED] = color }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[KEY_DYNAMIC] = enabled }
    suspend fun setCornerRadius(dp: Float) = store.edit { it[KEY_RADIUS] = dp }
    suspend fun setFontScale(scale: Float) = store.edit { it[KEY_FONT_SCALE] = scale }
    suspend fun setHaptic(enabled: Boolean) = store.edit { it[KEY_HAPTIC] = enabled }
    suspend fun setShowDecimalApprox(enabled: Boolean) = store.edit { it[KEY_APPROX] = enabled }
    suspend fun setKeypadStyle(style: KeypadStyle) = store.edit { it[KEY_KEYPAD] = style.name }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_SEED = longPreferencesKey("seed_color")
        val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEY_RADIUS = floatPreferencesKey("corner_radius")
        val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        val KEY_HAPTIC = booleanPreferencesKey("haptic")
        val KEY_APPROX = booleanPreferencesKey("decimal_approx")
        val KEY_KEYPAD = stringPreferencesKey("keypad_style")
    }
}
