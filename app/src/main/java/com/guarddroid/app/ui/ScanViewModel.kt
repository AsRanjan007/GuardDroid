package com.guarddroid.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guarddroid.app.data.AppScanResult
import com.guarddroid.app.data.RiskLevel
import com.guarddroid.app.data.ScanRepository
import com.guarddroid.app.worker.AppScanWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Overall device posture shown on the dashboard header. */
enum class SecurityStatus { SAFE, AT_RISK, UNKNOWN }

data class DashboardUiState(
    val apps: List<AppScanResult> = emptyList(),
    val isScanning: Boolean = false,
    val lastScanTime: Long = 0L,
) {
    val highRiskCount: Int get() = apps.count { it.riskLevel == RiskLevel.HIGH }
    val moderateCount: Int get() = apps.count { it.riskLevel == RiskLevel.MODERATE }
    val scannedCount: Int get() = apps.size

    val status: SecurityStatus
        get() = when {
            apps.isEmpty() -> SecurityStatus.UNKNOWN
            highRiskCount > 0 -> SecurityStatus.AT_RISK
            else -> SecurityStatus.SAFE
        }
}

/**
 * Exposes scan state to Compose and forwards user actions (Scan Now) to the
 * [AppScanWorker]. All data flows from [ScanRepository] so the UI stays in sync
 * with background scans and install-time scans.
 */
class ScanViewModel(app: Application) : AndroidViewModel(app) {

    val uiState: StateFlow<DashboardUiState> =
        combine(
            ScanRepository.results,
            ScanRepository.isScanning,
            ScanRepository.lastScanTime,
        ) { apps, scanning, lastScan ->
            DashboardUiState(apps = apps, isScanning = scanning, lastScanTime = lastScan)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(),
        )

    fun scanNow() {
        AppScanWorker.enqueueFullScan(getApplication())
    }

    fun appByPackage(packageName: String): AppScanResult? =
        uiState.value.apps.firstOrNull { it.packageName == packageName }
}
