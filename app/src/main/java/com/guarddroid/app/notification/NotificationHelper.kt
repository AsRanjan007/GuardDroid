package com.guarddroid.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.guarddroid.app.MainActivity
import com.guarddroid.app.R
import com.guarddroid.app.data.AppScanResult

/**
 * Creates the notification channels and posts urgent threat alerts when a
 * scanned app crosses the malicious-probability threshold.
 */
object NotificationHelper {

    const val CHANNEL_THREATS = "guarddroid_threats"
    const val CHANNEL_STATUS = "guarddroid_status"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        val threats = NotificationChannel(
            CHANNEL_THREATS,
            context.getString(R.string.channel_threats_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_threats_desc)
            enableVibration(true)
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

    /** Posts a high-priority alert for a single dangerous app. */
    fun showThreatAlert(context: Context, result: AppScanResult) {
        if (!hasNotificationPermission(context)) return

        val contentIntent = MainActivity.detailPendingIntent(context, result.packageName)

        val notification = NotificationCompat.Builder(context, CHANNEL_THREATS)
            .setSmallIcon(R.drawable.ic_shield_alert)
            .setContentTitle(context.getString(R.string.alert_title))
            .setContentText(
                context.getString(
                    R.string.alert_text,
                    result.appLabel,
                    result.riskScorePercent,
                )
            )
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setColor(0xFFD32F2F.toInt())
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context)
            .notify(result.packageName.hashCode(), notification)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
