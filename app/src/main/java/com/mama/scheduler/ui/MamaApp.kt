package com.mama.scheduler.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mama.scheduler.ui.screens.agenda.AgendaScreen
import com.mama.scheduler.ui.screens.approvals.ApprovalsScreen
import com.mama.scheduler.ui.screens.approvals.ApprovalsViewModel
import com.mama.scheduler.ui.screens.calendar.CalendarScreen
import com.mama.scheduler.ui.screens.chat.ChatScreen
import com.mama.scheduler.ui.screens.profiles.ProfilesScreen

sealed class MamaDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Agenda : MamaDestination("agenda", "Agenda", Icons.Filled.Today, Icons.Outlined.Today)
    data object Calendar : MamaDestination("calendar", "Calendar", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth)
    data object Approvals : MamaDestination("approvals", "Approvals", Icons.Filled.Checklist, Icons.Outlined.Checklist)
    data object Chat : MamaDestination("chat", "Assistant", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)
    data object Profiles : MamaDestination("profiles", "Family", Icons.Filled.Group, Icons.Outlined.Group)

    companion object {
        val all = listOf(Agenda, Calendar, Approvals, Chat, Profiles)
    }
}

@Composable
fun MamaApp(
    onGoogleSignIn: () -> Unit,
    approvalsBadgeViewModel: ApprovalsViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val pendingCount by approvalsBadgeViewModel.pendingEvents.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                MamaDestination.all.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            if (destination == MamaDestination.Approvals && pendingCount.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text("${pendingCount.size}") } }) {
                                    Icon(
                                        if (selected) destination.selectedIcon else destination.unselectedIcon,
                                        contentDescription = destination.label
                                    )
                                }
                            } else {
                                Icon(
                                    if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.label
                                )
                            }
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MamaDestination.Agenda.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(MamaDestination.Agenda.route) { AgendaScreen() }
            composable(MamaDestination.Calendar.route) { CalendarScreen() }
            composable(MamaDestination.Approvals.route) { ApprovalsScreen() }
            composable(MamaDestination.Chat.route) { ChatScreen() }
            composable(MamaDestination.Profiles.route) {
                ProfilesScreen(onGoogleSignIn = onGoogleSignIn)
            }
        }
    }
}
