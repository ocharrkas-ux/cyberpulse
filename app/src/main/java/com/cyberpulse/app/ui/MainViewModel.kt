package com.cyberpulse.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyberpulse.app.appGraph
import com.cyberpulse.app.data.db.Article
import com.cyberpulse.app.domain.NotifyMode
import com.cyberpulse.app.domain.Severity
import com.cyberpulse.app.domain.SystemType
import com.cyberpulse.app.domain.WatchLevel
import com.cyberpulse.app.domain.Briefing
import com.cyberpulse.app.domain.BriefingBuilder
import com.cyberpulse.app.domain.BriefingScript
import com.cyberpulse.app.domain.SpeechSegment
import com.cyberpulse.app.tts.BriefingSpeaker
import com.cyberpulse.app.work.DailyBriefingWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class Tab { BRIEF, NEWS, VULNS, SYSTEMS }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = app.appGraph
    private val repository = graph.repository
    private val settings = graph.settings

    val articles: StateFlow<List<Article>> = repository.observeArticles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val watchLevels = settings.watchLevels
    val alertsEnabled = settings.alertsEnabled
    val notifyMode = settings.notifyMode
    val minSeverity = settings.minSeverity

    val speechRate = settings.speechRate
    val briefingEnabled = settings.briefingEnabled
    val briefingTime = settings.briefingTime

    private val speaker = graph.speaker
    val speakerState: StateFlow<BriefingSpeaker.State> = speaker.state

    // Only the last day matters for the briefing; don't load the whole table for it.
    val briefing: StateFlow<Briefing?> = combine(
        repository.observeSince(System.currentTimeMillis() - BriefingBuilder.WINDOW_MILLIS),
        watchLevels,
    ) { recent, watch ->
        BriefingBuilder.build(recent, watch)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The script currently loaded into the speaker, used to map the spoken segment back to a card. */
    private val playingScript = MutableStateFlow<List<SpeechSegment>>(emptyList())

    val speakingItemKey: StateFlow<String?> = combine(speaker.state, playingScript) { state, script ->
        val active = state.status == BriefingSpeaker.Status.SPEAKING || state.status == BriefingSpeaker.Status.PAUSED
        if (active) script.getOrNull(state.index)?.itemKey else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val speakingText: StateFlow<String?> = combine(speaker.state, playingScript) { state, script ->
        script.getOrNull(state.index)?.text
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _tab = MutableStateFlow(Tab.BRIEF)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    init {
        val stale = System.currentTimeMillis() - settings.lastRefresh > TimeUnit.MINUTES.toMillis(15)
        if (stale) refresh()
    }

    fun selectTab(tab: Tab) {
        _tab.value = tab
    }

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                val result = repository.refresh()
                if (result.failedSources.isNotEmpty()) {
                    _message.value = "> connection refused: ${result.failedSources.joinToString()}"
                }
            } catch (e: Exception) {
                _message.value = "> sync failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun markRead(article: Article) {
        if (!article.isRead) viewModelScope.launch { repository.markRead(article.id) }
    }

    // ---- briefing playback ----

    fun playBriefing() {
        val b = briefing.value ?: return
        val script = BriefingScript.build(b)
        playingScript.value = script
        speaker.play(script.map { it.text })
    }

    /** Called from the notification's "Listen" action: wait for data to load, then read. */
    fun playBriefingWhenReady() {
        selectTab(Tab.BRIEF)
        viewModelScope.launch {
            withTimeoutOrNull(10_000) { briefing.first { it != null && !it.isEmpty } }
            playBriefing()
        }
    }

    fun togglePlayback() {
        when (speakerState.value.status) {
            BriefingSpeaker.Status.SPEAKING -> speaker.pause()
            BriefingSpeaker.Status.PAUSED -> if (playingScript.value.isEmpty()) playBriefing() else speaker.resume()
            BriefingSpeaker.Status.INITIALIZING -> speaker.pause()
            else -> playBriefing()
        }
    }

    fun stopPlayback() = speaker.stop()
    fun nextSegment() = speaker.next()
    fun previousSegment() = speaker.previous()

    /** Start reading from a specific card. */
    fun playFrom(itemKey: String) {
        val script = BriefingScript.build(briefing.value ?: return)
        val index = script.indexOfFirst { it.itemKey == itemKey }.takeIf { it >= 0 } ?: return
        playingScript.value = script
        speaker.play(script.map { it.text }, from = index)
    }

    fun cycleSpeechRate() {
        val rates = listOf(0.8f, 1.0f, 1.25f, 1.5f, 1.75f)
        val next = rates.firstOrNull { it > speechRate.value + 0.01f } ?: rates.first()
        settings.setSpeechRate(next)
        speaker.setRate(next)
    }

    fun setBriefingEnabled(enabled: Boolean) {
        settings.setBriefingEnabled(enabled)
        DailyBriefingWorker.schedule(getApplication(), settings, replace = true)
    }

    fun setBriefingTime(minutesOfDay: Int) {
        settings.setBriefingTime(minutesOfDay)
        DailyBriefingWorker.schedule(getApplication(), settings, replace = true)
    }

    fun setWatchLevel(type: SystemType, level: WatchLevel) = settings.setWatchLevel(type, level)
    fun setAlertsEnabled(enabled: Boolean) = settings.setAlertsEnabled(enabled)
    fun setNotifyMode(mode: NotifyMode) = settings.setNotifyMode(mode)
    fun setMinSeverity(severity: Severity?) = settings.setMinSeverity(severity)
}
