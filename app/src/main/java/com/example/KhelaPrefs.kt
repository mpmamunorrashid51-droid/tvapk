package com.example

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class KhelaPrefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("khela365_prefs", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    
    companion object {
        private const val KEY_APP_NAME = "app_name"
        private const val KEY_THEME_PRESET = "theme_preset"
        private const val KEY_BANNER_AD = "is_banner_ad_enabled"
        private const val KEY_POPUNDER_AD = "is_popunder_ad_enabled"
        private const val KEY_REWARDED_PASS = "is_rewarded_pass_enabled"
        private const val KEY_REWARD_DURATION = "reward_pass_duration_hours"
        private const val KEY_BANNER_ID = "adsterra_banner_id"
        private const val KEY_SMARTLINK_URL = "adsterra_smartlink_url"
        private const val KEY_PASS_EXPIRY = "khela365_pass_expiry"
        private const val KEY_CUSTOM_CHANNELS = "custom_channels_json"
    }

    var appName: String
        get() = prefs.getString(KEY_APP_NAME, "Khela365") ?: "Khela365"
        set(value) = prefs.edit().putString(KEY_APP_NAME, value).apply()

    var themePreset: String
        get() = prefs.getString(KEY_THEME_PRESET, "neon") ?: "neon"
        set(value) = prefs.edit().putString(KEY_THEME_PRESET, value).apply()

    var isBannerAdEnabled: Boolean
        get() = prefs.getBoolean(KEY_BANNER_AD, true)
        set(value) = prefs.edit().putBoolean(KEY_BANNER_AD, value).apply()

    var isPopunderAdEnabled: Boolean
        get() = prefs.getBoolean(KEY_POPUNDER_AD, true)
        set(value) = prefs.edit().putBoolean(KEY_POPUNDER_AD, value).apply()

    var isRewardedPassEnabled: Boolean
        get() = prefs.getBoolean(KEY_REWARDED_PASS, true)
        set(value) = prefs.edit().putBoolean(KEY_REWARDED_PASS, value).apply()

    var rewardPassDurationHours: Int
        get() = prefs.getInt(KEY_REWARD_DURATION, 12)
        set(value) = prefs.edit().putInt(KEY_REWARD_DURATION, value).apply()

    var adsterraBannerId: String
        get() = prefs.getString(KEY_BANNER_ID, "ax769wqp") ?: "ax769wqp"
        set(value) = prefs.edit().putString(KEY_BANNER_ID, value).apply()

    var adsterraSmartlinkUrl: String
        get() = prefs.getString(KEY_SMARTLINK_URL, "https://adsterra-direct-link.com/xyz") ?: "https://adsterra-direct-link.com/xyz"
        set(value) = prefs.edit().putString(KEY_SMARTLINK_URL, value).apply()

    var passExpiry: Long
        get() = prefs.getLong(KEY_PASS_EXPIRY, 0L)
        set(value) = prefs.edit().putLong(KEY_PASS_EXPIRY, value).apply()

    fun isPassActive(): Boolean {
        return System.currentTimeMillis() < passExpiry
    }

    fun activatePass() {
        val durationMs = rewardPassDurationHours * 60 * 60 * 1000L
        passExpiry = System.currentTimeMillis() + durationMs
    }

    fun clearPass() {
        passExpiry = 0L
    }

    // Custom Channels management
    fun getCustomChannels(): List<LiveChannel> {
        val json = prefs.getString(KEY_CUSTOM_CHANNELS, null) ?: return emptyList()
        return try {
            val type = Types.newParameterizedType(List::class.java, LiveChannel::class.java)
            val adapter = moshi.adapter<List<LiveChannel>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomChannels(channels: List<LiveChannel>) {
        val type = Types.newParameterizedType(List::class.java, LiveChannel::class.java)
        val adapter = moshi.adapter<List<LiveChannel>>(type)
        val json = adapter.toJson(channels)
        prefs.edit().putString(KEY_CUSTOM_CHANNELS, json).apply()
    }
}
