package com.guarddroid.app.ml

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Bridges the Android [PackageManager] and the NaticusDroid model.
 *
 * Responsibilities:
 *  1. Enumerate user-installed (non-system) third-party apps.
 *  2. Read the permissions each app declares (`GET_PERMISSIONS`).
 *  3. Map those permission strings onto the fixed `[1, 86]` binary feature
 *     vector the TFLite model expects, using [PermissionSchema].
 */
object PermissionExtractor {

    /**
     * Returns every third-party (user-installed) app, filtering out system
     * apps (`FLAG_SYSTEM`) and system apps that were merely updated
     * (`FLAG_UPDATED_SYSTEM_APP`). The host app itself is excluded.
     */
    fun getUserInstalledPackages(context: Context): List<PackageInfo> {
        val pm = context.packageManager
        return getInstalledPackages(pm)
            .filter { info ->
                val app = info.applicationInfo ?: return@filter false
                !app.isSystemApp() && info.packageName != context.packageName
            }
            .sortedBy { it.applicationInfo?.let { a -> pm.getApplicationLabel(a).toString() } ?: it.packageName }
    }

    /** Reads the permissions declared by [packageName]; empty if none/uninstalled. */
    fun getDeclaredPermissions(context: Context, packageName: String): List<String> {
        return try {
            val info = getPackageInfo(context.packageManager, packageName)
            info.requestedPermissions?.toList() ?: emptyList()
        } catch (e: PackageManager.NameNotFoundException) {
            emptyList()
        }
    }

    /** Loads the [PackageInfo] (with permissions populated) for a package. */
    fun getDeclaredPermissionsInfo(pm: PackageManager, packageName: String): PackageInfo =
        getPackageInfo(pm, packageName)

    /** Convenience: declared permissions straight from an already-loaded [PackageInfo]. */
    fun getDeclaredPermissions(info: PackageInfo): List<String> =
        info.requestedPermissions?.toList() ?: emptyList()

    /**
     * Builds the `[86]` binary feature vector: index i is 1.0f when the app
     * declares [PermissionSchema.PERMISSIONS]\[i], else 0.0f.
     */
    fun toFeatureVector(declaredPermissions: Collection<String>): FloatArray {
        val vector = FloatArray(PermissionSchema.NUM_FEATURES)
        val declared = declaredPermissions.toHashSet()
        for ((index, permission) in PermissionSchema.PERMISSIONS.withIndex()) {
            if (permission in declared) vector[index] = 1.0f
        }
        return vector
    }

    /** The subset of an app's permissions that are actually model features. */
    fun modelRelevantPermissions(declaredPermissions: Collection<String>): List<String> {
        val declared = declaredPermissions.toHashSet()
        return PermissionSchema.PERMISSIONS.filter { it in declared }
    }

    private fun ApplicationInfo.isSystemApp(): Boolean {
        val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        return (flags and systemFlags) != 0
    }

    @Suppress("DEPRECATION")
    private fun getInstalledPackages(pm: PackageManager): List<PackageInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(pm: PackageManager, packageName: String): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }
    }
}
