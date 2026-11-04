package com.pluvist.sachet.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.LocalGlassColors

internal fun appIcon(key:String):ImageVector=when(key) {
    "cultivation"->Icons.Default.School
    "textbooks"->Icons.Default.Book
    "exams"->Icons.Default.Schedule
    "grades"->Icons.Default.HistoryEdu
    "gpa"->Icons.Default.Leaderboard
    "transcript"->Icons.Default.Description
    "library"->Icons.Default.LocalLibrary
    "physics"->Icons.Default.Science
    "network"->Icons.Default.Wifi
    "classrooms"->Icons.Default.MeetingRoom
    "calendar"->Icons.Default.DateRange
    "news"->Icons.Default.Newspaper
    "mail"->Icons.Default.Email
    "devices"->Icons.Default.Devices
    "portal"->Icons.Default.AccountBalance
    "website"->Icons.Default.Public
    "login"->Icons.Default.Person
    "permissions"->Icons.Default.Notifications
    "settings"->Icons.Default.Settings
    "appearance"->Icons.Default.Palette
    "files"->Icons.Default.ImportExport
    "fileManager"->Icons.Default.Folder
    "widget"->Icons.Default.Widgets
    "logout"->Icons.Default.Logout
    "source"->Icons.Default.Code
    "semester"->Icons.Default.CalendarMonth
    "activity"->Icons.Default.Timelapse
    "reminders"->Icons.Default.NotificationsActive
    "theme"->Icons.Default.DarkMode
    "accent"->Icons.Default.ColorLens
    else->Icons.Default.Info
}

/** Bright solid badge colors, following the pre-rewrite MiuixIconBadge treatment. */
internal fun appIconColor(key:String):Color=Color(when(key) {
    "cultivation", "network", "settings", "devices", "website" -> 0xFF3482FF
    "textbooks", "portal", "login", "widget" -> 0xFF7962FF
    "exams", "classrooms", "permissions", "fileManager" -> 0xFFFFA922
    "grades", "course_done" -> 0xFF28C65B
    "gpa" -> 0xFFA45AFF
    "transcript" -> 0xFF328BE8
    "library" -> 0xFF00B6C7
    "physics" -> 0xFFF34F85
    "calendar", "logout" -> 0xFFFF515E
    "news", "files", "course_running" -> 0xFFFF9822
    "mail" -> 0xFF3D78FF
    "appearance" -> 0xFFE74CAF
    "source" -> 0xFF00BFA5
    "semester" -> 0xFF3482FF
    "activity" -> 0xFF25BAA5
    "reminders" -> 0xFFFFA922
    "theme" -> 0xFF7962FF
    "accent" -> 0xFFE74CAF
    else -> 0xFF3482FF
})

@Composable internal fun AppIconBadge(key:String,compact:Boolean=false) {
    val tint=appIconColor(key)
    val isDark=LocalGlassColors.current.isDark
    val scale=io.github.neoruaa.glasmio.core.LocalGlasmioLayoutFontScale.current.coerceIn(1f,1.35f)
    val slotSize=(if(compact)30f else 34f)*scale
    val badgeSize=slotSize*.9f
    val badgeShape=RoundedCornerShape((badgeSize*.28f).dp)
    // Cache two soft colored halos behind the opaque badge. The white glyph stays crisp.
    Box(Modifier.size(slotSize.dp).drawWithCache {
        val radius=size.width*.72f
        val alpha=if(isDark) .23f else .32f
        val left=Offset(size.width*.24f,size.height*.68f)
        val right=Offset(size.width*.82f,size.height*.30f)
        val leftGlow=Brush.radialGradient(listOf(tint.copy(alpha=alpha),tint.copy(alpha=alpha*.43f),Color.Transparent),center=left,radius=radius)
        val rightGlow=Brush.radialGradient(listOf(tint.copy(alpha=alpha*.7f),tint.copy(alpha=alpha*.27f),Color.Transparent),center=right,radius=radius)
        onDrawBehind {
            drawCircle(leftGlow,radius,left)
            drawCircle(rightGlow,radius,right)
        }
    },contentAlignment=Alignment.Center) {
        Box(Modifier.size(badgeSize.dp)
            .background(tint,badgeShape)
            .border(.8.dp,Brush.linearGradient(listOf(Color.White.copy(alpha=if(isDark).48f else .88f),Color.White.copy(alpha=.10f),tint.copy(alpha=.46f))),badgeShape),
            contentAlignment=Alignment.Center) {
            Icon(appIcon(key),null,Modifier.size((badgeSize*.62f).dp),tint=Color.White)
        }
    }
}
