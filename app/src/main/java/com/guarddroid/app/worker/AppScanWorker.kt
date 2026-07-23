package com.guarddroid.app.worker

import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.guarddroid.app.R
import com.guarddroid.app.data.AppScanResult
import com.guarddroid.app.data.ScanRepository
import com.guarddroid.app.ml.AppScanner
import com.guarddroid.app.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Runs the malware scan off the main thread.
 *
 * Two modes:
 *  - **Full scan** (no input package): re-scans every user app. Scheduled
 *    periodically and triggered by the "Scan Now" button.
 *  - **Single scan** ([KEY_PACKAGE] set): scans one freshly installed/updated
 *    package, fired by [com.guarddroid.app.receiver.PackageInstallReceiver].
 *
 * High-risk results raise an urgent notification (deduplicated so the same
 * package is not alerted twice at the same risk).
 */
class AppScanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val targetPackage = inputData.getString(KEY_PACKAGE)
        ScanRepository.setScanning(true)
        try {
            if (targetPackage != null) {
                scanSingle(targetPackage)
            } else {
                scanAll()
            }
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Scan failed", t)
            Result.retry()
        } finally {
            ScanRepository.setScanning(false)
        }
    }

    /**
     * Required for expedited work: on Android versions that back expedited jobs
     * with a foreground service, WorkManager shows this quiet notification.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification: Notification = NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_shield_alert)
            .setContentTitle(applicationContext.getString(R.string.scanning_title))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(SCAN_FGS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(SCAN_FGS_ID, notification)
        }
    }

    private fun scanAll() {
        val results = AppScanner.scanAllApps(applicationContext)
        ScanRepository.replaceAll(applicationContext, results)
        // Quieter alerts for a full sweep (no full-screen barrage), deduplicated.
        results.filter { it.isHighRisk }.forEach { maybeAlert(it) }
        val highRisk = results.count { it.isHighRisk }
        NotificationHelper.showScanSummary(applicationContext, results.size, highRisk)
        Log.i(TAG, "Full scan complete: ${results.size} apps, $highRisk high-risk")
    }

    private fun scanSingle(packageName: String) {
        val result = AppScanner.scanSingleApp(applicationContext, packageName) ?: return
        ScanRepository.upsert(applicationContext, result)
        // Newly installed app: loud full-screen alarm if high-risk, else an info card.
        NotificationHelper.notifyInstalledAppScanned(applicationContext, result)
        // Remember the alert signature so a later full scan won't re-alarm identically.
        alertPrefs(applicationContext).edit().putString(result.packageName, result.riskLevel.name).apply()
        Log.i(TAG, "Scanned $packageName -> ${result.riskScorePercent}% malicious (${result.riskLevel})")
    }

    /** Alerts once per (package, risk-bucket) so periodic scans don't spam. */
    private fun maybeAlert(result: AppScanResult) {
        val prefs = alertPrefs(applicationContext)
        val key = result.packageName
        val previous = prefs.getString(key, null)
        val signature = result.riskLevel.name
        if (previous == signature) return
        NotificationHelper.showThreatAlert(applicationContext, result, fullScreen = false)
        prefs.edit().putString(key, signature).apply()
    }

    companion object {
        private const val TAG = "AppScanWorker"
        const val KEY_PACKAGE = "target_package"

        private const val UNIQUE_PERIODIC = "guarddroid_periodic_scan"
        private const val UNIQUE_MANUAL = "guarddroid_manual_scan"
        private const val ALERT_PREFS = "guarddroid_alert_dedupe"
        private const val SCAN_FGS_ID = 909091

        private fun alertPrefs(context: Context): SharedPreferences =
            context.getSharedPreferences(ALERT_PREFS, Context.MODE_PRIVATE)

        /** Schedules the recurring background scan (idempotent). */
        fun schedulePeriodicScan(context: Context) {
            val request = PeriodicWorkRequestBuilder<AppScanWorker>(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Triggers an immediate full scan (used by the "Scan Now" button). */
        fun enqueueFullScan(context: Context) {
            val request = OneTimeWorkRequestBuilder<AppScanWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_MANUAL,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        /**
         * Triggers a scan of a single package (used on install / replace).
         * Expedited so the alert fires as soon as possible after installation,
         * even while GuardDroid is in the background.
         */
        fun enqueueSingleScan(context: Context, packageName: String) {
            val request = OneTimeWorkRequestBuilder<AppScanWorker>()
                .setInputData(workDataOf(KEY_PACKAGE to packageName))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "guarddroid_scan_$packageName",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
