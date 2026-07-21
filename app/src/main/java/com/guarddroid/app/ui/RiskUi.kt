package com.guarddroid.app.ui

import androidx.compose.ui.graphics.Color
import com.guarddroid.app.data.RiskLevel
import com.guarddroid.app.ui.theme.RiskHigh
import com.guarddroid.app.ui.theme.RiskLow
import com.guarddroid.app.ui.theme.RiskModerate
import com.guarddroid.app.ui.theme.RiskSafe
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun RiskLevel.color(): Color = when (this) {
    RiskLevel.SAFE -> RiskSafe
    RiskLevel.LOW -> RiskLow
    RiskLevel.MODERATE -> RiskModerate
    RiskLevel.HIGH -> RiskHigh
}

fun RiskLevel.label(): String = when (this) {
    RiskLevel.SAFE -> "Safe"
    RiskLevel.LOW -> "Low risk"
    RiskLevel.MODERATE -> "Moderate risk"
    RiskLevel.HIGH -> "High risk"
}

/** Human-friendly permission name, e.g. "android.permission.SEND_SMS" -> "Send SMS". */
fun prettyPermission(permission: String): String {
    val leaf = permission.substringAfterLast('.')
    return leaf.split('_')
        .joinToString(" ") { part ->
            part.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) }
        }
}

fun formatTimestamp(millis: Long): String {
    if (millis <= 0L) return "Never"
    val formatter = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    return formatter.format(Date(millis))
}
