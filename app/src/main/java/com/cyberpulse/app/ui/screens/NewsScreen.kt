package com.cyberpulse.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cyberpulse.app.data.db.Article
import com.cyberpulse.app.domain.ItemKind
import com.cyberpulse.app.domain.NewsCategory
import com.cyberpulse.app.domain.SystemType
import com.cyberpulse.app.domain.WatchLevel
import com.cyberpulse.app.ui.components.ArticleCard
import com.cyberpulse.app.ui.components.GlitchText
import com.cyberpulse.app.ui.theme.Fsociety

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    articles: List<Article>,
    watch: Map<SystemType, WatchLevel>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpen: (Article) -> Unit,
    onSetWatch: (SystemType, WatchLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    // null = "All"
    var selected by rememberSaveable { mutableStateOf<NewsCategory?>(null) }
    val news = remember(articles) { articles.filter { it.kind != ItemKind.CVE } }
    val counts = remember(news) { news.groupingBy { it.category }.eachCount() }
    val visible = remember(news, selected) { if (selected == null) news else news.filter { it.category == selected } }

    Column(modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(selected = selected == null, onClick = { selected = null }, label = { Text("all [${news.size}]") })
            }
            items(NewsCategory.entries.filter { (counts[it] ?: 0) > 0 }) { category ->
                FilterChip(
                    selected = selected == category,
                    onClick = { selected = if (selected == category) null else category },
                    label = { Text("${category.label.lowercase()} [${counts[category] ?: 0}]") },
                )
            }
        }

        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            if (visible.isEmpty()) {
                EmptyState(if (refreshing) "> pulling feeds…" else "> no articles yet. pull down to sync.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visible, key = { it.id }) { article ->
                        ArticleCard(article, watch, onOpen, onSetWatch)
                    }
                }
            }
        }
    }
}

@Composable
internal fun EmptyState(text: String) {
    // Scrollable so pull-to-refresh still works on an empty list.
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 96.dp, start = 32.dp, end = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlitchText(
                    "hello, friend.",
                    style = MaterialTheme.typography.headlineSmall,
                    cursor = false,
                )
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fsociety.Green,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
