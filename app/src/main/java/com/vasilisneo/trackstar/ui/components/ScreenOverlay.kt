package com.vasilisneo.trackstar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

// Full-screen covers (the activity recorder, the full-screen piste map) drawn in the activity's own
// window, above the whole app, instead of in a Dialog window. Compose sizes a Dialog to the display
// minus the system bars on Android 11 and older, so on 3-button phones a "full-screen" dialog stopped
// short of the nav bar and the tab bar showed through the gap. iOS: fullScreenCover.

class ScreenOverlayHost {
    internal val entries = mutableStateListOf<State<@Composable () -> Unit>>()
}

val LocalScreenOverlayHost = staticCompositionLocalOf<ScreenOverlayHost?> { null }

/** Wraps the app (in MainActivity) and draws any open covers on top of it. */
@Composable
fun ScreenOverlayHostBox(content: @Composable () -> Unit) {
    val host = remember { ScreenOverlayHost() }
    CompositionLocalProvider(LocalScreenOverlayHost provides host) {
        Box(Modifier.fillMaxSize()) {
            content()
            host.entries.forEach { entry ->
                key(entry) {
                    // Swallows touches the cover doesn't use, so nothing underneath reacts.
                    Box(Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                    }) { entry.value() }
                }
            }
        }
    }
}

/** Shows [content] above the whole app while this is in the composition. Returns false when there's
 *  no host (the caller then falls back to a Dialog). */
@Composable
fun ScreenOverlay(content: @Composable () -> Unit): Boolean {
    val host = LocalScreenOverlayHost.current ?: return false
    val latest = rememberUpdatedState(content)
    DisposableEffect(host) {
        host.entries.add(latest)
        onDispose { host.entries.remove(latest) }
    }
    return true
}
