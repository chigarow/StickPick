package com.avishkar.stickpick.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stickpick_prefs")

class PreferencesManager(private val context: Context) {
    companion object {
        val BOT_TOKEN = stringPreferencesKey("bot_token")
        val AUTHOR_NAME = stringPreferencesKey("author_name")
        val PACK_NAMING_PATTERN = stringPreferencesKey("pack_naming_pattern")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val AUTO_SPLIT = booleanPreferencesKey("auto_split")
        val LOSSLESS_CONVERSION = booleanPreferencesKey("lossless_conversion")
        val PACK_LIMIT = intPreferencesKey("pack_limit")
        val THEME_MODE = stringPreferencesKey("theme_mode") // "light", "dark", "system"
        val WHATSAPP_BUSINESS = booleanPreferencesKey("whatsapp_business")
    }

    val botToken: Flow<String> = context.dataStore.data.map { it[BOT_TOKEN] ?: "" }
    val authorName: Flow<String> = context.dataStore.data.map { it[AUTHOR_NAME] ?: "" }
    val packNamingPattern: Flow<String> = context.dataStore.data.map { it[PACK_NAMING_PATTERN] ?: "{name}_by_{author}" }
    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }
    val autoSplit: Flow<Boolean> = context.dataStore.data.map { it[AUTO_SPLIT] ?: true }
    val losslessConversion: Flow<Boolean> = context.dataStore.data.map { it[LOSSLESS_CONVERSION] ?: false }
    val packLimit: Flow<Int> = context.dataStore.data.map { it[PACK_LIMIT] ?: 30 }
    val themeMode: Flow<String> = context.dataStore.data.map { it[THEME_MODE] ?: "system" }
    val whatsappBusiness: Flow<Boolean> = context.dataStore.data.map { it[WHATSAPP_BUSINESS] ?: false }

    suspend fun saveBotToken(token: String) {
        context.dataStore.data.map { it[BOT_TOKEN] }
        context.dataStore.edit { it[BOT_TOKEN] = token }
    }

    suspend fun saveAuthorName(name: String) {
        context.dataStore.edit { it[AUTHOR_NAME] = name }
    }

    suspend fun savePackNamingPattern(pattern: String) {
        context.dataStore.edit { it[PACK_NAMING_PATTERN] = pattern }
    }

    suspend fun setOnboardingComplete() {
        context.dataStore.edit { it[ONBOARDING_COMPLETE] = true }
    }

    suspend fun saveAutoSplit(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_SPLIT] = enabled }
    }

    suspend fun saveLosslessConversion(enabled: Boolean) {
        context.dataStore.edit { it[LOSSLESS_CONVERSION] = enabled }
    }

    suspend fun savePackLimit(limit: Int) {
        context.dataStore.edit { it[PACK_LIMIT] = limit.coerceIn(3, 50) }
    }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun saveWhatsAppBusiness(enabled: Boolean) {
        context.dataStore.edit { it[WHATSAPP_BUSINESS] = enabled }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
