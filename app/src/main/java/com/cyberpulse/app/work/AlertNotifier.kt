package com.cyberpulse.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cyberpulse.app.MainActivity
import com.cyberpulse.app.R
import com.cyberpulse.app.data.Settings
import com.cyberpulse.app.data.db.Article
import com.cyberpulse.app.domain.VulnPolicy
import java.util.concurrent.TimeUnit

object AlertNotifier {
    private const val CHANNEL_ID = "vuln_alerts"
    private const val NOTIFICATION_ID = 1001
    const val EXTRA_OPEN_VULNS = "open_vulns"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Vulnerability alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "New vulnerabilities affecting the systems you flagged" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notifyNewVulnerabilities(context: Context, newItems: List<Article>, settings: Settings) {
        if (!settings.alertsEnabled.value || !canNotify(context)) return
        val recentCutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val watch = settings.watchLevels.value
        val matches = newItems
            .filter { it.publishedAt >= recentCutoff }
            .filter { VulnPolicy.shouldNotify(it, watch, settings.notifyMode.value, settings.minSeverity.value) }
            .sortedWith(compareByDescending<Article> { it.knownExploited }.thenByDescending { it.cvssScore ?: 0.0 })
        if (matches.isEmpty()) return

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_VULNS, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)

        if (matches.size == 1) {
            val item = matches.first()
            builder
                .setContentTitle(headerLine(item))
                .setContentText(item.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText("${item.title}\n\n${item.summary.take(300)}"))
        } else {
            val style = NotificationCompat.InboxStyle()
            matches.take(6).forEach { style.addLine("${headerLine(it)} — ${it.title}") }
            if (matches.size > 6) style.setSummaryText("+${matches.size - 6} more")
            builder
                .setContentTitle("${matches.size} new vulnerabilities for your systems")
                .setContentText(matches.joinToString(", ") { it.cveIds.firstOrNull() ?: it.source })
                .setStyle(style)
                .setNumber(matches.size)
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    private fun headerLine(item: Article): String = buildString {
        append(item.cveIds.firstOrNull() ?: item.source)
        item.severity?.let { append(" · ").append(it.label) }
        if (item.knownExploited) append(" · Exploited")
        item.systemTypes.firstOrNull()?.let { append(" · ").append(it.label) }
    }
}
