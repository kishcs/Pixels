package com.erohsik.pixels.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.erohsik.pixels.R
import com.erohsik.pixels.data.ThemeMode
import com.erohsik.pixels.data.export.ImportPlan
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val pendingImport by vm.pendingImport.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var timeDialogOpen by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.messageShown()
    }

    // POST_NOTIFICATIONS is requested here, at the moment the reminder is switched on.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setReminderEnabled(true) else permissionDenied = true
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_JSON)) { uri ->
        if (uri != null) vm.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.previewImport(uri)
    }

    fun enableReminder(enable: Boolean) {
        if (!enable) {
            vm.setReminderEnabled(false)
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.setReminderEnabled(true)
        }
    }

    val timeLabel = remember(state.reminderMinuteOfDay) {
        LocalTime.of(state.reminderMinuteOfDay / 60, state.reminderMinuteOfDay % 60)
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
                title = { Text(stringResource(R.string.settings_title)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.settings_reminder))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = state.reminderEnabled,
                        role = Role.Switch,
                        onValueChange = ::enableReminder,
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_reminder_toggle), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_reminder_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.reminderEnabled, onCheckedChange = null)
            }
            if (permissionDenied && !state.reminderEnabled) {
                Text(
                    stringResource(R.string.settings_permission_denied),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            SettingRow(
                title = stringResource(R.string.settings_reminder_time),
                value = timeLabel,
                enabled = state.reminderEnabled,
                onClick = { timeDialogOpen = true },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_theme))
            ThemeMode.entries.forEach { mode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = state.theme == mode, role = Role.RadioButton, onClick = { vm.setTheme(mode) })
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    RadioButton(selected = state.theme == mode, onClick = null)
                    Text(
                        stringResource(
                            when (mode) {
                                ThemeMode.SYSTEM -> R.string.theme_system
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.DARK -> R.string.theme_dark
                            },
                        ),
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_data))
            SettingRow(
                title = stringResource(R.string.settings_export),
                value = stringResource(R.string.settings_export_body),
                onClick = { exportLauncher.launch(context.getString(R.string.export_file_name)) },
            )
            SettingRow(
                title = stringResource(R.string.settings_import),
                value = stringResource(R.string.settings_import_body),
                onClick = { importLauncher.launch(arrayOf(MIME_JSON, "text/plain", "application/octet-stream")) },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_about))
            Text(
                stringResource(R.string.settings_about_body, versionName(context)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (timeDialogOpen) {
        val pickerState = rememberTimePickerState(
            initialHour = state.reminderMinuteOfDay / 60,
            initialMinute = state.reminderMinuteOfDay % 60,
        )
        AlertDialog(
            onDismissRequest = { timeDialogOpen = false },
            title = { Text(stringResource(R.string.settings_reminder_time)) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    vm.setReminderTime(pickerState.hour * 60 + pickerState.minute)
                    timeDialogOpen = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { timeDialogOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    pendingImport?.let { plan -> ImportSummaryDialog(plan, onConfirm = vm::confirmImport, onDismiss = vm::cancelImport) }
}

@Composable
private fun ImportSummaryDialog(plan: ImportPlan, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val newTrackers = plan.trackers.count { it.existing == null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Column {
                Text(stringResource(R.string.import_summary, plan.added, plan.updated, plan.skipped))
                if (newTrackers > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.import_new_trackers, newTrackers))
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.import_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit, enabled: Boolean = true) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        val alpha = if (enabled) 1f else 0.4f
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
    }
}

private fun versionName(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

private const val MIME_JSON = "application/json"
