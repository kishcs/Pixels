package com.erohsik.pixels.ui.year

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erohsik.pixels.R
import com.erohsik.pixels.domain.DateUtils
import com.erohsik.pixels.ui.palette.Palette
import kotlin.math.floor

private val GAP = 2.dp
private val RING = 2.dp
private const val CORNER_FRACTION = 0.18f

/**
 * Cell geometry shared by drawing, hit testing, and the month-initial header, so the three can
 * never disagree. cellSize = floor((width - 11 * gap) / 12); leftover width centres the grid.
 */
internal class GridMetrics(widthPx: Float, val gap: Float) {
    val cell: Float = floor((widthPx - (DateUtils.GRID_COLUMNS - 1) * gap) / DateUtils.GRID_COLUMNS).coerceAtLeast(1f)
    val stride: Float = cell + gap
    val usedWidth: Float = DateUtils.GRID_COLUMNS * cell + (DateUtils.GRID_COLUMNS - 1) * gap
    val offsetX: Float = ((widthPx - usedWidth) / 2f).coerceAtLeast(0f)
    val height: Float = DateUtils.GRID_ROWS * cell + (DateUtils.GRID_ROWS - 1) * gap
}

/**
 * The year as a 12 × 31 grid in one Canvas: one drawRoundRect per visible cell, no per-cell
 * composables. [levels] comes pre-built from the ViewModel, so drawing does no lookups or date
 * maths. Accessibility is one semantics node per month, laid over the canvas.
 */
@Composable
fun PixelGrid(
    year: Int,
    levels: IntArray,
    todayIndex: Int,
    today: Long,
    palette: Palette,
    monthDescriptions: List<String>,
    onDayTap: (dayIndex: Long) -> Unit,
    onMonthOpen: (month: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val emptyColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val ringColor = MaterialTheme.colorScheme.onSurface
    val colors: List<Color> = palette.colors
    val initials = stringArrayResource(R.array.month_initials)
    val openMonthLabel = stringResource(R.string.a11y_open_month)

    val currentYear by rememberUpdatedState(year)
    val currentToday by rememberUpdatedState(today)
    val currentOnDayTap by rememberUpdatedState(onDayTap)
    val currentOnMonthOpen by rememberUpdatedState(onMonthOpen)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gapPx = with(density) { GAP.toPx() }
        val metrics = remember(constraints.maxWidth, gapPx) { GridMetrics(constraints.maxWidth.toFloat(), gapPx) }
        val cellDp = with(density) { metrics.cell.toDp() }
        val gapDp = with(density) { metrics.gap.toDp() }
        val offsetDp = with(density) { metrics.offsetX.toDp() }
        val heightDp = with(density) { metrics.height.toDp() }

        Column {
            // Month initials: real Text, outside the canvas, aligned with the same metrics.
            Row(Modifier.padding(start = offsetDp)) {
                for (i in 0 until DateUtils.GRID_COLUMNS) {
                    Text(
                        text = initials[i],
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(cellDp)
                            .pointerInput(Unit) { detectTapGestures(onLongPress = { currentOnMonthOpen(i + 1) }) }
                            .semantics {
                                contentDescription = monthDescriptions.getOrElse(i) { "" }
                                onLongClick(label = openMonthLabel) { onMonthOpen(i + 1); true }
                            },
                    )
                    if (i < DateUtils.GRID_COLUMNS - 1) Spacer(Modifier.width(gapDp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(heightDp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            // Recomputed from this scope's own size so hit testing matches drawing.
                            val m = GridMetrics(size.width.toFloat(), GAP.toPx())
                            val x = offset.x - m.offsetX
                            if (x < 0f || x > m.usedWidth) return@detectTapGestures
                            val col = (x / m.stride).toInt().coerceIn(0, DateUtils.GRID_COLUMNS - 1)
                            val row = (offset.y / m.stride).toInt().coerceIn(0, DateUtils.GRID_ROWS - 1)
                            val month = col + 1
                            val dom = row + 1
                            if (!DateUtils.isValidDay(currentYear, month, dom)) return@detectTapGestures
                            val day = DateUtils.dayIndex(currentYear, month, dom)
                            if (day > currentToday) return@detectTapGestures // future days: no-op
                            currentOnDayTap(day)
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val m = GridMetrics(size.width, GAP.toPx())
                    val cellSize = Size(m.cell, m.cell)
                    val corner = CornerRadius(m.cell * CORNER_FRACTION)
                    for (col in 0 until DateUtils.GRID_COLUMNS) {
                        val x = m.offsetX + col * m.stride
                        val base = col * DateUtils.GRID_ROWS
                        for (row in 0 until DateUtils.GRID_ROWS) {
                            val v = levels[base + row]
                            if (v < 0) continue // non-existent or future date: leave blank
                            drawRoundRect(
                                color = if (v == 0) emptyColor else colors[v - 1],
                                topLeft = Offset(x, row * m.stride),
                                size = cellSize,
                                cornerRadius = corner,
                            )
                        }
                    }
                    if (todayIndex >= 0) {
                        val ring = RING.toPx()
                        val col = todayIndex / DateUtils.GRID_ROWS
                        val row = todayIndex % DateUtils.GRID_ROWS
                        drawRoundRect(
                            color = ringColor,
                            topLeft = Offset(m.offsetX + col * m.stride + ring / 2, row * m.stride + ring / 2),
                            size = Size(m.cell - ring, m.cell - ring),
                            cornerRadius = corner,
                            style = Stroke(width = ring),
                        )
                    }
                }
                // One semantics node per month column, carrying that month's summary.
                Row(Modifier.fillMaxSize().padding(start = offsetDp)) {
                    for (i in 0 until DateUtils.GRID_COLUMNS) {
                        Box(
                            Modifier
                                .width(cellDp)
                                .fillMaxHeight()
                                .semantics {
                                    contentDescription = monthDescriptions.getOrElse(i) { "" }
                                    onClick(label = openMonthLabel) { onMonthOpen(i + 1); true }
                                },
                        )
                        if (i < DateUtils.GRID_COLUMNS - 1) Spacer(Modifier.width(gapDp))
                    }
                }
            }
        }
    }
}
