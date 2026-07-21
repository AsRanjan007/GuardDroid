package com.guarddroid.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single source of truth for scan results, shared by the background worker,
 * the install [com.guarddroid.app.receiver.PackageInstallReceiver] and the UI.
 *
 * Results are held in a [StateFlow] for reactive Compose consumption and are
 * persisted to [android.content.SharedPreferences] as JSON so the dashboard
 * survives process death.
 */
object ScanRepository {

    private const val PREFS = "guarddroid_scan_store"
    private const val KEY_RESULTS = "results"
    private const val KEY_LAST_SCAN = "last_scan"

    private val _results = MutableStateFlow<List<AppScanResult>>(emptyList())
    val results: StateFlow<List<AppScanResult>> = _results.asStateFlow()

    private val _lastScanTime = MutableStateFlow(0L)
    val lastScanTime: StateFlow<Long> = _lastScanTime.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun setScanning(scanning: Boolean) {
        _isScanning.value = scanning
    }

    /** Loads any previously-persisted results into memory (call once at startup). */
    @Synchronized
    fun load(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _lastScanTime.value = prefs.getLong(KEY_LAST_SCAN, 0L)
        val raw = prefs.getString(KEY_RESULTS, null) ?: return
        _results.value = runCatching { deserialize(raw) }.getOrDefault(emptyList())
    }

    /** Replaces the full result set (used after a complete scan). */
    @Synchronized
    fun replaceAll(context: Context, results: List<AppScanResult>) {
        val sorted = results.sortedByDescending { it.maliciousProbability }
        _results.value = sorted
        _lastScanTime.value = System.currentTimeMillis()
        persist(context)
    }

    /** Inserts or updates a single app's result (used on install/replace events). */
    @Synchronized
    fun upsert(context: Context, result: AppScanResult) {
        val updated = _results.value.filterNot { it.packageName == result.packageName } + result
        _results.value = updated.sortedByDescending { it.maliciousProbability }
        _lastScanTime.value = System.currentTimeMillis()
        persist(context)
    }

    /** Removes a package's result (used on uninstall events). */
    @Synchronized
    fun remove(context: Context, packageName: String) {
        _results.value = _results.value.filterNot { it.packageName == packageName }
        persist(context)
    }

    private fun persist(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_RESULTS, serialize(_results.value))
            .putLong(KEY_LAST_SCAN, _lastScanTime.value)
            .apply()
    }

    private fun serialize(results: List<AppScanResult>): String {
        val array = JSONArray()
        for (r in results) {
            array.put(
                JSONObject()
                    .put("packageName", r.packageName)
                    .put("appLabel", r.appLabel)
                    .put("versionName", r.versionName ?: JSONObject.NULL)
                    .put("maliciousProbability", r.maliciousProbability.toDouble())
                    .put("declaredPermissions", JSONArray(r.declaredPermissions))
                    .put("modelPermissions", JSONArray(r.modelPermissions))
                    .put("scannedAt", r.scannedAt)
            )
        }
        return array.toString()
    }

    private fun deserialize(raw: String): List<AppScanResult> {
        val array = JSONArray(raw)
        val out = ArrayList<AppScanResult>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            out.add(
                AppScanResult(
                    packageName = o.getString("packageName"),
                    appLabel = o.getString("appLabel"),
                    versionName = o.optString("versionName").takeIf { it.isNotEmpty() && it != "null" },
                    maliciousProbability = o.getDouble("maliciousProbability").toFloat(),
                    declaredPermissions = o.getJSONArray("declaredPermissions").toStringList(),
                    modelPermissions = o.getJSONArray("modelPermissions").toStringList(),
                    scannedAt = o.getLong("scannedAt"),
                )
            )
        }
        return out
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }
}
