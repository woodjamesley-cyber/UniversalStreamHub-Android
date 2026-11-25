package com.universalstreamhub.app.util

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index <= 1) {
        String.format(Locale.US, "%.0f %s", value, units[index])
    } else {
        String.format(Locale.US, "%.1f %s", value, units[index])
    }
}

fun formatRuntime(minutes: Int?): String? {
    if (minutes == null || minutes <= 0) return null
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) "${hours}h ${rest}m" else "${rest}m"
}

fun formatStorageLimit(gb: Int): String = when {
    gb <= 0 -> "Unlimited"
    gb >= 1024 -> "${gb / 1024} TB"
    else -> "$gb GB"
}

fun formatPlaybackTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun ratingLabel(value: Double): String? =
    if (value <= 0) null else String.format(Locale.US, "%.1f", value)
