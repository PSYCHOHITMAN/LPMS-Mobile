package com.pointblank.lpms.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import com.pointblank.lpms.LpmsApplication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pointblank.lpms.core.model.Role
import com.pointblank.lpms.core.model.User
import com.pointblank.lpms.ui.AppViewModelProvider
import com.pointblank.lpms.ui.components.*
import com.pointblank.lpms.ui.messages.MessagesScreen
import com.pointblank.lpms.ui.navigation.navItemsFor
import com.pointblank.lpms.ui.screens.*
import com.pointblank.lpms.ui.teacher.MyLearnersScreen
import com.pointblank.lpms.ui.teacher.TeacherAttendanceScreen
import com.pointblank.lpms.ui.theme.*
import kotlinx.coroutines.launch

/** Signed-in shell: drawer = the website's sidebar, top bar = its header. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    user: User,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val monitor = remember { (context.applicationContext as LpmsApplication).container.networkMonitor }
    val online by monitor.online.collectAsStateWithLifecycle()
    val seeding by viewModel.seeding.collectAsStateWithLifecycle()
    val seedMessage by viewModel.seedMessage.collectAsStateWithLifecycle()
    val items = remember(user.role) { navItemsFor(user.role) }
    var current by rememberSaveable { mutableStateOf("dashboard") }
    // Set when a teacher taps "Mark attendance" on a session, so the register opens on that session.
    var attendanceSession by remember { mutableStateOf<String?>(null) }
    val screensVm: ScreensViewModel = viewModel(factory = AppViewModelProvider.Factory)
    LaunchedEffect(current, user.id) { screensVm.refresh() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    LaunchedEffect(current) { if (current == "dashboard") viewModel.refresh() }
    // Back closes the menu, then returns to the dashboard; only from the dashboard does it leave the app.
    BackHandler(enabled = drawer.isOpen || current != "dashboard") {
        if (drawer.isOpen) scope.launch { drawer.close() } else current = "dashboard"
    }
    val title = when {
        current == "notifications" -> "Notifications"
        user.role == Role.TEACHER && current == "attendance" -> "Mark Attendance"
        else -> items.firstOrNull { it.key == current }?.label ?: "Dashboard"
    }
    val initials = user.displayName.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Sand) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                    LpmsLogo(Modifier.padding(vertical = 16.dp).fillMaxWidth())
                    items.forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.label) },
                            icon = { Icon(item.icon, null) },
                            selected = current == item.key,
                            onClick = { attendanceSession = null; current = item.key; scope.launch { drawer.close() } },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = BrandTint, selectedTextColor = BrandDark,
                                selectedIconColor = Brand, unselectedContainerColor = Sand,
                                unselectedIconColor = Muted
                            )
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Line)
                    NavigationDrawerItem(label = { Text("Settings") }, icon = { Icon(Icons.Filled.Settings, null) },
                        selected = false, onClick = { scope.launch { drawer.close() }; onSettings() },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Sand, unselectedIconColor = Muted))
                    NavigationDrawerItem(label = { Text("Help") }, icon = { Icon(Icons.Filled.Info, null) },
                        selected = false, onClick = { scope.launch { drawer.close() }; onHelp() },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Sand, unselectedIconColor = Muted))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Filled.Menu, "Menu") }
                    },
                    actions = {
                        IconButton(onClick = { current = "notifications" }) {
                            Icon(Icons.Filled.Notifications, "Notifications")
                        }
                        Box(Modifier.padding(end = 12.dp).size(34.dp).background(Brand, CircleShape), Alignment.Center) {
                            Text(initials, color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Sand)
                )
            }
        ) { padding ->
            Column(Modifier.padding(padding)) {
            if (!online) OfflineBanner()
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (current == "dashboard") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Hello, ${user.displayName.substringBefore(' ')}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        RoleBadge(user.role)
                    }
                    when (user.role) {
                        Role.LEARNER -> LearnerDashboard(data)
                        Role.PARENT -> ParentDashboard(data) { current = it }
                        Role.TEACHER -> TeacherDashboard(data) { current = it }
                        Role.COORDINATOR -> CoordinatorDashboard(data) { current = it }
                        Role.ADMIN -> AdminDashboard(data, seeding, seedMessage, viewModel::seedDemoData) { current = it }
                    }
                } else if (user.role == Role.TEACHER && current == "my-learners") {
                    MyLearnersScreen()
                } else if (user.role == Role.TEACHER && current == "attendance") {
                    TeacherAttendanceScreen(initialSessionId = attendanceSession)
                } else {
                    when (current) {
                        "schools" -> SchoolsScreen(user)
                        "teachers" -> TeachersScreen()
                        "learners" -> LearnersScreen(user)
                        "children" -> ChildrenScreen()
                        "messages" -> MessagesScreen()
                        "visits" -> VisitsScreen(user)
                        "audit" -> AuditScreen()
                        "sessions" -> SessionsScreen(user, onMarkAttendance = { sessionId -> attendanceSession = sessionId; current = "attendance" })
                        "attendance" -> AttendanceOverviewScreen(user)
                        "progress" -> ProgressScreen(user)
                        "feedback" -> FeedbackScreen(user)
                        "resources" -> ResourcesScreen(user)
                        "shared" -> SharedResourcesScreen(user)
                        "reports" -> ReportsScreen()
                        "analytics" -> AnalyticsScreen()
                        "parent" -> ParentPortalScreen()
                        "users" -> UserManagementScreen(user)
                        "notifications" -> NotificationsScreen()
                        else -> LpmsCard {
                            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("This screen is still to be built.", color = Muted, fontSize = 14.sp)
                        }
                    }
                }
            }
            }
        }
    }
}
