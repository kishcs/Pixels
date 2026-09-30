package com.erohsik.pixels.ui.trackers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import com.erohsik.pixels.R
import com.erohsik.pixels.data.model.Tracker
import com.erohsik.pixels.ui.common.PaletteStrip
import com.erohsik.pixels.ui.palette.Palettes
import kotlin.math.abs
import kotlin.math.roundToInt

private val ROW_HEIGHT = 64.dp

/**
 * Active trackers: drag the handle to reorder, swipe the row sideways to archive, tap to edit.
 * Archived trackers can be restored or deleted (with typed confirmation). All gestures are
 * hand-rolled on pointerInput and mirrored as accessibility actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerListScreen(
    vm: TrackersViewModel,
    onEdit: (Long) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
                title = { Text(stringResource(R.string.trackers_title)) },
                actions = { TextButton(onClick = onAdd) { Text(stringResource(R.string.trackers_add)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            ReorderableTrackerList(
                trackers = state.active,
                onReorder = vm::reorder,
                onArchive = vm::archive,
                onEdit = onEdit,
            )
            if (state.active.isNotEmpty()) {
                Text(
                    stringResource(R.string.trackers_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (state.archived.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.trackers_archived),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() },
                )
                state.archived.forEach { t ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    ) {
                        PaletteStrip(Palettes.byId(t.paletteId).colors)
                        Spacer(Modifier.width(16.dp))
                        Text(t.name, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.unarchive(t.id) }) { Text(stringResource(R.string.trackers_restore)) }
                        TextButton(
                            onClick = { pendingDelete = t.id },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.action_delete)) }
                    }
                }
            }
        }
    }

    val deleting = pendingDelete?.let { id -> state.archived.firstOrNull { it.id == id } }
    if (deleting != null) {
        DeleteTrackerDialog(
            tracker = deleting,
            onConfirm = { vm.delete(deleting.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun ReorderableTrackerList(
    trackers: List<Tracker>,
    onReorder: (List<Long>) -> Unit,
    onArchive: (Long) -> Unit,
    onEdit: (Long) -> Unit,
) {
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
    // Local copy so rows move under the finger; written back on drop.
    var items by remember(trackers) { mutableStateOf(trackers) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val moveUp = stringResource(R.string.a11y_move_up)
    val moveDown = stringResource(R.string.a11y_move_down)
    val archiveLabel = stringResource(R.string.trackers_archive)
    val handleLabel = stringResource(R.string.a11y_drag_handle)

    fun move(from: Int, to: Int) {
        if (to !in items.indices) return
        items = items.toMutableList().apply { add(to, removeAt(from)) }
    }

    Column {
        items.forEachIndexed { index, t ->
            key(t.id) {
                val dragging = draggingId == t.id
                SwipeToArchiveRow(
                    onArchive = { onArchive(t.id) },
                    archiveLabel = archiveLabel,
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(moveUp) {
                                    move(index, index - 1); onReorder(items.map { it.id }); true
                                },
                                CustomAccessibilityAction(moveDown) {
                                    move(index, index + 1); onReorder(items.map { it.id }); true
                                },
                                CustomAccessibilityAction(archiveLabel) { onArchive(t.id); true },
                            )
                        },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onEdit(t.id) },
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(48.dp)
                                .fillMaxHeight()
                                .clearAndSetSemantics { contentDescription = handleLabel }
                                .pointerInput(t.id) {
                                    detectDragGestures(
                                        onDragStart = { draggingId = t.id; dragOffset = 0f },
                                        onDragEnd = {
                                            draggingId = null; dragOffset = 0f
                                            onReorder(items.map { it.id })
                                        },
                                        onDragCancel = { draggingId = null; dragOffset = 0f },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            val current = items.indexOfFirst { it.id == t.id }
                                            if (dragOffset > rowPx / 2 && current < items.lastIndex) {
                                                move(current, current + 1); dragOffset -= rowPx
                                            } else if (dragOffset < -rowPx / 2 && current > 0) {
                                                move(current, current - 1); dragOffset += rowPx
                                            }
                                        },
                                    )
                                },
                        ) {
                            Text(stringResource(R.string.symbol_drag_handle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PaletteStrip(Palettes.byId(t.paletteId).colors)
                        Spacer(Modifier.width(16.dp))
                        Text(t.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

/** A row that archives itself when flung more than 40% of its width either way. */
@Composable
private fun SwipeToArchiveRow(
    onArchive: () -> Unit,
    archiveLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    val shown by animateFloatAsState(if (dragging) offset else 0f, label = "swipe")
    val x = if (dragging) offset else shown

    Box(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                width = size.width.toFloat()
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; offset = 0f },
                    onDragEnd = {
                        val archive = abs(offset) > width * 0.4f
                        dragging = false
                        if (archive) onArchive()
                        offset = 0f
                    },
                    onDragCancel = { dragging = false; offset = 0f },
                    onHorizontalDrag = { change, amount -> change.consume(); offset += amount },
                )
            },
    ) {
        Text(
            archiveLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(if (x >= 0) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 24.dp),
        )
        Box(Modifier.offset { IntOffset(x.roundToInt(), 0) }) { content() }
    }
}

/** Deleting cascades every entry, so the user must type the tracker's name to confirm. */
@Composable
fun DeleteTrackerDialog(tracker: Tracker, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable(tracker.id) { mutableStateOf("") }
    val matches = typed.trim() == tracker.name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title, tracker.name)) },
        text = {
            Column {
                Text(stringResource(R.string.delete_body, tracker.name))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.delete_field_label)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = matches,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
