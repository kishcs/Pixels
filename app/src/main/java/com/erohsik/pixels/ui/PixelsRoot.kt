package com.erohsik.pixels.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erohsik.pixels.ui.month.MonthScreen
import com.erohsik.pixels.ui.month.MonthViewModel
import com.erohsik.pixels.ui.settings.SettingsScreen
import com.erohsik.pixels.ui.settings.SettingsViewModel
import com.erohsik.pixels.ui.trackers.TrackerEditScreen
import com.erohsik.pixels.ui.trackers.TrackerEditViewModel
import com.erohsik.pixels.ui.trackers.TrackerListScreen
import com.erohsik.pixels.ui.trackers.TrackersViewModel
import com.erohsik.pixels.ui.year.YearScreen
import com.erohsik.pixels.ui.year.YearViewModel
import java.time.YearMonth

/** Screens, encoded as plain strings so the back stack survives rotation and process death. */
sealed interface Route {
    data object Year : Route
    data class Month(val month: YearMonth) : Route
    data object Trackers : Route
    data class EditTracker(val id: Long) : Route
    data object Settings : Route

    fun encode(): String = when (this) {
        Year -> "year"
        is Month -> "month:$month"
        Trackers -> "trackers"
        is EditTracker -> "edit:$id"
        Settings -> "settings"
    }

    companion object {
        fun decode(raw: String): Route = when {
            raw.startsWith("month:") -> Month(YearMonth.parse(raw.removePrefix("month:")))
            raw.startsWith("edit:") -> EditTracker(raw.removePrefix("edit:").toLong())
            raw == "trackers" -> Trackers
            raw == "settings" -> Settings
            else -> Year
        }
    }
}

private val BackStackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { stack -> stack.map { it.encode() } },
    restore = { saved -> mutableStateListOf<Route>().apply { addAll(saved.map(Route::decode)) } },
)

/**
 * Hand-rolled navigation: a saveable stack of [Route]s. No navigation library.
 * ViewModels are activity-scoped and created by [PixelsViewModelFactory].
 */
@Composable
fun PixelsRoot() {
    val stack = rememberSaveable(saver = BackStackSaver) { mutableStateListOf<Route>(Route.Year) }
    // Keeps each screen's saveable state (scroll position, open sheets) while it is covered.
    val holder = rememberSaveableStateHolder()
    fun push(route: Route) {
        stack.add(route)
    }
    fun pop() {
        // removeAt(lastIndex), not removeLast(): the latter resolves to a Java 21 API on new JDKs.
        if (stack.size > 1) holder.removeState(stack.removeAt(stack.lastIndex).stateKey())
    }

    BackHandler(enabled = stack.size > 1 && stack.last() !is Route.EditTracker) { pop() }

    AnimatedContent(
        targetState = stack.last(),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        // Same key for every month, so swiping months recomposes in place instead of
        // cross-fading two month screens that would share one saved-state key.
        contentKey = { it.stateKey() },
        label = "screen",
    ) { route ->
        holder.SaveableStateProvider(route.stateKey()) {
            when (route) {
                Route.Year -> YearScreen(
                    vm = viewModel<YearViewModel>(factory = PixelsViewModelFactory),
                    onOpenMonth = { push(Route.Month(it)) },
                    onManageTrackers = { push(Route.Trackers) },
                    onOpenSettings = { push(Route.Settings) },
                )
                is Route.Month -> MonthScreen(
                    vm = viewModel<MonthViewModel>(factory = PixelsViewModelFactory),
                    month = route.month,
                    onMonthChange = { stack[stack.lastIndex] = Route.Month(it) },
                    onBack = ::pop,
                )
                Route.Trackers -> TrackerListScreen(
                    vm = viewModel<TrackersViewModel>(factory = PixelsViewModelFactory),
                    onEdit = { push(Route.EditTracker(it)) },
                    onAdd = { push(Route.EditTracker(TrackerEditViewModel.NEW)) },
                    onBack = ::pop,
                )
                is Route.EditTracker -> TrackerEditScreen(
                    vm = viewModel<TrackerEditViewModel>(factory = PixelsViewModelFactory),
                    trackerId = route.id,
                    onDone = ::pop,
                )
                Route.Settings -> SettingsScreen(
                    vm = viewModel<SettingsViewModel>(factory = PixelsViewModelFactory),
                    onBack = ::pop,
                )
            }
        }
    }
}

/** Month screens share one key so swiping between months doesn't pile up saved state. */
private fun Route.stateKey(): String = if (this is Route.Month) "month" else encode()
