package com.cyberpulse.app.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cyberpulse.app.data.db.Article
import com.cyberpulse.app.domain.ItemKind
import com.cyberpulse.app.domain.SystemType
import com.cyberpulse.app.domain.VulnPolicy
import com.cyberpulse.app.domain.WatchLevel
import com.cyberpulse.app.ui.theme.ExploitedColor
import com.cyberpulse.app.ui.theme.Fsociety
import com.cyberpulse.app.ui.theme.color
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArticleCard(
    article: Article,
    watch: Map<SystemType, WatchLevel>,
    onOpen: (Article) -> Unit,
    onSetWatch: (SystemType, WatchLevel) -> Unit,
    modifier: Modifier = Modifier,
    showVulnDetails: Boolean = article.kind != ItemKind.NEWS || article.cveIds.isNotEmpty(),
) {
    val flagged = VulnPolicy.isFlagged(article, watch)
    OutlinedCard(
        onClick = { onOpen(article) },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (flagged) Fsociety.RedDeep.copy(alpha = 0.55f) else Fsociety.Panel,
        ),
        border = BorderStroke(1.dp, if (flagged) Fsociety.Red else Fsociety.Line),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (article.isRead) "  " else "> ",
                    style = MaterialTheme.typography.labelMedium,
                    color = Fsociety.Green,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${article.source.lowercase().replace(' ', '_')} // ${relativeTime(article.publishedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (flagged) {
                    Text(
                        "[TARGET]",
                        style = MaterialTheme.typography.labelSmall,
                        color = Fsociety.RedGlow,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (showVulnDetails) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (System.currentTimeMillis() - article.publishedAt < TimeUnit.DAYS.toMillis(1)) {
                        Pill("NEW", Fsociety.Green)
                    }
                    article.cveIds.take(3).forEach { cve ->
                        Pill(cve, Fsociety.White)
                    }
                    if (article.cveIds.size > 3) Pill("+${article.cveIds.size - 3}", Fsociety.Grey)
                    article.severity?.let { sev ->
                        val score = article.cvssScore?.let { " %.1f".format(it) } ?: ""
                        Pill("${sev.label.uppercase()}$score", sev.color())
                    }
                    if (article.knownExploited) Pill("EXPLOITED", ExploitedColor, filled = true)
                }
            }

            Text(
                article.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (article.isRead) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val body = summaryWithoutHeadline(article)
            if (body.isNotBlank()) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (article.systemTypes.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    article.systemTypes.forEach { type ->
                        SystemChip(type, watch[type] ?: WatchLevel.DEFAULT, onSetWatch)
                    }
                }
            }
        }
    }
}

/** Tap a system tag to flag or suppress that system type right from the feed. */
@Composable
private fun SystemChip(type: SystemType, level: WatchLevel, onSetWatch: (SystemType, WatchLevel) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = level == WatchLevel.FLAGGED,
            onClick = { menu = true },
            label = {
                Text(
                    "#" + type.label.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    textDecoration = if (level == WatchLevel.SUPPRESSED) TextDecoration.LineThrough else null,
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                labelColor = if (level == WatchLevel.SUPPRESSED) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            WatchLevel.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (option) {
                                WatchLevel.FLAGGED -> "flag ${type.label} // notify me"
                                WatchLevel.DEFAULT -> "default"
                                WatchLevel.SUPPRESSED -> "suppress ${type.label}"
                            },
                            fontWeight = if (option == level) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSetWatch(type, option)
                        menu = false
                    },
                )
            }
        }
    }
}

/** Terminal-style tag: outlined by default, solid for the loudest signals. */
@Composable
fun Pill(text: String, color: Color, filled: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (filled) Fsociety.White else color,
        modifier = Modifier
            .background(if (filled) color else color.copy(alpha = 0.08f))
            .border(1.dp, color)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

private val SENTENCE_BREAK = Regex("(?<=[.!?])\\s+")

/** CVE titles are the description's first sentence; don't repeat it in the body. */
private fun summaryWithoutHeadline(article: Article): String {
    if (article.summary == article.title) return ""
    val headline = article.title.removeSuffix("…")
    return if (article.kind == ItemKind.CVE && headline.length >= 20 && article.summary.startsWith(headline)) {
        article.summary.split(SENTENCE_BREAK, limit = 2).getOrNull(1).orEmpty()
    } else {
        article.summary
    }
}

private fun relativeTime(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
