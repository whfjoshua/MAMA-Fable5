package com.mama.scheduler.ui.screens.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mama.scheduler.core.DateUtils
import com.mama.scheduler.ui.components.AddEventDialog
import com.mama.scheduler.ui.components.EmptyState
import com.mama.scheduler.ui.components.EventCard
import kotlinx.coroutines.launch

@Composable
fun AgendaScreen(viewModel: AgendaViewModel = hiltViewModel()) {
    val events by viewModel.allEvents.collectAsStateWithLifecycle()
    val profiles by viewModel.allProfiles.collectAsStateWithLifecycle()
    val isParsing by viewModel.isParsing.collectAsStateWithLifecycle()
    val isFindingPhotos by viewModel.isFindingPhotos.collectAsStateWithLifecycle()

    var activeDate by rememberSaveable { mutableStateOf(DateUtils.todayString()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var nlInput by rememberSaveable { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val dayEvents = events.filter { it.dateString == activeDate }.sortedBy { it.startTime }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add activity")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (DateUtils.isToday(activeDate)) "Today" else DateUtils.formatForDisplay(activeDate),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "${dayEvents.size} ${if (dayEvents.size == 1) "activity" else "activities"} scheduled",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Week date picker
            WeekDatePicker(
                activeDate = activeDate,
                datesWithEvents = events.map { it.dateString }.toSet(),
                onDateSelected = { activeDate = it }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Natural language quick-add
            OutlinedTextField(
                value = nlInput,
                onValueChange = { nlInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Try \"Lexi swim tomorrow 3-4pm\"") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (isParsing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(
                            enabled = nlInput.isNotBlank(),
                            onClick = {
                                viewModel.parseSchedulerText(nlInput) { _, message ->
                                    scope.launch { snackbarHostState.showSnackbar(message) }
                                }
                                nlInput = ""
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Parse")
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Timeline
            if (dayEvents.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.EventAvailable,
                    title = "Nothing scheduled",
                    subtitle = "A free day! Add an activity with the + button or type a request above."
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(dayEvents, key = { it.id }) { event ->
                        Row {
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.width(64.dp)
                            ) {
                                Text(
                                    DateUtils.toDisplayTime(event.startTime),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    DateUtils.toDisplayTime(event.endTime),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            EventCard(
                                event = event,
                                kidColorHex = profiles.firstOrNull { it.id == event.kidId }?.colorHex,
                                isFindingPhotos = isFindingPhotos,
                                onDelete = { viewModel.deleteEvent(event) },
                                onFindPhotos = { viewModel.findAndSetEventPhotos(event) },
                                onClearPhotos = { viewModel.clearEventPhotos(event) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEventDialog(
            profiles = profiles,
            initialDate = activeDate,
            onConfirm = { event ->
                val conflicts = viewModel.checkConflicts(
                    event.startTime, event.endTime, event.travelBufferMinutes,
                    event.kidId, event.dateString
                )
                viewModel.addEvent(event)
                showAddDialog = false
                if (conflicts.isNotEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar("⚠️ Added with conflicts: ${conflicts.first().message}")
                    }
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun WeekDatePicker(
    activeDate: String,
    datesWithEvents: Set<String>,
    onDateSelected: (String) -> Unit
) {
    val weekDates = DateUtils.getWeekDates(activeDate)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(weekDates) { date ->
            val selected = date == activeDate
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                    .clickable { onDateSelected(date) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    DateUtils.formatDayOfWeek(date),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${DateUtils.dayOfMonth(date)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
                if (date in datesWithEvents) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.primary
                            )
                    )
                } else {
                    Spacer(modifier = Modifier.height(5.dp))
                }
            }
        }
    }
}
