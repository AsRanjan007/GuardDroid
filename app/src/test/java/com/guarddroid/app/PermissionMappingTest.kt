package com.guarddroid.app

import com.guarddroid.app.data.RiskLevel
import com.guarddroid.app.ml.PermissionExtractor
import com.guarddroid.app.ml.PermissionSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for the permission -> feature-vector mapping and risk buckets. */
class PermissionMappingTest {

    @Test
    fun schemaHasExpectedFeatureCount() {
        assertEquals(86, PermissionSchema.NUM_FEATURES)
        assertEquals(86, PermissionSchema.PERMISSIONS.size)
        assertEquals(86, PermissionSchema.PERMISSIONS.toSet().size) // no duplicates
    }

    @Test
    fun featureVectorMarksDeclaredPermissions() {
        val declared = listOf(
            "android.permission.SEND_SMS",
            "android.permission.INTERNET",
            "com.example.UNKNOWN_PERMISSION", // not a model feature -> ignored
        )
        val vector = PermissionExtractor.toFeatureVector(declared)

        assertEquals(86, vector.size)
        assertEquals(1.0f, vector[PermissionSchema.INDEX.getValue("android.permission.SEND_SMS")])
        assertEquals(1.0f, vector[PermissionSchema.INDEX.getValue("android.permission.INTERNET")])
        // Exactly two features set.
        assertEquals(2, vector.count { it == 1.0f })
    }

    @Test
    fun modelRelevantPermissionsFiltersToSchema() {
        val declared = listOf(
            "android.permission.READ_SMS",
            "com.example.CUSTOM",
        )
        val relevant = PermissionExtractor.modelRelevantPermissions(declared)
        assertEquals(listOf("android.permission.READ_SMS"), relevant)
    }

    @Test
    fun riskLevelBucketsByThreshold() {
        assertEquals(RiskLevel.HIGH, RiskLevel.fromProbability(0.95f))
        assertEquals(RiskLevel.HIGH, RiskLevel.fromProbability(PermissionSchema.RISK_THRESHOLD))
        assertEquals(RiskLevel.MODERATE, RiskLevel.fromProbability(0.5f))
        assertEquals(RiskLevel.LOW, RiskLevel.fromProbability(0.25f))
        assertEquals(RiskLevel.SAFE, RiskLevel.fromProbability(0.05f))
        assertTrue(PermissionSchema.RISK_THRESHOLD > 0.5f)
    }
}
