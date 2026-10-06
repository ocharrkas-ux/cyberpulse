package com.cyberpulse.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import com.cyberpulse.app.ui.theme.Fsociety
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyberpulse.app.data.db.Article
import com.cyberpulse.app.domain.NewsCategory
import com.cyberpulse.app.domain.NotifyMode
import com.cyberpulse.app.domain.Severity
import com.cyberpulse.app.domain.SystemType
import com.cyberpulse.app.domain.WatchLevel
import com.cyberpulse.app.ui.MainViewModel
import com.cyberpulse.app.ui.components.CustomKeywordRow
import com.cyberpulse.app.ui.components.TopicRuleList
import com.cyberpulse.app.work.AlertNotifier
import java.util.concurrent.TimeUnit

private val SharpSegment = RoundedCornerShape(2.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemsScreen(viewModel: MainViewModel, articles: List<Article>, modifier: Modifier = Modifier) {
    val watch by viewModel.watchLevels.collectAsStateWithLifecycle()
    val alertsEnabled by viewModel.alertsEnabled.collectAsStateWithLifecycle()
    val notifyMode by viewModel.notifyMode.collectAsStateWithLifecycle()
    val minSeverity by viewModel.minSeverity.collectAsStateWithLifecycle()
    val topicRules by viewModel.topicRules.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val ensureNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !AlertNotifier.canNotify(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // How many vulnerabilities from the last 7 days touch each system type.
    val weeklyCounts = remember(articles) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        articles.asSequence()
            .filter { it.category == NewsCategory.VULNERABILITIES && it.publishedAt >= cutoff }
            .flatMap { it.systemTypes.asSequence() }
            .groupingBy { it }
            .eachCount()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Fsociety.Panel), border = BorderStroke(1.dp, Fsociety.Line)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("// vulnerability alerts", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "> background sweep every ~2h",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = alertsEnabled,
                            onCheckedChange = {
                                viewModel.setAlertsEnabled(it)
                                if (it) ensureNotificationPermission()
                            },
                        )
                    }
                    if (alertsEnabled) {
                        Text("notify_on", style = MaterialTheme.typography.labelLarge)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            NotifyMode.entries.forEachIndexed { i, mode ->
                                SegmentedButton(
                                    selected = notifyMode == mode,
                                    onClick = { viewModel.setNotifyMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(i, NotifyMode.entries.size, SharpSegment),
                                ) { Text(mode.label.lowercase(), style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                        Text("min_severity", style = MaterialTheme.typography.labelLarge)
                        val severities = listOf<Severity?>(null, Severity.MEDIUM, Severity.HIGH, Severity.CRITICAL)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            severities.forEachIndexed { i, sev ->
                                SegmentedButton(
                                    selected = minSeverity == sev,
                                    onClick = { viewModel.setMinSeverity(sev) },
                                    shape = SegmentedButtonDefaults.itemShape(i, severities.size, SharpSegment),
                                ) { Text(sev?.label?.lowercase() ?: "any", style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                        Text(
                            "// actively exploited (CISA KEV) always alerts, regardless of severity",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Fsociety.Panel), border = BorderStroke(1.dp, Fsociety.Line)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("// briefing topics", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Flagged topics get their own briefing section. Suppressed ones are hidden from the briefing, the vulns tab and alerts, even on flagged systems.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TopicRuleList(topicRules, viewModel::setTopicLevel)
                    CustomKeywordRow(viewModel::setTopicLevel)
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                Text("// system types", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Flag what you run to highlight and alert on it. Suppress what you don't, to hide it " +
                        "from the vulnerability feed. An item is hidden only if every system it affects is suppressed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(SystemType.entries, key = { it.name }) { type ->
            SystemRow(
                type = type,
                level = watch[type] ?: WatchLevel.DEFAULT,
                weeklyCount = weeklyCounts[type] ?: 0,
                onChange = { level ->
                    viewModel.setWatchLevel(type, level)
                    if (level == WatchLevel.FLAGGED && alertsEnabled) ensureNotificationPermission()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SystemRow(type: SystemType, level: WatchLevel, weeklyCount: Int, onChange: (WatchLevel) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Fsociety.Panel), border = BorderStroke(1.dp, Fsociety.Line)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(type.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        type.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "$weeklyCount this week",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                WatchLevel.entries.forEachIndexed { i, option ->
                    SegmentedButton(
                        selected = level == option,
                        onClick = { onChange(option) },
                        shape = SegmentedButtonDefaults.itemShape(i, WatchLevel.entries.size, SharpSegment),
                    ) { Text(option.label.lowercase()) }
                }
            }
        }
    }
}
