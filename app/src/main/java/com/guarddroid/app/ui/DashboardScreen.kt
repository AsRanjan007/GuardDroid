package com.guarddroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guarddroid.app.R
import com.guarddroid.app.data.AppScanResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAppClick: (String) -> Unit,
    viewModel: ScanViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_shield_alert),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("GuardDroid", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SecurityStatusCard(state) }
            item { StatsRow(state) }
            item {
                ScanNowButton(
                    isScanning = state.isScanning,
                    onClick = viewModel::scanNow,
                )
            }
            item {
                Text(
                    text = "Last scan: ${formatTimestamp(state.lastScanTime)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            item {
                Text(
                    text = if (state.apps.isEmpty()) "Scanned apps" else "Scanned apps (${state.scannedCount})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.apps.isEmpty()) {
                item { EmptyState(isScanning = state.isScanning) }
            } else {
                items(state.apps, key = { it.packageName }) { app ->
                    AppRiskRow(app = app, onClick = { onAppClick(app.packageName) })
                }
            }
        }
    }
}

@Composable
private fun SecurityStatusCard(state: DashboardUiState) {
    val (bg, title, subtitle, icon) = when (state.status) {
        SecurityStatus.SAFE -> StatusVisual(
            RiskLevelColors.safe, "You're protected",
            "No high-risk apps detected on this device.", Icons.Default.CheckCircle,
        )

        SecurityStatus.AT_RISK -> StatusVisual(
            RiskLevelColors.high, "Action needed",
            "${state.highRiskCount} app(s) flagged as high risk.", Icons.Default.Warning,
        )

        SecurityStatus.UNKNOWN -> StatusVisual(
            RiskLevelColors.unknown, "Not scanned yet",
            "Run a scan to check your installed apps.", Icons.Default.Warning,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
private fun StatsRow(state: DashboardUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(Modifier.weight(1f), state.scannedCount.toString(), "Scanned", MaterialTheme.colorScheme.primary)
        StatCard(Modifier.weight(1f), state.highRiskCount.toString(), "High risk", RiskLevelColors.high)
        StatCard(Modifier.weight(1f), state.moderateCount.toString(), "Moderate", RiskLevelColors.moderate)
    }
}

@Composable
private fun StatCard(modifier: Modifier, value: String, label: String, accent: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = accent, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ScanNowButton(isScanning: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isScanning,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        if (isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(12.dp))
            Text("Scanning…")
        } else {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Scan Now")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRiskRow(app: AppScanResult, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(app.riskLevel.color().copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${app.riskScorePercent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = app.riskLevel.color(),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.appLabel,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    app.riskLevel.label() + " • ${app.modelPermissions.size} key permissions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = app.riskLevel.color(),
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { app.maliciousProbability },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = app.riskLevel.color(),
                    trackColor = app.riskLevel.color().copy(alpha = 0.15f),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(isScanning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (isScanning) "Scanning your apps…" else "No scan results yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (isScanning) "This only takes a moment." else "Tap “Scan Now” to analyse installed apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

private data class StatusVisual(
    val bg: Color,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private object RiskLevelColors {
    val safe = Color(0xFF2E9E5B)
    val moderate = Color(0xFFF5A623)
    val high = Color(0xFFD32F2F)
    val unknown = Color(0xFF607D8B)
}
