package com.erohsik.pixels.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erohsik.pixels.R
import com.erohsik.pixels.data.model.Level

/** A single level colour, optionally with its label beneath. Selection is shown as a ring. */
@Composable
fun LevelSwatch(
    level: Int,
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    showLabel: Boolean = true,
) {
    val shape = RoundedCornerShape(size * 0.22f)
    val handleClick = onClick
    val description = stringResource(R.string.a11y_level, label, level, Level.MAX)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.RadioButton
                this.selected = selected
                this.onClick { handleClick(); true }
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            Modifier
                .size(size)
                .then(
                    if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, shape).padding(5.dp)
                    } else {
                        Modifier
                    },
                )
                .clip(shape)
                .background(color),
        )
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = size + 8.dp),
            )
        }
    }
}

/** A small, non-interactive preview of a palette's five colours. */
@Composable
fun PaletteStrip(colors: List<Color>, modifier: Modifier = Modifier, cell: Dp = 14.dp) {
    androidx.compose.foundation.layout.Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        colors.forEach { c ->
            Box(Modifier.size(cell).clip(RoundedCornerShape(cell * 0.2f)).background(c))
        }
    }
}
