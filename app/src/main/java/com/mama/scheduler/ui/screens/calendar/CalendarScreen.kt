package com.mama.scheduler.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mama.scheduler.core.DateUtils
import com.mama.scheduler.data.local.KidProfile
import com.mama.scheduler.data.local.ScheduledEvent
import com.mama.scheduler.ui.components.EmptyState
import com.mama.scheduler.ui.components.EventCard
import com.mama.scheduler.ui.components.KidDot
import com.mama.scheduler.ui.components.safeParseColor
import java.time.LocalDate

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val events by viewModel.allEvents.collectAsStateWithLifecycle()
    val profiles by viewModel.allProfiles.collectAsStateWithLifecycle()

    var viewMode by rememberSaveable { mutableIntStateOf(0) } // 0=Month 1=Week 2=Day
    var selectedDate by rememberSaveable { mutableStateOf(DateUtils.todayString()) }
    var displayYear by rememberSaveable { mutableIntStateOf(DateUtils.currentYear()) }
    var displayMonth by rememberSaveable { mutableIntStateOf(DateUtils.currentMonth()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text("Calendar", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(12.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("Month", "Week", "Day").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = viewMode == index,
                    onClick = { viewMode = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                ) { Text(label) }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        when (viewMode) {
            0 -> MonthView(
                year = displayYear,
                month = displayMonth,
                events = events,
                profiles = profiles,
                selectedDate = selectedDate,
                onDateSelected = {
                    selectedDate = it
                    viewMode = 2
                },
                onPrevMonth = {
                    if (displayMonth == 1) { displayMonth = 12; displayYear-- } else displayMonth--
                },
                onNextMonth = {
                    if (displayMonth == 12) { displayMonth = 1; displayYear++ } else displayMonth++
                }
            )
            1 -> WeekView(
                selectedDate = selectedDate,
                events = events,
                profiles = profiles,
                onDateSelected = { selectedDate = it }
            )
            else -> DayView(
                selectedDate = selectedDate,
                events = events,
                profiles = profiles,
                onPrevDay = { selectedDate = DateUtils.addDays(selectedDate, -1) },
                onNextDay = { selectedDate = DateUtils.addDays(selectedDate, 1) }
            )
        }
    }
}

// ---------------- Month ----------------

@Composable
private fun MonthView(
    year: Int,
    month: Int,
    events: List<ScheduledEvent>,
    profiles: List<KidProfile>,
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val monthName = LocalDate.of(year, month, 1).month.name.lowercase()
        .replaceFirstChar { it.uppercase() }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onPrevMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month")
            }
            Text("$monthName $year", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onNextMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(
                    day,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        val firstDayOffset = DateUtils.firstDayOfWeekOfMonth(year, month)
        val daysInMonth = DateUtils.daysInMonth(year, month)
        val cells: List<Int?> = List(firstDayOffset) { null } + (1..daysInMonth).toList()

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(cells) { day ->
                if (day == null) {
                    Box(modifier = Modifier.aspectRatio(1f))
                } else {
                    val dateStr = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month, day)
                    val dayEvents = events.filter { it.dateString == dateStr }
                    val isToday = DateUtils.isToday(dateStr)
                    val isSelected = dateStr == selectedDate

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    isToday -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                }
                            )
                            .clickable { onDateSelected(dateStr) }
                            .padding(top = 6.dp)
                    ) {
                        Text(
                            "$day",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            dayEvents.take(3).forEach { event ->
                                KidDot(profiles.firstOrNull { it.id == event.kidId }?.colorHex, size = 5.dp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Legend
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            profiles.forEach { kid ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KidDot(kid.colorHex)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(kid.name, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ---------------- Week ----------------

@Composable
private fun WeekView(
    selectedDate: String,
    events: List<ScheduledEvent>,
    profiles: List<KidProfile>,
    onDateSelected: (String) -> Unit
) {
    val weekDates = DateUtils.getWeekDates(selectedDate)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(weekDates) { date ->
            val dayEvents = events.filter { it.dateString == date }.sortedBy { it.startTime }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (DateUtils.isToday(date)) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                    .clickable { onDateSelected(date) }
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DateUtils.formatForDisplay(date),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (DateUtils.isToday(date)) FontWeight.Bold else FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (dayEvents.isNotEmpty()) {
                        Text(
                            "${dayEvents.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                dayEvents.forEach { event ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KidDot(profiles.firstOrNull { it.id == event.kidId }?.colorHex)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${DateUtils.toDisplayTime(event.startTime)} · ${event.title}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ---------------- Day ----------------

@Composable
private fun DayView(
    selectedDate: String,
    events: List<ScheduledEvent>,
    profiles: List<KidProfile>,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit
) {
    val dayEvents = events.filter { it.dateString == selectedDate }.sortedBy { it.startTime }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onPrevDay) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day")
            }
            Text(DateUtils.formatForDisplay(selectedDate), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onNextDay) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (dayEvents.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.CalendarMonth,
                title = "No activities",
                subtitle = "Nothing scheduled for this day."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(dayEvents, key = { it.id }) { event ->
                    Row {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    safeParseColor(
                                        profiles.firstOrNull { it.id == event.kidId }?.colorHex,
                                        MaterialTheme.colorScheme.primary
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        EventCard(
                            event = event,
                            kidColorHex = profiles.firstOrNull { it.id == event.kidId }?.colorHex,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}
