package com.cyberpulse.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cyberpulse.app.appGraph
import com.cyberpulse.app.data.Settings
import com.cyberpulse.app.domain.BriefingBuilder
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Builds the daily briefing at the user's chosen time and posts it as a notification. */
class DailyBriefingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = applicationContext.appGraph
        if (!graph.settings.briefingEnabled.value) return Result.success()
        // Best effort: brief from fresh data, but fall back to what's cached if offline.
        runCatching { graph.repository.refresh() }
        val now = System.currentTimeMillis()
        val recent = graph.database.articleDao().getSince(now - BriefingBuilder.WINDOW_MILLIS)
        val briefing = BriefingBuilder.build(recent, graph.settings.watchLevels.value, now, topics = graph.settings.topicRules.value)
        if (!briefing.isEmpty) AlertNotifier.notifyDailyBriefing(applicationContext, briefing)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "cyberpulse-daily-briefing"

        /** @param replace true when the user changed the time, so the schedule restarts from the new time. */
        fun schedule(context: Context, settings: Settings, replace: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!settings.briefingEnabled.value) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val minutes = settings.briefingTime.value
            val request = PeriodicWorkRequestBuilder<DailyBriefingWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntil(minutes / 60, minutes % 60).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        private fun delayUntil(hour: Int, minute: Int): Duration {
            val now = ZonedDateTime.now()
            var next = now.with(LocalTime.of(hour, minute))
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next)
        }
    }
}
