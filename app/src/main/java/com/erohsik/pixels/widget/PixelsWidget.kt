package com.erohsik.pixels.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.erohsik.pixels.R
import com.erohsik.pixels.data.homeTrackerBlocking
import com.erohsik.pixels.data.model.Entry
import com.erohsik.pixels.data.model.Level
import com.erohsik.pixels.di.ServiceLocator
import com.erohsik.pixels.domain.DateUtils
import com.erohsik.pixels.ui.palette.Palettes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth

/** Everything the widget draws, loaded once per update. */
private class WidgetSnapshot(
    val trackerName: String,
    val colors: List<Color>,
    val labels: List<String>,
    val todayLevel: Int,
    /** One value per day of the current month: level, 0 = unlogged, -1 = future. */
    val month: IntArray,
    val todayDom: Int,
)

/**
 * Home-screen widget. Small (2×2): today's cell plus five log targets. Wide (4×2): the current
 * month as a strip plus the same targets. Tapping a target logs today without opening the app.
 *
 * ASSUMPTION: one responsive widget covers both sizes, and it shows the app's home tracker
 * (the one selected on the year screen).
 */
class PixelsWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = withContext(Dispatchers.IO) { loadSnapshot() }
        provideContent { WidgetContent(snapshot) }
    }

    private fun loadSnapshot(): WidgetSnapshot? {
        val tracker = ServiceLocator.trackerDao.homeTrackerBlocking(ServiceLocator.settings) ?: return null
        val today = DateUtils.today()
        val date = DateUtils.toDate(today)
        val ym = YearMonth.from(date)
        val range = DateUtils.monthRange(ym)
        val entries = ServiceLocator.entryDao.queryRange(tracker.id, range.first, range.last)
        val month = IntArray(ym.lengthOfMonth()) { i -> if (range.first + i > today) -1 else Level.EMPTY }
        for (e in entries) {
            val i = (e.dayIndex - range.first).toInt()
            if (i in month.indices && e.dayIndex <= today) month[i] = e.level
        }
        return WidgetSnapshot(
            trackerName = tracker.name,
            colors = Palettes.byId(tracker.paletteId).colors,
            labels = tracker.labels,
            todayLevel = month[date.dayOfMonth - 1],
            month = month,
            todayDom = date.dayOfMonth,
        )
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        val LevelKey = ActionParameters.Key<Int>("level")
    }
}

private val Background = ColorProvider(day = Color(0xFFFBF9F6), night = Color(0xFF1E1E21))
private val Foreground = ColorProvider(day = Color(0xFF1C1B1F), night = Color(0xFFE7E4E0))
private val EmptyCell = ColorProvider(day = Color(0x1F1C1B1F), night = Color(0x1FE7E4E0))
private val Ring = Foreground

@Composable
private fun WidgetContent(s: WidgetSnapshot?) {
    val context = LocalContext.current
    val wide = LocalSize.current.width >= PixelsWidget.WIDE.width
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(Background)
            .cornerRadius(16.dp)
            .padding(10.dp),
    ) {
        if (s == null) {
            Text(
                context.getString(R.string.widget_no_tracker),
                style = TextStyle(color = Foreground, fontSize = 13.sp),
            )
            return@Column
        }
        Text(
            s.trackerName,
            maxLines = 1,
            style = TextStyle(color = Foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(GlanceModifier.height(6.dp))
        if (wide) {
            MonthStrip(s, GlanceModifier.fillMaxWidth().defaultWeight())
        } else {
            TodayCell(s, GlanceModifier.fillMaxWidth().defaultWeight())
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth().height(36.dp)) {
            for (level in Level.all) {
                val description = context.getString(R.string.widget_log_level, s.labels[level - 1])
                Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 2.dp)) {
                    Box(
                        GlanceModifier
                            .fillMaxSize()
                            .background(s.colors[level - 1])
                            .cornerRadius(8.dp)
                            .semantics { contentDescription = description }
                            .clickable(actionRunCallback<LogLevelAction>(actionParametersOf(PixelsWidget.LevelKey to level))),
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun TodayCell(s: WidgetSnapshot, modifier: GlanceModifier) {
    val context = LocalContext.current
    val logged = s.todayLevel > 0
    val description = if (logged) {
        context.getString(R.string.a11y_today_logged, s.labels[s.todayLevel - 1])
    } else {
        context.getString(R.string.today_prompt)
    }
    Box(
        modifier = modifier
            .background(if (logged) ColorProvider(s.colors[s.todayLevel - 1], s.colors[s.todayLevel - 1]) else EmptyCell)
            .cornerRadius(12.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (!logged) {
            Text(context.getString(R.string.today_prompt), style = TextStyle(color = Foreground, fontSize = 12.sp))
        }
    }
}

/**
 * Glance Rows hold at most 10 children, so the ~31 days are laid out as four rows-within-a-row
 * of 8 slots each; unused trailing slots are blank so every cell has the same width.
 */
@Composable
private fun MonthStrip(s: WidgetSnapshot, modifier: GlanceModifier) {
    val context = LocalContext.current
    val logged = s.month.count { it > 0 }
    val past = s.month.count { it >= 0 }
    val description = context.getString(R.string.widget_month_summary, logged, past)
    Row(modifier.semantics { contentDescription = description }) {
        for (group in 0 until STRIP_GROUPS) {
            Row(GlanceModifier.defaultWeight().fillMaxHeight()) {
                for (slot in 0 until STRIP_GROUP_SIZE) {
                    val i = group * STRIP_GROUP_SIZE + slot
                    val v = s.month.getOrElse(i) { -2 }
                    Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)) {
                        if (v >= -1) DayCell(v, isToday = i + 1 == s.todayDom, colors = s.colors)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(value: Int, isToday: Boolean, colors: List<Color>) {
    val fill = when {
        value > 0 -> ColorProvider(colors[value - 1], colors[value - 1])
        value == 0 -> EmptyCell
        else -> null // future: drawn as nothing
    }
    if (fill == null) return
    if (isToday) {
        // Glance has no borders; a ring is an outer box in the foreground colour.
        Box(GlanceModifier.fillMaxSize().background(Ring).cornerRadius(3.dp).padding(1.5.dp)) {
            Box(GlanceModifier.fillMaxSize().background(fill).cornerRadius(2.dp)) {}
        }
    } else {
        Box(GlanceModifier.fillMaxSize().background(fill).cornerRadius(3.dp)) {}
    }
}

private const val STRIP_GROUPS = 4
private const val STRIP_GROUP_SIZE = 8

/** Logs today at the tapped level from the widget. Keeps an existing note. Never opens the app. */
class LogLevelAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val level = parameters[PixelsWidget.LevelKey] ?: return
        if (!Level.isValid(level)) return
        withContext(Dispatchers.IO) {
            val tracker = ServiceLocator.trackerDao.homeTrackerBlocking(ServiceLocator.settings) ?: return@withContext
            val today = DateUtils.today()
            val note = ServiceLocator.entryDao.getForDayBlocking(tracker.id, today)?.note
            ServiceLocator.entryDao.upsert(Entry(tracker.id, today, level, note, System.currentTimeMillis()))
        }
        PixelsWidget().update(context, glanceId)
    }
}
