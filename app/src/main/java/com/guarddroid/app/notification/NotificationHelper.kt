package com.guarddroid.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.guarddroid.app.MainActivity
import com.guarddroid.app.R
import com.guarddroid.app.alert.ThreatAlertActivity
import com.guarddroid.app.data.AppScanResult
import com.guarddroid.app.data.RiskLevel

/**
 * Creates the notification channels and posts alerts.
 *
 * Two experiences:
 *  - **High-risk newly-installed app** → a loud, high-priority notification
 *    whose *full-screen intent* launches [ThreatAlertActivity] (alarm tone +
 *    vibration + over-lock-screen popup) even when GuardDroid is in the
 *    background.
 *  - **Any other scanned app** → an informational notification carrying the
 *    package name, malicious percentage and criticality.
 */
object NotificationHelper {

    const val CHANNEL_THREATS = "guarddroid_threats"
    const val CHANNEL_STATUS = "guarddroid_status"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        val alarmSound = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val threats = NotificationChannel(
            CHANNEL_THREATS,
            context.getString(R.string.channel_threats_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_threats_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 300, 500)
            setSound(alarmSound, alarmAttrs)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val status = NotificationChannel(
            CHANNEL_STATUS,
            context.getString(R.string.channel_status_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_status_desc)
        }

        manager.createNotificationChannel(threats)
        manager.createNotificationChannel(status)
    }

    /**
     * Notifies about a freshly installed app. High-risk apps get the loud
     * full-screen alarm; everything else gets a quiet informational card.
     */
    fun notifyInstalledAppScanned(context: Context, result: AppScanResult) {
        if (result.riskLevel == RiskLevel.HIGH) {
            showThreatAlert(context, result, fullScreen = true)
        } else {
            showInfoNotification(context, result)
        }
    }

    /** High-priority threat alert. When [fullScreen] it also launches the alarm popup. */
    fun showThreatAlert(context: Context, result: AppScanResult, fullScreen: Boolean) {
        if (!hasNotificationPermission(context)) {
            // Even without notification permission we can still try the full-screen alert.
            if (fullScreen) runCatching { context.startActivity(alertIntent(context, result)) }
            return
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_THREATS)
            .setSmallIcon(R.drawable.ic_shield_alert)
            .setContentTitle(context.getString(R.string.alert_title))
            .setContentText(context.getString(R.string.alert_text, result.appLabel, result.riskScorePercent))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(
                        R.string.alert_big_text,
                        result.appLabel,
                        result.riskScorePercent,
                        result.modelPermissions.size,
                    )
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setColor(0xFFD32F2F.toInt())
            .setAutoCancel(true)
            .setContentIntent(MainActivity.detailPendingIntent(context, result.packageName))

        if (fullScreen) {
            builder.setFullScreenIntent(alertPendingIntent(context, result), true)
        }

        NotificationManagerCompat.from(context).notify(result.packageName.hashCode(), builder.build())
    }

    private fun showInfoNotification(context: Context, result: AppScanResult) {
        if (!hasNotificationPermission(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_shield_alert)
            .setContentTitle(context.getString(R.string.install_scan_title))
            .setContentText(
                context.getString(
                    R.string.install_scan_text,
                    result.appLabel,
                    result.riskScorePercent,
                    criticalityLabel(result.riskLevel),
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(MainActivity.detailPendingIntent(context, result.packageName))
            .build()
        NotificationManagerCompat.from(context).notify(result.packageName.hashCode(), notification)
    }

    /** Summary posted after a full manual/background scan completes. */
    fun showScanSummary(context: Context, scanned: Int, highRisk: Int) {
        if (!hasNotificationPermission(context)) return
        val text = if (highRisk > 0) {
            context.getString(R.string.summary_text_risky, scanned, highRisk)
        } else {
            context.getString(R.string.summary_text_clean, scanned)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_shield_alert)
            .setContentTitle(context.getString(R.string.summary_title))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(MainActivity.launchPendingIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(SUMMARY_ID, notification)
    }

    private fun alertIntent(context: Context, result: AppScanResult) =
        ThreatAlertActivity.buildIntent(
            context = context,
            packageName = result.packageName,
            label = result.appLabel,
            version = result.versionName,
            percent = result.riskScorePercent,
            criticality = criticalityLabel(result.riskLevel),
            permCount = result.modelPermissions.size,
            topPerms = result.modelPermissions.take(6),
        )

    private fun alertPendingIntent(context: Context, result: AppScanResult): PendingIntent =
        PendingIntent.getActivity(
            context,
            result.packageName.hashCode(),
            alertIntent(context, result),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun criticalityLabel(level: RiskLevel): String = when (level) {
        RiskLevel.HIGH -> "Critical"
        RiskLevel.MODERATE -> "Moderate"
        RiskLevel.LOW -> "Low"
        RiskLevel.SAFE -> "Safe"
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private const val SUMMARY_ID = 424242
}
