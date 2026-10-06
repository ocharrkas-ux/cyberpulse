package com.cyberpulse.app

import android.app.Application
import com.cyberpulse.app.work.AlertNotifier
import com.cyberpulse.app.work.DailyBriefingWorker
import com.cyberpulse.app.work.RefreshWorker

class CyberPulseApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        AlertNotifier.createChannels(this)
        RefreshWorker.schedule(this)
        DailyBriefingWorker.schedule(this, graph.settings, replace = false)
    }
}
