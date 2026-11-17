@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.pluvist.sachet.next

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun appIcon(key: String): ImageVector = when (key) {
    "cultivation" -> Icons.Rounded.School
    "textbooks" -> Icons.Rounded.Book
    "exams" -> Icons.Rounded.Schedule
    "grades" -> Icons.Rounded.HistoryEdu
    "gpa" -> Icons.Rounded.Leaderboard
    "transcript" -> Icons.Rounded.Description
    "library" -> Icons.Rounded.LocalLibrary
    "physics" -> Icons.Rounded.Science
    "network" -> Icons.Rounded.Wifi
    "classrooms" -> Icons.Rounded.MeetingRoom
    "calendar" -> Icons.Rounded.DateRange
    "news" -> Icons.Rounded.Newspaper
    "mail" -> Icons.Rounded.Email
    "devices" -> Icons.Rounded.Devices
    "portal" -> Icons.Rounded.AccountBalance
    "website" -> Icons.Rounded.Public
    "login" -> Icons.Rounded.Person
    "permissions" -> Icons.Rounded.Notifications
    "settings" -> Icons.Rounded.Settings
    "appearance" -> Icons.Rounded.Palette
    "files" -> Icons.Rounded.ImportExport
    "fileManager" -> Icons.Rounded.Folder
    "widget" -> Icons.Rounded.Widgets
    "logout" -> Icons.AutoMirrored.Rounded.Logout
    "source" -> Icons.Rounded.Code
    "semester" -> Icons.Rounded.CalendarMonth
    "activity" -> Icons.Rounded.Timelapse
    "reminders" -> Icons.Rounded.NotificationsActive
    "theme" -> Icons.Rounded.DarkMode
    "accent" -> Icons.Rounded.ColorLens
    else -> Icons.Rounded.AutoAwesome
}

@Composable
internal fun appIconContainerPair(key: String): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (key) {
        "cultivation", "network", "settings", "semester", "portal", "transcript", "devices", "login" ->
            scheme.primaryContainer to scheme.onPrimaryContainer
        "textbooks", "grades", "course_done", "activity", "fileManager", "library", "source" ->
            scheme.secondaryContainer to scheme.onSecondaryContainer
        "exams", "gpa", "classrooms", "physics", "appearance", "accent", "theme", "widget", "reminders", "permissions", "files", "news", "mail", "website", "course_running" ->
            scheme.tertiaryContainer to scheme.onTertiaryContainer
        "calendar", "logout" ->
            scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.primaryContainer to scheme.onPrimaryContainer
    }
}

/** Tonal M3 Expressive icon badge supporting both MaterialShapes polygons and rounded containers. */
@Composable
internal fun AppIconBadge(
    key: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    useExpressivePolygon: Boolean = false,
    shape: Shape = if (useExpressivePolygon) rememberFeaturePolygonShape(key) else MaterialTheme.shapes.medium,
) {
    val (container, content) = appIconContainerPair(key)
    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = container,
        contentColor = content,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = appIcon(key),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
