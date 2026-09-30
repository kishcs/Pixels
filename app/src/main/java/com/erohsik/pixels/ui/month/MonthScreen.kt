package com.erohsik.pixels.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erohsik.pixels.R
import com.erohsik.pixels.domain.DateUtils
import com.erohsik.pixels.ui.log.LogSheet
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * One month as a 7-column calendar (Monday first), larger cells than the year grid, a dot on
 * days with a note, and that month's notes listed beneath. Swipe or use the arrows to move.
 *
 * ASSUMPTION: weeks start on Monday regardless of locale.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(
    vm: MonthViewModel,
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(month) { vm.setMonth(month) }
    val state by vm.state.collectAsState()
    var sheetDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val currentMonth = YearMonth.from(DateUtils.toDate(state.today))
    val canGoForward = month < currentMonth

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
                title = {
                    Text(
                        DateUtils.formatMonth(month),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                actions = {
                    val prevLabel = stringResource(R.string.a11y_previous_month)
                    val nextLabel = stringResource(R.string.a11y_next_month)
                    TextButton(
                        onClick = { onMonthChange(month.minusMonths(1)) },
                        modifier = Modifier.semantics { contentDescription = prevLabel },
                    ) { Text(stringResource(R.string.symbol_previous), modifier = Modifier.clearAndSetSemantics { }) }
                    TextButton(
                        onClick = { onMonthChange(month.plusMonths(1)) },
                        enabled = canGoForward,
                        modifier = Modifier.semantics { contentDescription = nextLabel },
                    ) { Text(stringResource(R.string.symbol_next), modifier = Modifier.clearAndSetSemantics { }) }
                },
            )
        },
    ) { padding ->
        val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
        val latestMonth by rememberUpdatedState(month)
        val latestCanGoForward by rememberUpdatedState(canGoForward)
        val latestOnMonthChange by rememberUpdatedState(onMonthChange)
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            when {
                                total > swipeThreshold -> latestOnMonthChange(latestMonth.minusMonths(1))
                                total < -swipeThreshold && latestCanGoForward -> latestOnMonthChange(latestMonth.plusMonths(1))
                            }
                        },
                        onHorizontalDrag = { _, amount -> total += amount },
                    )
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            val tracker = state.tracker
            if (tracker == null || state.month != month) {
                Spacer(Modifier.height(1.dp))
                return@Column
            }
            WeekdayHeader()
            Spacer(Modifier.height(4.dp))
            CalendarGrid(
                month = month,
                state = state,
                onDayTap = { sheetDay = it },
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.month_notes_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            if (state.notes.isEmpty()) {
                Text(
                    stringResource(R.string.month_notes_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.notes.forEachIndexed { i, entry ->
                    if (i > 0) HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { sheetDay = entry.dayIndex }
                            .padding(vertical = 10.dp),
                    ) {
                        Box(
                            Modifier
                                .padding(top = 3.dp)
                                .size(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(state.palette.color(entry.level)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                DateUtils.formatShort(entry.dayIndex),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(entry.note.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    val tracker = state.tracker
    val day = sheetDay
    if (tracker != null && day != null) {
        LogSheet(
            dayIndex = day,
            tracker = tracker,
            palette = state.palette,
            entry = state.entriesByDay[day],
            onPick = { level, note -> vm.log(day, level, note) },
            onSaveNote = { note -> vm.saveNote(day, note) },
            onClear = { vm.clear(day) },
            onDismiss = { sheetDay = null },
        )
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { dow ->
            Text(
                dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun CalendarGrid(month: YearMonth, state: MonthUiState, onDayTap: (Long) -> Unit) {
    val tracker = state.tracker ?: return
    val lead = month.atDay(1).dayOfWeek.value - 1 // Monday = 0
    val length = month.lengthOfMonth()
    val weeks = (lead + length + 6) / 7
    val firstDay = month.atDay(1).toEpochDay()
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val ring = MaterialTheme.colorScheme.onSurface
    val noteDescription = stringResource(R.string.a11y_has_note)
    val unlogged = stringResource(R.string.a11y_unlogged)
    val futureLabel = stringResource(R.string.a11y_future)
    val logLabel = stringResource(R.string.a11y_log_day)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (w in 0 until weeks) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (d in 0 until 7) {
                    val dom = w * 7 + d - lead + 1
                    if (dom < 1 || dom > length) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                        continue
                    }
                    val day = firstDay + dom - 1
                    val entry = state.entriesByDay[day]
                    val future = day > state.today
                    val fill = when {
                        future -> Color.Transparent
                        entry == null -> empty
                        else -> state.palette.color(entry.level)
                    }
                    val shape = RoundedCornerShape(8.dp)
                    val status = when {
                        future -> futureLabel
                        entry == null -> unlogged
                        else -> tracker.label(entry.level)
                    }
                    val description = buildString {
                        append(DateUtils.formatLong(day))
                        append(", ")
                        append(status)
                        if (!entry?.note.isNullOrBlank()) append(", ").append(noteDescription)
                    }
                    val textColor = when {
                        future -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        entry == null -> MaterialTheme.colorScheme.onSurface
                        fill.luminance() > 0.45f -> Color.Black.copy(alpha = 0.75f)
                        else -> Color.White.copy(alpha = 0.9f)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(shape)
                            .background(fill)
                            .then(if (day == state.today) Modifier.border(2.dp, ring, shape) else Modifier)
                            .then(if (future) Modifier else Modifier.clickable { onDayTap(day) })
                            .clearAndSetSemantics {
                                contentDescription = description
                                if (!future) onClick(label = logLabel) { onDayTap(day); true }
                            },
                    ) {
                        Text(
                            dom.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = textColor,
                            modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                        )
                        if (!entry?.note.isNullOrBlank()) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(textColor),
                            )
                        }
                    }
                }
            }
        }
    }
}
