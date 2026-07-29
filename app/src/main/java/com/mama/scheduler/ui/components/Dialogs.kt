package com.mama.scheduler.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mama.scheduler.core.DateUtils
import com.mama.scheduler.data.local.KidProfile
import com.mama.scheduler.data.local.PendingApprovalEvent
import com.mama.scheduler.data.local.ScheduledEvent
import com.mama.scheduler.ui.theme.KidColorPresets

private fun formatHourLabel(hour: Int): String = when {
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}

/** Manual "add event" dialog: title, kid, date, time, duration, travel buffer. */
@Composable
fun AddEventDialog(
    profiles: List<KidProfile>,
    initialDate: String,
    onConfirm: (ScheduledEvent) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedKid by remember { mutableStateOf<KidProfile?>(null) }
    var dateString by remember { mutableStateOf(initialDate) }
    var startHour by remember { mutableIntStateOf(15) }
    var durationMinutes by remember { mutableIntStateOf(60) }
    var bufferMinutes by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New activity") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Activity title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Who's it for?", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedKid == null,
                        onClick = { selectedKid = null },
                        label = { Text("Family") }
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    profiles.take(4).forEach { kid ->
                        FilterChip(
                            selected = selectedKid?.id == kid.id,
                            onClick = { selectedKid = kid },
                            label = { Text(kid.name) },
                            leadingIcon = { KidDot(kid.colorHex) }
                        )
                    }
                }

                Text("Date", style = MaterialTheme.typography.labelLarge)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { dateString = DateUtils.addDays(dateString, -1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day")
                    }
                    Text(
                        DateUtils.formatForDisplay(dateString),
                        style = MaterialTheme.typography.titleSmall
                    )
                    IconButton(onClick = { dateString = DateUtils.addDays(dateString, 1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day")
                    }
                }

                Text("Starts at ${formatHourLabel(startHour)}", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = startHour.toFloat(),
                    onValueChange = { startHour = it.toInt() },
                    valueRange = 6f..21f,
                    steps = 14
                )

                Text("Duration", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 90, 120).forEach { mins ->
                        FilterChip(
                            selected = durationMinutes == mins,
                            onClick = { durationMinutes = mins },
                            label = { Text(if (mins < 60) "${mins}m" else "${mins / 60}h${if (mins % 60 != 0) "30" else ""}") }
                        )
                    }
                }

                Text("Travel buffer", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 10, 15, 30).forEach { mins ->
                        FilterChip(
                            selected = bufferMinutes == mins,
                            onClick = { bufferMinutes = mins },
                            label = { Text(if (mins == 0) "None" else "${mins}m") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val start = DateUtils.millisFromDateString(dateString, startHour, 0)
                    onConfirm(
                        ScheduledEvent(
                            title = title.trim(),
                            kidId = selectedKid?.id,
                            kidName = selectedKid?.name,
                            startTime = start,
                            endTime = start + durationMinutes * 60_000L,
                            dateString = dateString,
                            travelBufferMinutes = bufferMinutes
                        )
                    )
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Add a kid profile: name, color preset, daily activity limit. */
@Composable
fun AddProfileDialog(
    onConfirm: (name: String, colorHex: String, dailyLimit: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(KidColorPresets.first().second) }
    var dailyLimit by remember { mutableFloatStateOf(2f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add family member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Color", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KidColorPresets.take(4).forEach { (_, hex) ->
                        ColorSwatch(hex, selectedColor == hex) { selectedColor = hex }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KidColorPresets.drop(4).forEach { (_, hex) ->
                        ColorSwatch(hex, selectedColor == hex) { selectedColor = hex }
                    }
                }

                Text(
                    "Daily activity limit: ${dailyLimit.toInt()}",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = dailyLimit,
                    onValueChange = { dailyLimit = it },
                    valueRange = 1f..5f,
                    steps = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), selectedColor, dailyLimit.toInt()) }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(safeParseColor(hex))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Edit a pending approval event's title and time before approving. */
@Composable
fun EditPendingDialog(
    pending: PendingApprovalEvent,
    onConfirm: (PendingApprovalEvent) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(pending.title) }
    var dateString by remember { mutableStateOf(pending.dateString) }
    var startHour by remember {
        mutableIntStateOf(
            java.util.Calendar.getInstance()
                .apply { timeInMillis = pending.startTime }
                .get(java.util.Calendar.HOUR_OF_DAY)
        )
    }
    val durationMillis = pending.endTime - pending.startTime

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit activity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { dateString = DateUtils.addDays(dateString, -1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day")
                    }
                    Text(
                        DateUtils.formatForDisplay(dateString),
                        style = MaterialTheme.typography.titleSmall
                    )
                    IconButton(onClick = { dateString = DateUtils.addDays(dateString, 1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day")
                    }
                }
                Text("Starts at ${formatHourLabel(startHour)}", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = startHour.toFloat(),
                    onValueChange = { startHour = it.toInt() },
                    valueRange = 6f..21f,
                    steps = 14
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val start = DateUtils.millisFromDateString(dateString, startHour, 0)
                    onConfirm(
                        pending.copy(
                            title = title.trim(),
                            dateString = dateString,
                            startTime = start,
                            endTime = start + durationMillis
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
