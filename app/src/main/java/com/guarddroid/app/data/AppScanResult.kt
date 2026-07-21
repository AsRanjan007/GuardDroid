package com.guarddroid.app.data

import com.guarddroid.app.ml.PermissionSchema

/** Bucketed risk classification derived from the model's malicious probability. */
enum class RiskLevel {
    SAFE, LOW, MODERATE, HIGH;

    companion object {
        fun fromProbability(p: Float): RiskLevel = when {
            p >= PermissionSchema.RISK_THRESHOLD -> HIGH        // > 70% malicious
            p >= 0.40f -> MODERATE
            p >= 0.20f -> LOW
            else -> SAFE
        }
    }
}

/**
 * The outcome of scanning a single installed app.
 *
 * @param maliciousProbability model output in `[0, 1]`.
 * @param riskScorePercent convenience integer percentage for the UI.
 * @param declaredPermissions every permission the app requests.
 * @param modelPermissions the subset that are NaticusDroid model features
 *        (i.e. the ones that actually drove the score).
 */
data class AppScanResult(
    val packageName: String,
    val appLabel: String,
    val versionName: String?,
    val maliciousProbability: Float,
    val declaredPermissions: List<String>,
    val modelPermissions: List<String>,
    val scannedAt: Long,
) {
    val riskLevel: RiskLevel get() = RiskLevel.fromProbability(maliciousProbability)
    val riskScorePercent: Int get() = (maliciousProbability * 100).toInt()
    val isHighRisk: Boolean get() = riskLevel == RiskLevel.HIGH
}
