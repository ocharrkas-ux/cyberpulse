package com.cyberpulse.app.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyberpulse.app.domain.Briefing
import com.cyberpulse.app.domain.BriefingItem
import com.cyberpulse.app.domain.BriefingSection
import com.cyberpulse.app.domain.SectionKind
import com.cyberpulse.app.tts.BriefingSpeaker
import com.cyberpulse.app.ui.MainViewModel
import com.cyberpulse.app.ui.components.Pill
import com.cyberpulse.app.ui.theme.ExploitedColor
import com.cyberpulse.app.ui.theme.Fsociety
import com.cyberpulse.app.ui.theme.color
import com.cyberpulse.app.work.AlertNotifier
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private sealed interface Row {
    val key: String

    data class Header(val briefing: Briefing) : Row {
        override val key = "header"
    }

    data class SectionHeader(val section: BriefingSection, val position: Int) : Row {
        override val key = "section:$position"
    }

    data class Item(val item: BriefingItem, val section: BriefingSection) : Row {
        override val key = "item:${item.key}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BriefingScreen(
    viewModel: MainViewModel,
    refreshing: Boolean,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val briefing by viewModel.briefing.collectAsStateWithLifecycle()
    val speaker by viewModel.speakerState.collectAsStateWithLifecycle()
    val speakingKey by viewModel.speakingItemKey.collectAsStateWithLifecycle()
    val speakingText by viewModel.speakingText.collectAsStateWithLifecycle()
    val rate by viewModel.speechRate.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val rows = remember(briefing) {
        val b = briefing ?: return@remember emptyList()
        buildList<Row> {
            add(Row.Header(b))
            b.sections.forEachIndexed { i, section ->
                add(Row.SectionHeader(section, i))
                section.items.forEach { add(Row.Item(it, section)) }
            }
        }
    }

    // Keep the card being read on screen.
    val listState = rememberLazyListState()
    LaunchedEffect(speakingKey) {
        val key = speakingKey ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it is Row.Item && it.item.key == key }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Column(modifier.fillMaxSize()) {
        PlayerPanel(
            state = speaker,
            speakingText = speakingText,
            rate = rate,
            hasContent = briefing?.isEmpty == false,
            onToggle = viewModel::togglePlayback,
            onPrevious = viewModel::previousSegment,
            onNext = viewModel::nextSegment,
            onStop = viewModel::stopPlayback,
            onRate = viewModel::cycleSpeechRate,
            onOpenTtsSettings = { openTtsSettings(context) },
        )

        PullToRefreshBox(isRefreshing = refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            val b = briefing
            if (b == null || b.isEmpty) {
                EmptyState(
                    when {
                        b == null -> "> compiling briefing…"
                        refreshing -> "> pulling feeds…"
                        else -> "> nothing in the last 24h. pull down to sync."
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is Row.Header -> BriefingHeader(row.briefing, viewModel)
                            is Row.SectionHeader -> SectionTitle(row.section)
                            is Row.Item -> BriefingCard(
                                item = row.item,
                                active = row.item.key == speakingKey,
                                onOpen = { onOpenLink(row.item.link) },
                                onReadFrom = { viewModel.playFrom(row.item.key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerPanel(
    state: BriefingSpeaker.State,
    speakingText: String?,
    rate: Float,
    hasContent: Boolean,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onRate: () -> Unit,
    onOpenTtsSettings: () -> Unit,
) {
    val status = state.status
    val active = status == BriefingSpeaker.Status.SPEAKING || status == BriefingSpeaker.Status.PAUSED
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = Fsociety.Panel),
        border = BorderStroke(1.dp, if (status == BriefingSpeaker.Status.SPEAKING) Fsociety.Red else Fsociety.Line),
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TerminalButton(
                    label = when (status) {
                        BriefingSpeaker.Status.SPEAKING -> "❚❚ pause"
                        BriefingSpeaker.Status.PAUSED -> "▶ resume"
                        BriefingSpeaker.Status.INITIALIZING -> "… voice"
                        else -> "▶ play"
                    },
                    enabled = hasContent,
                    color = Fsociety.RedGlow,
                    onClick = onToggle,
                )
                Spacer(Modifier.weight(1f))
                TerminalButton("«", enabled = active, onClick = onPrevious)
                TerminalButton("»", enabled = active, onClick = onNext)
                TerminalButton("■", enabled = active || status == BriefingSpeaker.Status.INITIALIZING, onClick = onStop)
                TerminalButton(formatRate(rate), color = Fsociety.Green, onClick = onRate)
            }
            if (active && state.total > 0) {
                LinearProgressIndicator(
                    progress = { (state.index + 1f) / state.total },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    color = Fsociety.Red,
                    trackColor = Fsociety.Line,
                    drawStopIndicator = {},
                )
                Text(
                    "> [%02d/%02d] %s".format(state.index + 1, state.total, speakingText.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = Fsociety.Green,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            if (status == BriefingSpeaker.Status.UNAVAILABLE || state.message != null) {
                Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "> ${state.message.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Fsociety.RedGlow,
                        modifier = Modifier.weight(1f),
                    )
                    if (status == BriefingSpeaker.Status.UNAVAILABLE) {
                        TerminalButton("tts settings", color = Fsociety.Green, onClick = onOpenTtsSettings)
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Fsociety.White,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        modifier = modifier.defaultMinSize(minWidth = 1.dp),
    ) {
        Text(
            "[$label]",
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) color else Fsociety.Grey.copy(alpha = 0.5f),
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BriefingHeader(briefing: Briefing, viewModel: MainViewModel) {
    val enabled by viewModel.briefingEnabled.collectAsStateWithLifecycle()
    val minutes by viewModel.briefingTime.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val s = briefing.stats
    val date = remember(briefing.generatedAt) {
        DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm", Locale.getDefault())
            .format(Instant.ofEpochMilli(briefing.generatedAt).atZone(ZoneId.systemDefault()))
            .lowercase()
    }

    Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("// $date · last 24h", style = MaterialTheme.typography.labelMedium, color = Fsociety.Grey)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (s.watchingAny) Pill("${s.affectingYou} TARGETS", Fsociety.RedGlow, filled = s.affectingYou > 0)
            if (s.exploited > 0) Pill("${s.exploited} EXPLOITED", ExploitedColor)
            if (s.critical > 0) Pill("${s.critical} CRITICAL", Fsociety.RedGlow)
            Pill("${s.newCves} NEW CVES", Fsociety.White)
            Pill("${s.newsStories} STORIES", Fsociety.Grey)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (enabled) "> daily notification at" else "> daily notification off",
                style = MaterialTheme.typography.bodySmall,
                color = Fsociety.Grey,
            )
            if (enabled) {
                TextButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            android.R.style.Theme_Material_Dialog_Alert,
                            { _, h, m -> viewModel.setBriefingTime(h * 60 + m) },
                            minutes / 60,
                            minutes % 60,
                            DateFormat.is24HourFormat(context),
                        ).show()
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) {
                    Text("[ %02d:%02d ]".format(minutes / 60, minutes % 60), color = Fsociety.Green, style = MaterialTheme.typography.labelLarge)
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        viewModel.setBriefingEnabled(it)
                        if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !AlertNotifier.canNotify(context)) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(section: BriefingSection) {
    Text(
        "// ${section.title.lowercase()} [${section.items.size}]",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = if (section.kind == SectionKind.YOUR_SYSTEMS || section.kind == SectionKind.EXPLOITED) Fsociety.RedGlow else Fsociety.White,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BriefingCard(item: BriefingItem, active: Boolean, onOpen: () -> Unit, onReadFrom: () -> Unit) {
    OutlinedCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = when {
                active -> Color(0xFF071A07)
                item.flagged -> Fsociety.RedDeep.copy(alpha = 0.55f)
                else -> Fsociety.Panel
            },
        ),
        border = when {
            active -> BorderStroke(2.dp, Fsociety.Green)
            item.flagged -> BorderStroke(1.dp, Fsociety.Red)
            else -> BorderStroke(1.dp, Fsociety.Line)
        },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val hasPills = item.cveIds.isNotEmpty() || item.severity != null || item.knownExploited || item.sources.size > 1
            if (hasPills) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item.cveIds.firstOrNull()?.let { Pill(it, Fsociety.White) }
                    if (item.cveIds.size > 1) Pill("+${item.cveIds.size - 1}", Fsociety.Grey)
                    item.severity?.let { sev ->
                        val score = item.cvssScore?.let { " %.1f".format(it) } ?: ""
                        Pill("${sev.label.uppercase()}$score", sev.color())
                    }
                    if (item.knownExploited) Pill("EXPLOITED", ExploitedColor, filled = true)
                    if (item.sources.size > 1) Pill("×${item.sources.size} SOURCES", Fsociety.Green)
                }
            }
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.summary.isNotBlank()) {
                Text(
                    item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = Fsociety.Grey,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.systemTypes.isNotEmpty()) {
                Text(
                    item.systemTypes.joinToString(" ") { "#" + it.label.lowercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.flagged) Fsociety.RedGlow else Fsociety.Grey,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "// " + item.sources.joinToString(", ") { it.lowercase().replace(' ', '_') },
                    style = MaterialTheme.typography.labelSmall,
                    color = Fsociety.Grey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TerminalButton(if (active) "reading" else "▶ from here", color = Fsociety.Green, onClick = onReadFrom)
            }
        }
    }
}

/** 0.8 -> "0.8x", 1.0 -> "1.0x", 1.25 -> "1.25x". */
private fun formatRate(rate: Float): String {
    val s = "%.2f".format(Locale.US, rate).trimEnd('0')
    return (if (s.endsWith('.')) "${s}0" else s) + "x"
}

private fun openTtsSettings(context: Context) {
    val intent = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
