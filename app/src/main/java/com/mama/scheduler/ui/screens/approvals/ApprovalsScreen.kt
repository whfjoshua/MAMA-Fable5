package com.mama.scheduler.ui.screens.approvals

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mama.scheduler.core.DateUtils
import com.mama.scheduler.data.local.PendingApprovalEvent
import com.mama.scheduler.ui.components.EditPendingDialog
import com.mama.scheduler.ui.components.EmptyState
import com.mama.scheduler.ui.components.KidAvatar

@Composable
fun ApprovalsScreen(viewModel: ApprovalsViewModel = hiltViewModel()) {
    val pendingEvents by viewModel.pendingEvents.collectAsStateWithLifecycle()
    val profiles by viewModel.allProfiles.collectAsStateWithLifecycle()
    // Collected so conflictsFor() sees fresh events
    val allEvents by viewModel.allEvents.collectAsStateWithLifecycle()

    var editingEvent by remember { mutableStateOf<PendingApprovalEvent?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text("Approvals", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = if (pendingEvents.isEmpty()) "You're all caught up"
            else "${pendingEvents.size} awaiting your review",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (pendingEvents.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Inbox,
                title = "No pending activities",
                subtitle = "When you add activities with natural language or AI chat, they land here for a quick review."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(pendingEvents, key = { it.id }) { pending ->
                    val conflicts = viewModel.conflictsFor(pending)
                    val kidColor = profiles.firstOrNull { it.id == pending.kidId }?.colorHex

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                KidAvatar(name = pending.kidName, colorHex = kidColor)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            pending.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (pending.isRecurring) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                Icons.Filled.Repeat,
                                                contentDescription = "Recurring",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Text(
                                        "${pending.kidName ?: "Family"} · ${DateUtils.formatForDisplay(pending.dateString)} · ${DateUtils.formatTimeRange(pending.startTime, pending.endTime)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { editingEvent = pending }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                                }
                            }

                            Text(
                                "\"${pending.rawInput}\"",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )

                            if (conflicts.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        conflicts.forEach { conflict ->
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Filled.WarningAmber,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    conflict.message,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.approve(pending) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Approve")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.reject(pending) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reject")
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    editingEvent?.let { pending ->
        EditPendingDialog(
            pending = pending,
            onConfirm = {
                viewModel.update(it)
                editingEvent = null
            },
            onDismiss = { editingEvent = null }
        )
    }
}
