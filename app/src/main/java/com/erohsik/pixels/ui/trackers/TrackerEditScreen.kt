package com.erohsik.pixels.ui.trackers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.erohsik.pixels.R
import com.erohsik.pixels.data.model.Level
import com.erohsik.pixels.ui.common.PaletteStrip
import com.erohsik.pixels.ui.palette.Palette
import com.erohsik.pixels.ui.palette.Palettes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerEditScreen(
    vm: TrackerEditViewModel,
    trackerId: Long,
    onDone: () -> Unit,
) {
    val defaultLabels = stringArrayResource(R.array.default_generic_labels).toList()
    LaunchedEffect(trackerId) { vm.load(trackerId, defaultLabels) }
    val state by vm.state.collectAsState()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val leave = { vm.reset(); onDone() }
    BackHandler(onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = leave) { Text(stringResource(R.string.action_cancel)) } },
                title = {
                    Text(stringResource(if (trackerId == TrackerEditViewModel.NEW) R.string.edit_title_new else R.string.edit_title))
                },
                actions = {
                    TextButton(onClick = { vm.save { onDone() } }, enabled = state.canSave) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        if (state.loadedId != trackerId || (trackerId != TrackerEditViewModel.NEW && state.existing == null)) {
            Spacer(Modifier.padding(padding))
            return@Scaffold
        }
        val palette = Palettes.byId(state.paletteId)
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::setName,
                label = { Text(stringResource(R.string.edit_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.edit_palette),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                Palettes.all.forEach { p ->
                    PaletteOption(p, selected = p.id == state.paletteId, onClick = { vm.setPalette(p.id) })
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.edit_labels),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            for (level in Level.all) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorChip(palette.color(level))
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = state.labels[level - 1],
                        onValueChange = { vm.setLabel(level, it) },
                        label = { Text(stringResource(R.string.edit_label_n, level)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (!state.isNew) {
                Spacer(Modifier.height(32.dp))
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.edit_delete)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    val existing = state.existing
    if (confirmDelete && existing != null) {
        DeleteTrackerDialog(
            tracker = existing,
            onConfirm = { confirmDelete = false; vm.delete(onDone) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun PaletteOption(palette: Palette, selected: Boolean, onClick: () -> Unit) {
    val name = stringResource(palette.nameRes)
    val shape = RoundedCornerShape(12.dp)
    val handleClick = onClick
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(shape)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, shape)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), shape),
            )
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = name
                role = Role.RadioButton
                this.selected = selected
                this.onClick { handleClick(); true }
            }
            .padding(10.dp),
    ) {
        PaletteStrip(palette.colors, cell = 18.dp)
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ColorChip(color: Color) {
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
    )
}
