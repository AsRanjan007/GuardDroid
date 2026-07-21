package com.guarddroid.app.ml

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.guarddroid.app.data.AppScanResult

/**
 * Orchestrates a scan: enumerate apps -> extract permissions -> run the model.
 *
 * A fresh [InferenceEngine] is created per batch and closed when done so native
 * resources are never leaked between background jobs.
 */
object AppScanner {

    private const val TAG = "AppScanner"

    /** Scans every user-installed app. Returns results (may be empty on error). */
    fun scanAllApps(context: Context): List<AppScanResult> {
        val engine = runCatching { InferenceEngine.create(context) }.getOrElse {
            Log.e(TAG, "Model unavailable — aborting scan", it)
            return emptyList()
        }
        return engine.use { inference ->
            val now = System.currentTimeMillis()
            PermissionExtractor.getUserInstalledPackages(context).mapNotNull { info ->
                runCatching {
                    buildResult(context, inference, info.packageName, now)
                }.getOrNull()
            }
        }
    }

    /** Scans a single package (used on install / replace). Null if not scannable. */
    fun scanSingleApp(context: Context, packageName: String): AppScanResult? {
        val engine = runCatching { InferenceEngine.create(context) }.getOrElse {
            Log.e(TAG, "Model unavailable — aborting single scan", it)
            return null
        }
        return engine.use { inference ->
            runCatching {
                buildResult(context, inference, packageName, System.currentTimeMillis())
            }.getOrNull()
        }
    }

    private fun buildResult(
        context: Context,
        inference: InferenceEngine,
        packageName: String,
        scannedAt: Long,
    ): AppScanResult {
        val pm = context.packageManager
        val info = PermissionExtractor.getDeclaredPermissionsInfo(pm, packageName)
        val declared = PermissionExtractor.getDeclaredPermissions(info)
        val vector = PermissionExtractor.toFeatureVector(declared)
        val probability = inference.predictMaliciousProbability(vector)

        val label = info.applicationInfo
            ?.let { pm.getApplicationLabel(it).toString() }
            ?: packageName

        return AppScanResult(
            packageName = packageName,
            appLabel = label,
            versionName = info.versionName,
            maliciousProbability = probability,
            declaredPermissions = declared,
            modelPermissions = PermissionExtractor.modelRelevantPermissions(declared),
            scannedAt = scannedAt,
        )
    }
}
