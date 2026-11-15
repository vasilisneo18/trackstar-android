package com.vasilisneo.trackstar.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/** Scroll left over once a list inside the sheet hits its top is swallowed here instead of reaching
 *  the sheet, so scrolling back down through the content never drags the sheet closed. Upward
 *  scrolls still reach the sheet first (a half-open sheet expands), and the grabber / header and a
 *  tap outside still dismiss it — like iOS sheets. */
private val KeepSheetOnScroll = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        Offset(0f, available.y)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(0f, available.y)
}

/** The app's ModalBottomSheet: the same parameters, but scrolling its content can't dismiss it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackstarBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
        dragHandle = dragHandle,
        contentWindowInsets = contentWindowInsets,
    ) {
        Column(Modifier.nestedScroll(KeepSheetOnScroll)) { content() }
    }
}
