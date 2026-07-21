package com.guarddroid.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.guarddroid.app.data.ScanRepository
import com.guarddroid.app.worker.AppScanWorker

/**
 * Listens for package install/replace/remove broadcasts and reacts:
 *
 *  - `ACTION_PACKAGE_ADDED` / `ACTION_PACKAGE_REPLACED` on a **third-party**
 *    app enqueues an immediate single-app scan via [AppScanWorker].
 *  - `ACTION_PACKAGE_REMOVED` (full uninstall) drops the app from the store.
 *
 * System apps and the replace-half of an update (`EXTRA_REPLACING`) are ignored
 * to avoid duplicate work.
 */
class PackageInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return
        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                if (packageName == context.packageName) return
                if (isSystemApp(context, packageName)) {
                    Log.d(TAG, "Ignoring system app $packageName")
                    return
                }
                Log.i(TAG, "Third-party app installed/updated: $packageName — scanning")
                AppScanWorker.enqueueSingleScan(context.applicationContext, packageName)
            }

            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                if (replacing) return // part of an update, not a real uninstall
                Log.i(TAG, "App removed: $packageName")
                ScanRepository.remove(context.applicationContext, packageName)
            }
        }
    }

    private fun isSystemApp(context: Context, packageName: String): Boolean {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            val flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
            (info.flags and flags) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    companion object {
        private const val TAG = "PackageInstallReceiver"
    }
}
