package com.guarddroid.app.worker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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

    private fun scanAll() {
        val results = AppScanner.scanAllApps(applicationContext)
        ScanRepository.replaceAll(applicationContext, results)
        results.filter { it.isHighRisk }.forEach { maybeAlert(it) }
        Log.i(TAG, "Full scan complete: ${results.size} apps, ${results.count { it.isHighRisk }} high-risk")
    }

    private fun scanSingle(packageName: String) {
        val result = AppScanner.scanSingleApp(applicationContext, packageName) ?: return
        ScanRepository.upsert(applicationContext, result)
        if (result.isHighRisk) maybeAlert(result)
        Log.i(TAG, "Scanned $packageName -> ${result.riskScorePercent}% malicious")
    }

    /** Alerts once per (package, risk-bucket) so periodic scans don't spam. */
    private fun maybeAlert(result: AppScanResult) {
        val prefs = alertPrefs(applicationContext)
        val key = result.packageName
        val previous = prefs.getString(key, null)
        val signature = result.riskLevel.name
        if (previous == signature) return
        NotificationHelper.showThreatAlert(applicationContext, result)
        prefs.edit().putString(key, signature).apply()
    }

    companion object {
        private const val TAG = "AppScanWorker"
        const val KEY_PACKAGE = "target_package"

        private const val UNIQUE_PERIODIC = "guarddroid_periodic_scan"
        private const val UNIQUE_MANUAL = "guarddroid_manual_scan"
        private const val ALERT_PREFS = "guarddroid_alert_dedupe"

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

        /** Triggers a scan of a single package (used on install / replace). */
        fun enqueueSingleScan(context: Context, packageName: String) {
            val request = OneTimeWorkRequestBuilder<AppScanWorker>()
                .setInputData(workDataOf(KEY_PACKAGE to packageName))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "guarddroid_scan_$packageName",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
