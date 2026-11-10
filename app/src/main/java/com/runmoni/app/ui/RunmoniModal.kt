@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.runmoni.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Every modal has an always-reachable close action outside its scrolling body. */
@Composable
internal fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: (@Composable () -> Unit)? = null,
    dismissLabel: String? = "취소",
    closeTag: String = "form_dialog_close",
    scrollState: ScrollState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val actions: (@Composable () -> Unit)? = if (confirm == null) null else ({
        if (dismissLabel != null) TextButton(shape = RunmoniShapes.Action, onClick = onDismiss,
            modifier = Modifier.heightIn(min = 48.dp)) { Text(dismissLabel) }
        confirm()
    })
    ModalFrame(onDismiss, closeTag, scrollState, longTitle = title.length > 32,
        title = { Text(title) }, actions = actions, content = content)
}

/** Confirmation semantics stay at the call site; closing always means dismiss. */
@Composable
internal fun DismissibleAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    shape: Shape = RunmoniShapes.Dialog,
) {
    ModalFrame(onDismissRequest, "confirmation_close", shape = shape, title = title,
        actions = { dismissButton?.invoke(); confirmButton() }) { text() }
}

@Composable
private fun ModalFrame(
    onDismiss: () -> Unit,
    closeTag: String,
    scrollState: ScrollState? = null,
    longTitle: Boolean = false,
    shape: Shape = RunmoniShapes.Dialog,
    title: @Composable () -> Unit,
    actions: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bodyScroll = scrollState ?: rememberScrollState()
    val latestDismiss by rememberUpdatedState(onDismiss)
    Dialog(onDismissRequest = { latestDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val dismiss: () -> Unit = { keyboard?.hide(); focus.clearFocus(force = true); latestDismiss() }
        val currentDismiss by rememberUpdatedState(dismiss)
        Box(Modifier.fillMaxSize().testTag("modal_window")) {
        // This full-window sibling also covers the safe-area margins around
        // the card. A padding-only strip must not become an inert dead zone.
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { currentDismiss() } })
        BoxWithConstraints(Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
            .padding(horizontal = 16.dp, vertical = 8.dp).testTag("modal_safe_content"),
            contentAlignment = Alignment.Center) {
            // The backdrop is a sibling, so it never competes with a child
            // button's pointer handler. Platform Back remains a dismiss action.
            val compact = maxHeight < 440.dp || LocalDensity.current.fontScale >= 1.3f || longTitle
            // Keep confirmation/cancel actions fixed while they can fit. In a
            // very short IME window only the close bar is fixed; the remaining
            // actions can be reached through the same body scroll.
            val scrollingActions = maxHeight < 220.dp
            Surface(Modifier.widthIn(max = RunmoniLayout.DialogWidth).fillMaxWidth()
                .heightIn(max = maxHeight).testTag("form_dialog")
                .pointerInput(Unit) {
                    // Participate in hit testing to block the backdrop, without
                    // consuming button, selection, scroll, or text-field events.
                    awaitPointerEventScope { while (true) awaitPointerEvent() }
                }, shape = shape, color = RunmoniColors.Paper) {
                Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                        .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (!compact) Box(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                            ProvideTextStyle(MaterialTheme.typography.headlineSmall, title)
                        } else Spacer(Modifier.weight(1f))
                        IconButton(onClick = dismiss, modifier = Modifier.size(48.dp).testTag(closeTag)) {
                            Icon(Icons.Outlined.Close, "닫기")
                        }
                    }
                    HorizontalDivider(color = RunmoniColors.Line)
                    Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(bodyScroll)
                        .testTag("modal_scroll_body")) {
                        if (compact) Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp)
                            .semantics(mergeDescendants = true) { heading() }) {
                            ProvideTextStyle(MaterialTheme.typography.headlineSmall, title)
                        }
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
                        if (scrollingActions && actions != null) ModalActions(actions)
                    }
                    if (!scrollingActions && actions != null) ModalActions(actions)
                }
            }
        }
        }
    }
}

@Composable
private fun ModalActions(actions: @Composable () -> Unit) {
    HorizontalDivider(color = RunmoniColors.Line)
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
}
