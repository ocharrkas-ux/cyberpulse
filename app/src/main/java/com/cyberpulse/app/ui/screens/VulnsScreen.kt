package com.cyberpulse.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyberpulse.app.data.db.Article
import com.cyberpulse.app.domain.ItemKind
import com.cyberpulse.app.domain.NewsCategory
import com.cyberpulse.app.domain.Severity
import com.cyberpulse.app.domain.SystemType
import com.cyberpulse.app.domain.VulnPolicy
import com.cyberpulse.app.domain.WatchLevel
import com.cyberpulse.app.ui.components.ArticleCard

private enum class KindFilter(val label: String) {
    ALL("all sources"),
    CVES("cve records"),
    NEWS("news & advisories"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VulnsScreen(
    articles: List<Article>,
    watch: Map<SystemType, WatchLevel>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpen: (Article) -> Unit,
    onSetWatch: (SystemType, WatchLevel) -> Unit,
    onManageSystems: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mySystemsOnly by rememberSaveable { mutableStateOf(false) }
    var exploitedOnly by rememberSaveable { mutableStateOf(false) }
    var minSeverity by rememberSaveable { mutableStateOf<Severity?>(null) }
    var kind by rememberSaveable { mutableStateOf(KindFilter.ALL) }
    var showSuppressed by rememberSaveable { mutableStateOf(false) }

    val vulns = remember(articles) { articles.filter { it.category == NewsCategory.VULNERABILITIES } }
    val hasFlags = watch.values.any { it == WatchLevel.FLAGGED }

    val filtered = remember(vulns, watch, mySystemsOnly, exploitedOnly, minSeverity, kind) {
        vulns.filter { a ->
            (!mySystemsOnly || VulnPolicy.isFlagged(a, watch)) &&
                (!exploitedOnly || a.knownExploited) &&
                (minSeverity == null || (a.severity != null && a.severity >= minSeverity!!)) &&
                when (kind) {
                    KindFilter.ALL -> true
                    KindFilter.CVES -> a.kind == ItemKind.CVE
                    KindFilter.NEWS -> a.kind != ItemKind.CVE
                }
        }
    }
    val suppressedCount = filtered.count { VulnPolicy.isSuppressed(it, watch) }
    val visible = if (showSuppressed) filtered else filtered.filterNot { VulnPolicy.isSuppressed(it, watch) }

    Column(modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = mySystemsOnly,
                    onClick = { mySystemsOnly = !mySystemsOnly },
                    label = { Text("my_systems") },
                )
            }
            item {
                FilterChip(
                    selected = exploitedOnly,
                    onClick = { exploitedOnly = !exploitedOnly },
                    label = { Text("exploited") },
                )
            }
            item {
                MenuChip(
                    label = minSeverity?.let { "${it.label.lowercase()}+" } ?: "any severity",
                    selected = minSeverity != null,
                    options = listOf<Severity?>(null) + Severity.entries,
                    optionLabel = { it?.let { s -> "${s.label.lowercase()}+" } ?: "any severity" },
                    onSelect = { minSeverity = it },
                )
            }
            item {
                MenuChip(
                    label = kind.label,
                    selected = kind != KindFilter.ALL,
                    options = KindFilter.entries,
                    optionLabel = { it.label },
                    onSelect = { kind = it },
                )
            }
        }

        if (!hasFlags) {
            OutlinedCard(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "> no targets set. flag the systems you run to get alerts.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onManageSystems) { Text("[ set up ]") }
                }
            }
        }

        if (suppressedCount > 0) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showSuppressed) "// showing $suppressedCount suppressed"
                    else "// $suppressedCount suppressed, hidden",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showSuppressed = !showSuppressed }) {
                    Text(if (showSuppressed) "[ hide ]" else "[ show ]")
                }
            }
        }

        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            if (visible.isEmpty()) {
                EmptyState(
                    when {
                        refreshing -> "> enumerating new CVEs…"
                        vulns.isEmpty() -> "> no vulnerabilities yet. pull down to sync."
                        else -> "> 0 results. loosen the filters."
                    }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visible, key = { it.id }) { article ->
                        ArticleCard(article, watch, onOpen, onSetWatch, showVulnDetails = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> MenuChip(
    label: String,
    selected: Boolean,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selected, onClick = { open = true }, label = { Text("$label ▾") })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        open = false
                    },
                )
            }
        }
    }
}
