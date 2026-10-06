package com.cyberpulse.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cyberpulse.app.ui.components.GlitchText
import com.cyberpulse.app.ui.theme.Fsociety
import com.cyberpulse.app.ui.theme.scanlines
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyberpulse.app.data.db.Article
import com.cyberpulse.app.domain.NewsCategory
import com.cyberpulse.app.domain.VulnPolicy
import com.cyberpulse.app.ui.screens.BriefingScreen
import com.cyberpulse.app.ui.screens.NewsScreen
import com.cyberpulse.app.ui.screens.SystemsScreen
import com.cyberpulse.app.ui.screens.VulnsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CyberPulseRoot(viewModel: MainViewModel) {
    val articles by viewModel.articles.collectAsStateWithLifecycle()
    val watch by viewModel.watchLevels.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val uriHandler = LocalUriHandler.current
    val open: (Article) -> Unit = { article ->
        viewModel.markRead(article)
        runCatching { uriHandler.openUri(article.link) }
    }

    val unreadFlagged = articles.count {
        !it.isRead && it.category == NewsCategory.VULNERABILITIES && VulnPolicy.isFlagged(it, watch)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    GlitchText(
                        when (tab) {
                            Tab.BRIEF -> "fsociety:~/brief$ "
                            Tab.NEWS -> "fsociety:~/news$ "
                            Tab.VULNS -> "fsociety:~/vulns$ "
                            Tab.SYSTEMS -> "fsociety:~/systems$ "
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Fsociety.Black,
                    scrolledContainerColor = Fsociety.Black,
                    actionIconContentColor = Fsociety.RedGlow,
                ),
                actions = {
                    if (tab != Tab.SYSTEMS) {
                        IconButton(onClick = viewModel::refresh, enabled = !refreshing) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
            )
        },
        bottomBar = {
            HorizontalDivider(color = Fsociety.Line)
            NavigationBar(containerColor = Fsociety.Black, tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = tab == Tab.BRIEF,
                    onClick = { viewModel.selectTab(Tab.BRIEF) },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    label = { Text("brief") },
                )
                NavigationBarItem(
                    selected = tab == Tab.NEWS,
                    onClick = { viewModel.selectTab(Tab.NEWS) },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("news") },
                )
                NavigationBarItem(
                    selected = tab == Tab.VULNS,
                    onClick = { viewModel.selectTab(Tab.VULNS) },
                    icon = {
                        BadgedBox(badge = { if (unreadFlagged > 0) Badge(containerColor = Fsociety.Red) { Text("$unreadFlagged") } }) {
                            Icon(Icons.Default.Warning, contentDescription = null)
                        }
                    },
                    label = { Text("vulns") },
                )
                NavigationBarItem(
                    selected = tab == Tab.SYSTEMS,
                    onClick = { viewModel.selectTab(Tab.SYSTEMS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("systems") },
                )
            }
        },
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Fsociety.Panel,
                    contentColor = Fsociety.Green,
                    shape = MaterialTheme.shapes.small,
                )
            }
        },
        containerColor = Fsociety.Black,
        modifier = Modifier.scanlines(),
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.BRIEF -> BriefingScreen(
                viewModel = viewModel,
                refreshing = refreshing,
                onOpenLink = { link -> runCatching { uriHandler.openUri(link) } },
                modifier = modifier,
            )
            Tab.NEWS -> NewsScreen(
                articles = articles,
                watch = watch,
                refreshing = refreshing,
                onRefresh = viewModel::refresh,
                onOpen = open,
                onSetWatch = viewModel::setWatchLevel,
                modifier = modifier,
            )
            Tab.VULNS -> VulnsScreen(
                articles = articles,
                watch = watch,
                refreshing = refreshing,
                onRefresh = viewModel::refresh,
                onOpen = open,
                onSetWatch = viewModel::setWatchLevel,
                onManageSystems = { viewModel.selectTab(Tab.SYSTEMS) },
                modifier = modifier,
            )
            Tab.SYSTEMS -> SystemsScreen(viewModel = viewModel, articles = articles, modifier = modifier)
        }
    }
}
