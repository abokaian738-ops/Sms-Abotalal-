package com.example.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())

    fun formatMessageTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 24 * 3600 * 1000 -> timeFormat.format(Date(timestamp))
            diff < 48 * 3600 * 1000 -> "أمس " + timeFormat.format(Date(timestamp))
            diff < 7 * 24 * 3600 * 1000 -> dateFormat.format(Date(timestamp))
            else -> fullDateFormat.format(Date(timestamp))
        }
    }

    fun formatDetailedDate(timestamp: Long): String {
        return fullDateFormat.format(Date(timestamp))
    }
}
