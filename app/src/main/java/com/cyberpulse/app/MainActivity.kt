package com.cyberpulse.app

import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.cyberpulse.app.ui.CyberPulseRoot
import com.cyberpulse.app.ui.MainViewModel
import com.cyberpulse.app.ui.Tab
import com.cyberpulse.app.ui.theme.CyberPulseTheme
import com.cyberpulse.app.work.AlertNotifier

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            CyberPulseTheme {
                CyberPulseRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when {
            intent == null -> Unit
            intent.getBooleanExtra(AlertNotifier.EXTRA_PLAY_BRIEFING, false) -> viewModel.playBriefingWhenReady()
            intent.getBooleanExtra(AlertNotifier.EXTRA_OPEN_BRIEFING, false) -> viewModel.selectTab(Tab.BRIEF)
            intent.getBooleanExtra(AlertNotifier.EXTRA_OPEN_VULNS, false) -> viewModel.selectTab(Tab.VULNS)
        }
    }
}
