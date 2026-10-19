package com.pointblank.lpms.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.pointblank.lpms.core.model.Role

data class NavItem(val key: String, val label: String, val icon: ImageVector)

private val dashboard = NavItem("dashboard", "Dashboard", Icons.Filled.Dashboard)
private val schools = NavItem("schools", "Schools", Icons.Filled.School)
private val teachers = NavItem("teachers", "Teachers", Icons.Filled.Person)
private val learners = NavItem("learners", "Learners", Icons.Filled.ChildCare)
private val sessions = NavItem("sessions", "Sessions", Icons.Filled.CalendarMonth)
private val attendance = NavItem("attendance", "Attendance", Icons.Filled.CheckCircle)
private val feedback = NavItem("feedback", "Feedback", Icons.Filled.Star)
private val reports = NavItem("reports", "Reports", Icons.Filled.BarChart)
private val resources = NavItem("resources", "Resources", Icons.Filled.Folder)
private val shared = NavItem("shared", "Shared Resources", Icons.Filled.Share)
private val parent = NavItem("parent", "Parent Portal", Icons.Filled.Groups)
private val analytics = NavItem("analytics", "Analytics", Icons.Filled.ShowChart)
private val progress = NavItem("progress", "Progress", Icons.Filled.ShowChart)
private val visits = NavItem("visits", "School Visits", Icons.Filled.DirectionsWalk)
private val children = NavItem("children", "My Children", Icons.Filled.ChildCare)
private val messages = NavItem("messages", "Messages", Icons.Filled.Chat)

fun navItemsFor(role: Role): List<NavItem> = when (role) {
    Role.LEARNER -> listOf(dashboard, attendance, progress, feedback, resources)
    Role.PARENT -> listOf(dashboard, children, messages, feedback, resources)
    Role.TEACHER -> listOf(dashboard, NavItem("my-learners", "My Learners", Icons.Filled.ChildCare),
        sessions, attendance, progress, messages, feedback, resources, shared, analytics)
    Role.COORDINATOR -> listOf(dashboard, NavItem("users", "Accounts", Icons.Filled.Groups), schools, teachers, learners,
        sessions, attendance, visits, messages, feedback, reports, resources, shared, parent, analytics)
    Role.ADMIN -> listOf(dashboard, NavItem("users", "User Management", Icons.Filled.Groups), schools, teachers,
        learners, sessions, attendance, visits, messages, feedback, resources, shared, parent, reports, analytics,
        NavItem("audit", "Activity Log", Icons.Filled.History))
}
