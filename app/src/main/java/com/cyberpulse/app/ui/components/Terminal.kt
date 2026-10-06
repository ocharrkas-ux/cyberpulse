package com.cyberpulse.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cyberpulse.app.ui.theme.Fsociety
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Text that occasionally tears into red/cyan offsets, with a blinking block cursor.
 * Timers only run while the screen is started, and state changes a few times per second at most.
 */
@Composable
fun GlitchText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Fsociety.White,
    cursor: Boolean = true,
) {
    var glitch by remember { mutableStateOf(false) }
    var cursorOn by remember { mutableStateOf(true) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(text) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Glitch once right away when the title changes, then every few seconds.
            while (true) {
                repeat(Random.nextInt(2, 4)) {
                    glitch = true; delay(70)
                    glitch = false; delay(60)
                }
                delay(Random.nextLong(4_000, 9_000))
            }
        }
    }
    if (cursor) {
        LaunchedEffect(Unit) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(530)
                    cursorOn = !cursorOn
                }
            }
        }
    }

    val shown = if (cursor) text + if (cursorOn) "█" else " " else text
    Box(modifier) {
        if (glitch) {
            Text(shown, style = style, color = Fsociety.Red.copy(alpha = 0.85f), modifier = Modifier.offset(x = (-2).dp))
            Text(shown, style = style, color = Fsociety.Cyan.copy(alpha = 0.6f), modifier = Modifier.offset(x = 2.dp, y = 1.dp))
        }
        Text(shown, style = style, color = color)
    }
}
