package com.pointblank.lpms.core.util

import com.pointblank.lpms.core.model.Session
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val display = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun formatDisplayDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(display) }.getOrDefault(iso)

/** Matches the website's session picker text, e.g. "Reading Boost · 21 Jun 2026". */
val Session.label: String get() = "$programme \u00B7 ${formatDisplayDate(date)}"

/** "Just now", "3h ago", "2d ago" for feed items. */
fun timeAgo(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - epochMillis) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        else -> "${minutes / (60 * 24)}d ago"
    }
}
