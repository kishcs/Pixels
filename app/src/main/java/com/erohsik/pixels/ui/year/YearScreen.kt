package com.erohsik.pixels.ui.year

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erohsik.pixels.R
import com.erohsik.pixels.data.model.Level
import com.erohsik.pixels.data.model.Tracker
import com.erohsik.pixels.ui.common.LevelSwatch
import com.erohsik.pixels.ui.common.PaletteStrip
import com.erohsik.pixels.ui.log.LogSheet
import com.erohsik.pixels.ui.palette.Palette
import com.erohsik.pixels.ui.palette.Palettes
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearScreen(
    vm: YearViewModel,
    onOpenMonth: (YearMonth) -> Unit,
    onManageTrackers: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsState()
    var sheetDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var switcherOpen by rememberSaveable { mutableStateOf(false) }
    var yearPickerOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var backfill by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val switchLabel = stringResource(R.string.a11y_switch_tracker, state.tracker?.name.orEmpty())
                        // The switcher drops down from the tracker name, so changing tracker stays
                        // within thumb reach of where the user tapped.
                        Box {
                            TextButton(
                                onClick = { switcherOpen = true },
                                enabled = state.tracker != null,
                                modifier = Modifier.semantics { contentDescription = switchLabel },
                            ) {
                                Text(
                                    text = stringResource(R.string.year_tracker_button, state.tracker?.name.orEmpty()),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            TrackerSwitcherMenu(
                                expanded = switcherOpen,
                                trackers = state.trackers,
                                selectedId = state.tracker?.id,
                                onSelect = { vm.selectTracker(it); switcherOpen = false },
                                onManage = { switcherOpen = false; onManageTrackers() },
                                onDismiss = { switcherOpen = false },
                            )
                        }
                        val yearLabel = stringResource(R.string.a11y_change_year, state.year)
                        TextButton(
                            onClick = { yearPickerOpen = true },
                            modifier = Modifier.semantics { contentDescription = yearLabel },
                        ) {
                            Text(
                                text = stringResource(R.string.year_year_button, state.year),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        val moreLabel = stringResource(R.string.a11y_more_options)
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.semantics { contentDescription = moreLabel },
                        ) {
                            Text(
                                stringResource(R.string.symbol_overflow),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.clearAndSetSemantics { },
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_month_view)) },
                                enabled = state.tracker != null,
                                onClick = { menuOpen = false; onOpenMonth(vm.defaultMonth()) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_trackers)) },
                                onClick = { menuOpen = false; onManageTrackers() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_settings)) },
                                onClick = { menuOpen = false; onOpenSettings() },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val tracker = state.tracker
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding))
            tracker == null -> NoTrackers(onManageTrackers, Modifier.padding(padding))
            else -> YearBody(
                state = state,
                tracker = tracker,
                showGrid = state.totalEntries > 0 || backfill,
                onShowGrid = { backfill = true },
                onLogToday = { level -> vm.log(state.today, level, null) },
                onOpenDay = { sheetDay = it },
                onOpenMonth = onOpenMonth,
                onSwipeToMonth = { onOpenMonth(vm.defaultMonth()) },
                modifier = Modifier.padding(padding),
            )
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

    if (yearPickerOpen) {
        YearPickerDialog(
            years = state.years,
            selected = state.year,
            onSelect = { vm.setYear(it); yearPickerOpen = false },
            onDismiss = { yearPickerOpen = false },
        )
    }
}

@Composable
private fun YearBody(
    state: YearUiState,
    tracker: Tracker,
    showGrid: Boolean,
    onShowGrid: () -> Unit,
    onLogToday: (Int) -> Unit,
    onOpenDay: (Long) -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
    onSwipeToMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipeThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    val currentOnSwipe by rememberUpdatedState(onSwipeToMonth)

    Column(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Swipe left from the year grid into the month view.
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -swipeThreshold) currentOnSwipe() },
                    onHorizontalDrag = { _, amount -> total += amount },
                )
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        TodayCard(
            tracker = tracker,
            palette = state.palette,
            todayLevel = state.todayEntry?.level,
            onLog = onLogToday,
            onChange = { onOpenDay(state.today) },
        )
        Spacer(Modifier.height(16.dp))
        if (showGrid) {
            val monthNames = androidx.compose.ui.res.stringArrayResource(R.array.month_names)
            val descriptions = state.months.mapIndexed { i, m ->
                val common = if (m.commonLevel > 0) {
                    stringResource(R.string.a11y_month_common, tracker.label(m.commonLevel))
                } else {
                    ""
                }
                pluralStringResource(R.plurals.a11y_month_summary, m.possible, monthNames[i], m.logged, m.possible) + common
            }
            PixelGrid(
                year = state.year,
                levels = state.levels,
                todayIndex = state.todayIndex,
                today = state.today,
                palette = state.palette,
                monthDescriptions = descriptions,
                onDayTap = onOpenDay,
                onMonthOpen = { month -> onOpenMonth(YearMonth.of(state.year, month)) },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = pluralStringResource(R.plurals.year_completion, state.daysElapsed, state.loggedInYear, state.daysElapsed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.insights.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                InsightsCard(state.insights)
            }
        } else {
            EmptyState(onShowGrid)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Unlogged: "How was today?" with the five swatches inline, so logging today is one tap.
 * Logged: a thin row with today's colour and label; tapping it opens the sheet to change it.
 */
@Composable
private fun TodayCard(
    tracker: Tracker,
    palette: Palette,
    todayLevel: Int?,
    onLog: (Int) -> Unit,
    onChange: () -> Unit,
) {
    AnimatedContent(
        targetState = todayLevel,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "today",
    ) { level ->
        if (level == null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.today_prompt),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (l in Level.all) {
                            LevelSwatch(
                                level = l,
                                label = tracker.label(l),
                                color = palette.color(l),
                                selected = false,
                                onClick = { onLog(l) },
                                modifier = Modifier.weight(1f),
                                size = 48.dp,
                            )
                        }
                    }
                }
            }
        } else {
            val description = stringResource(R.string.a11y_today_logged, tracker.label(level))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onChange)
                    .semantics { contentDescription = description }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(palette.color(level)),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.today_logged, tracker.label(level)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.today_change),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun InsightsCard(insights: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.insights_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            insights.forEachIndexed { i, text ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                else Spacer(Modifier.height(8.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun EmptyState(onShowGrid: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onShowGrid) { Text(stringResource(R.string.empty_backfill)) }
    }
}

@Composable
private fun NoTrackers(onManage: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.no_trackers_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onManage) { Text(stringResource(R.string.no_trackers_action)) }
    }
}

@Composable
private fun TrackerSwitcherMenu(
    expanded: Boolean,
    trackers: List<Tracker>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        trackers.forEach { t ->
            val selected = t.id == selectedId
            DropdownMenuItem(
                text = {
                    Text(
                        t.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                leadingIcon = { PaletteStrip(Palettes.byId(t.paletteId).colors) },
                trailingIcon = if (selected) {
                    { Text(stringResource(R.string.switcher_current), color = MaterialTheme.colorScheme.primary) }
                } else {
                    null
                },
                onClick = { onSelect(t.id) },
                modifier = Modifier.semantics { this.selected = selected },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.switcher_manage), color = MaterialTheme.colorScheme.primary) },
            onClick = onManage,
        )
    }
}

@Composable
private fun YearPickerDialog(years: List<Int>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.year_picker_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                years.forEach { y ->
                    TextButton(onClick = { onSelect(y) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            y.toString(),
                            fontWeight = if (y == selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
