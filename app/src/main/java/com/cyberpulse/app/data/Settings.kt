package com.cyberpulse.app.data

import android.content.Context
import androidx.core.content.edit
import com.cyberpulse.app.domain.NotifyMode
import com.cyberpulse.app.domain.Severity
import com.cyberpulse.app.domain.SystemType
import com.cyberpulse.app.domain.WatchLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("cyberpulse_settings", Context.MODE_PRIVATE)

    private val _watchLevels = MutableStateFlow(loadWatchLevels())
    val watchLevels: StateFlow<Map<SystemType, WatchLevel>> = _watchLevels.asStateFlow()

    private val _alertsEnabled = MutableStateFlow(prefs.getBoolean(KEY_ALERTS, true))
    val alertsEnabled: StateFlow<Boolean> = _alertsEnabled.asStateFlow()

    private val _notifyMode = MutableStateFlow(
        enumOrNull<NotifyMode>(prefs.getString(KEY_MODE, null)) ?: NotifyMode.FLAGGED_ONLY
    )
    val notifyMode: StateFlow<NotifyMode> = _notifyMode.asStateFlow()

    // Absent key -> HIGH; explicit "ANY" -> null (no threshold).
    private val _minSeverity = MutableStateFlow(
        when (val raw = prefs.getString(KEY_MIN_SEVERITY, null)) {
            null -> Severity.HIGH
            ANY -> null
            else -> enumOrNull<Severity>(raw) ?: Severity.HIGH
        }
    )
    val minSeverity: StateFlow<Severity?> = _minSeverity.asStateFlow()

    private val _speechRate = MutableStateFlow(prefs.getFloat(KEY_SPEECH_RATE, 1f))
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _briefingEnabled = MutableStateFlow(prefs.getBoolean(KEY_BRIEFING_ENABLED, true))
    val briefingEnabled: StateFlow<Boolean> = _briefingEnabled.asStateFlow()

    /** Minutes after midnight, local time. Default 07:30. */
    private val _briefingTime = MutableStateFlow(prefs.getInt(KEY_BRIEFING_TIME, 7 * 60 + 30))
    val briefingTime: StateFlow<Int> = _briefingTime.asStateFlow()

    var lastRefresh: Long
        get() = prefs.getLong(KEY_LAST_REFRESH, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_REFRESH, value) }

    fun setWatchLevel(type: SystemType, level: WatchLevel) {
        prefs.edit {
            if (level == WatchLevel.DEFAULT) remove(watchKey(type)) else putString(watchKey(type), level.name)
        }
        _watchLevels.value = loadWatchLevels()
    }

    fun setAlertsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ALERTS, enabled) }
        _alertsEnabled.value = enabled
    }

    fun setNotifyMode(mode: NotifyMode) {
        prefs.edit { putString(KEY_MODE, mode.name) }
        _notifyMode.value = mode
    }

    fun setMinSeverity(severity: Severity?) {
        prefs.edit { putString(KEY_MIN_SEVERITY, severity?.name ?: ANY) }
        _minSeverity.value = severity
    }

    fun setSpeechRate(rate: Float) {
        prefs.edit { putFloat(KEY_SPEECH_RATE, rate) }
        _speechRate.value = rate
    }

    fun setBriefingEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BRIEFING_ENABLED, enabled) }
        _briefingEnabled.value = enabled
    }

    fun setBriefingTime(minutesOfDay: Int) {
        prefs.edit { putInt(KEY_BRIEFING_TIME, minutesOfDay) }
        _briefingTime.value = minutesOfDay
    }

    private fun loadWatchLevels(): Map<SystemType, WatchLevel> =
        SystemType.entries.associateWith { type ->
            enumOrNull<WatchLevel>(prefs.getString(watchKey(type), null)) ?: WatchLevel.DEFAULT
        }

    private fun watchKey(type: SystemType) = "watch_${type.name}"

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

    private companion object {
        const val KEY_ALERTS = "alerts_enabled"
        const val KEY_MODE = "notify_mode"
        const val KEY_MIN_SEVERITY = "min_severity"
        const val KEY_LAST_REFRESH = "last_refresh"
        const val KEY_SPEECH_RATE = "speech_rate"
        const val KEY_BRIEFING_ENABLED = "briefing_enabled"
        const val KEY_BRIEFING_TIME = "briefing_time"
        const val ANY = "ANY"
    }
}
