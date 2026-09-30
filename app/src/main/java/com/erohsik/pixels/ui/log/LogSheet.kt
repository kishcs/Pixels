package com.erohsik.pixels.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.erohsik.pixels.R
import com.erohsik.pixels.data.model.Entry
import com.erohsik.pixels.data.model.Level
import com.erohsik.pixels.data.model.Tracker
import com.erohsik.pixels.domain.DateUtils
import com.erohsik.pixels.ui.common.LevelSwatch
import com.erohsik.pixels.ui.palette.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CONFIRM_FLASH_MS = 150L

/**
 * Two-tap logging: open, tap a colour. The tap writes immediately and the sheet closes after a
 * short confirmation flash; there is no save button. A note typed here is saved on dismiss.
 * Selected level and note draft survive process death via rememberSaveable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheet(
    dayIndex: Long,
    tracker: Tracker,
    palette: Palette,
    entry: Entry?,
    onPick: (level: Int, note: String?) -> Unit,
    onSaveNote: (note: String?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable(dayIndex) { mutableIntStateOf(entry?.level ?: Level.EMPTY) }
    var note by rememberSaveable(dayIndex) { mutableStateOf(entry?.note.orEmpty()) }
    var noteOpen by rememberSaveable(dayIndex) { mutableStateOf(!entry?.note.isNullOrEmpty()) }
    var closing by remember { mutableStateOf(false) }

    fun animateClosed() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = {
            // Swipe down, scrim tap, or back: keep whatever note was typed.
            if (!closing) {
                closing = true
                onSaveNote(note)
                onDismiss()
            }
        },
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(
                text = DateUtils.formatLong(dayIndex),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = tracker.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (level in Level.all) {
                    LevelSwatch(
                        level = level,
                        label = tracker.label(level),
                        color = palette.color(level),
                        selected = selected == level,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (closing) return@LevelSwatch
                            closing = true
                            selected = level
                            onPick(level, note)
                            scope.launch {
                                delay(CONFIRM_FLASH_MS)
                                animateClosed()
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (noteOpen) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(Entry.MAX_NOTE_LENGTH) },
                    label = { Text(stringResource(R.string.log_note_label)) },
                    supportingText = {
                        Text(stringResource(R.string.log_note_counter, note.length, Entry.MAX_NOTE_LENGTH))
                    },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TextButton(onClick = { noteOpen = true }) {
                    Text(stringResource(if (entry?.note.isNullOrEmpty()) R.string.log_add_note else R.string.log_edit_note))
                }
            }
            if (entry != null) {
                TextButton(
                    onClick = {
                        if (closing) return@TextButton
                        closing = true
                        onClear()
                        animateClosed()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.log_clear))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
