package com.mama.scheduler.ui.screens.profiles

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mama.scheduler.ui.components.AddProfileDialog
import com.mama.scheduler.ui.components.KidAvatar
import kotlinx.coroutines.launch

@Composable
fun ProfilesScreen(
    onGoogleSignIn: () -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel()
) {
    val profiles by viewModel.allProfiles.collectAsStateWithLifecycle()
    val geminiApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val morningNotifEnabled by viewModel.morningNotifEnabled.collectAsStateWithLifecycle()
    val morningNotifHour by viewModel.morningNotifHour.collectAsStateWithLifecycle()
    val morningNotifMinute by viewModel.morningNotifMinute.collectAsStateWithLifecycle()
    val googleAccount by viewModel.googleAccount.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    var showAddProfileDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.syncWithSystemCalendar { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Calendar permission is needed to sync.") }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Family", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Profiles, preferences and connections",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ---- Kid profiles ----
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Kids",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showAddProfileDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add kid")
                    }
                }
                profiles.forEach { kid ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        KidAvatar(name = kid.name, colorHex = kid.colorHex, size = 42.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(kid.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Up to ${kid.dailyLimit} ${if (kid.dailyLimit == 1) "activity" else "activities"} per day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.deleteKidProfile(kid) }) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Delete ${kid.name}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // ---- Appearance ----
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Material You colors", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Match the app to your wallpaper (Android 12+)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = dynamicColor, onCheckedChange = { viewModel.setDynamicColor(it) })
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // ---- Morning summary ----
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Morning summary", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Daily schedule notification at %d:%02d".format(morningNotifHour, morningNotifMinute),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = morningNotifEnabled,
                        onCheckedChange = {
                            viewModel.setMorningNotification(it, morningNotifHour, morningNotifMinute)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // ---- Gemini API key ----
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Gemini API key",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                var keyInput by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }
                var keyVisible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API key (overrides build key)") },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (keyVisible) "Hide key" else "Show key"
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.saveGeminiApiKey(keyInput)
                        scope.launch { snackbarHostState.showSnackbar("API key saved") }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save key") }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // ---- Calendar sync ----
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Calendar sync",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (googleAccount == null) {
                    Button(onClick = onGoogleSignIn, modifier = Modifier.fillMaxWidth()) {
                        Text("Sign in with Google")
                    }
                } else {
                    Text(
                        "Signed in as ${googleAccount?.email}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        enabled = !isSyncing,
                        onClick = {
                            viewModel.syncWithGoogleCalendar { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sync with Google Calendar") }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.signOutFromGoogle { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sign out") }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Device calendar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    enabled = !isSyncing,
                    onClick = {
                        calendarPermissionLauncher.launch(
                            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Sync with device calendar") }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showAddProfileDialog) {
        AddProfileDialog(
            onConfirm = { name, colorHex, dailyLimit ->
                viewModel.addKidProfile(name, colorHex, dailyLimit)
                showAddProfileDialog = false
            },
            onDismiss = { showAddProfileDialog = false }
        )
    }
}

@Composable
private fun SectionCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
