package com.erohsik.pixels.di

import android.app.Application
import com.erohsik.pixels.R
import com.erohsik.pixels.data.EntryDao
import com.erohsik.pixels.data.PixelsDbHelper
import com.erohsik.pixels.data.SettingsStore
import com.erohsik.pixels.data.TrackerDao
import com.erohsik.pixels.data.export.BackupRepository
import com.erohsik.pixels.domain.DateUtils
import com.erohsik.pixels.domain.InsightTemplates
import com.erohsik.pixels.notify.ReminderScheduler
import com.erohsik.pixels.widget.PixelsWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll

/**
 * Hand-rolled dependency container. Initialised once from [com.erohsik.pixels.PixelsApp.onCreate],
 * which runs before any Activity, widget receiver, or broadcast receiver.
 */
object ServiceLocator {
    lateinit var app: Application
        private set
    lateinit var dbHelper: PixelsDbHelper
        private set
    lateinit var trackerDao: TrackerDao
        private set
    lateinit var entryDao: EntryDao
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var backup: BackupRepository
        private set
    lateinit var insightTemplates: InsightTemplates
        private set

    /** Lives as long as the process; for work that must outlive a screen (widget refresh). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _today = MutableStateFlow(DateUtils.today())

    /** Today's epoch day. Refreshed on date/time/zone change broadcasts and on resume. */
    val today: StateFlow<Long> = _today.asStateFlow()

    private var initialised = false

    @OptIn(FlowPreview::class)
    fun init(context: Application) {
        if (initialised) return
        initialised = true
        app = context
        dbHelper = PixelsDbHelper(context)
        trackerDao = TrackerDao(dbHelper)
        entryDao = EntryDao(dbHelper)
        settings = SettingsStore(dbHelper)
        backup = BackupRepository(dbHelper, trackerDao, entryDao, context.contentResolver)
        insightTemplates = InsightTemplates(
            dayOfWeek = context.getString(R.string.insight_day_of_week),
            month = context.getString(R.string.insight_month),
            trendUp = context.getString(R.string.insight_trend_up),
            trendDown = context.getString(R.string.insight_trend_down),
            longestRun = context.getString(R.string.insight_longest_run),
            completeness = context.getString(R.string.insight_completeness),
        )
        ReminderScheduler.ensureChannel(context)

        // Any write (from the app, an import, or the widget itself) or a change of home tracker
        // refreshes every widget instance. Debounced so an import redraws once, not per row.
        appScope.launch {
            merge(dbHelper.changes, settings.selectedTrackerId.drop(1).map { })
                .debounce(WIDGET_REFRESH_DEBOUNCE_MS)
                .collect {
                    // A widget refresh failing must never take the app down with it.
                    runCatching { PixelsWidget().updateAll(context) }
                }
        }
    }

    fun refreshToday() {
        _today.value = DateUtils.today()
    }

    private const val WIDGET_REFRESH_DEBOUNCE_MS = 300L
}
