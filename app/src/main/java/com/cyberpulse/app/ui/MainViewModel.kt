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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class Tab { NEWS, VULNS, SYSTEMS }

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

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _tab = MutableStateFlow(Tab.NEWS)
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

    fun setWatchLevel(type: SystemType, level: WatchLevel) = settings.setWatchLevel(type, level)
    fun setAlertsEnabled(enabled: Boolean) = settings.setAlertsEnabled(enabled)
    fun setNotifyMode(mode: NotifyMode) = settings.setNotifyMode(mode)
    fun setMinSeverity(severity: Severity?) = settings.setMinSeverity(severity)
}
