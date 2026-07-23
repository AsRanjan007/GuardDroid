package com.guarddroid.app.alert

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Full-screen, attention-grabbing malware warning shown when a newly installed
 * app is classified as high-risk — even when GuardDroid is not in the
 * foreground. It is launched by the threat notification's full-screen intent,
 * shows over the lock screen, turns the screen on, plays a looping alarm tone
 * and vibrates until the user acts.
 */
class ThreatAlertActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val label = intent.getStringExtra(EXTRA_LABEL) ?: pkg
        val percent = intent.getIntExtra(EXTRA_PERCENT, 0)
        val criticality = intent.getStringExtra(EXTRA_CRITICALITY) ?: "High risk"
        val version = intent.getStringExtra(EXTRA_VERSION)
        val permCount = intent.getIntExtra(EXTRA_PERM_COUNT, 0)
        val topPerms = intent.getStringArrayExtra(EXTRA_TOP_PERMS)?.toList() ?: emptyList()

        startAlarm()

        setContent {
            com.guarddroid.app.ui.theme.GuardDroidTheme(dynamicColor = false) {
                AlertScreen(
                    label = label,
                    packageName = pkg,
                    version = version,
                    percent = percent,
                    criticality = criticality,
                    permCount = permCount,
                    topPerms = topPerms,
                    onDismiss = { stopAlarm(); finish() },
                    onDetails = {
                        stopAlarm()
                        startActivity(detailIntent(this, pkg))
                        finish()
                    },
                    onUninstall = {
                        stopAlarm()
                        runCatching {
                            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
                        }
                        finish()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
    }

    private fun startAlarm() {
        runCatching {
            val uri: Uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(this, uri).apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        }
        runCatching {
            vibrator = currentVibrator()
            val pattern = longArrayOf(0, 500, 300, 500, 300, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun stopAlarm() {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun currentVibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_VERSION = "extra_version"
        const val EXTRA_PERCENT = "extra_percent"
        const val EXTRA_CRITICALITY = "extra_criticality"
        const val EXTRA_PERM_COUNT = "extra_perm_count"
        const val EXTRA_TOP_PERMS = "extra_top_perms"

        fun buildIntent(
            context: Context,
            packageName: String,
            label: String,
            version: String?,
            percent: Int,
            criticality: String,
            permCount: Int,
            topPerms: List<String>,
        ): Intent = Intent(context, ThreatAlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(EXTRA_PACKAGE, packageName)
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_VERSION, version)
            putExtra(EXTRA_PERCENT, percent)
            putExtra(EXTRA_CRITICALITY, criticality)
            putExtra(EXTRA_PERM_COUNT, permCount)
            putExtra(EXTRA_TOP_PERMS, topPerms.toTypedArray())
        }

        private fun detailIntent(context: Context, packageName: String): Intent =
            Intent(context, com.guarddroid.app.MainActivity::class.java).apply {
                putExtra(com.guarddroid.app.MainActivity.EXTRA_PACKAGE, packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
    }
}

@Composable
private fun AlertScreen(
    label: String,
    packageName: String,
    version: String?,
    percent: Int,
    criticality: String,
    permCount: Int,
    topPerms: List<String>,
    onDismiss: () -> Unit,
    onDetails: () -> Unit,
    onUninstall: () -> Unit,
) {
    val danger = Color(0xFFD32F2F)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A0E0E))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(danger.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = danger, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Malware Warning", color = danger, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "A newly installed app looks dangerous.",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AlertLine("App", label)
            AlertLine("Package", packageName)
            if (!version.isNullOrEmpty()) AlertLine("Version", version)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Prediction", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
                Text("$percent% malicious", color = danger, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Criticality", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
                Text(criticality, color = danger, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            AlertLine("Dangerous perms", permCount.toString())
            if (topPerms.isNotEmpty()) {
                Text(
                    topPerms.joinToString("  •  ") { it.substringAfterLast('.') },
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onUninstall,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = danger),
        ) { Text("Uninstall now", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onDetails, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                Text("View details")
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun AlertLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = Color.White, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}
