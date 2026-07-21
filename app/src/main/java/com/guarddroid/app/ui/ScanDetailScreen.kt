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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guarddroid.app.data.AppScanResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDetailScreen(
    packageName: String,
    onBack: () -> Unit,
    viewModel: ScanViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val app = state.apps.firstOrNull { it.packageName == packageName }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app?.appLabel ?: "Scan details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (app == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("This app is no longer available.", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { RiskGaugeCard(app) }
            item { AppInfoCard(app) }
            item {
                SectionHeader(
                    "Model-relevant permissions (${app.modelPermissions.size})",
                    "These NaticusDroid features drove the risk score.",
                )
            }
            if (app.modelPermissions.isEmpty()) {
                item { EmptyPermissionsNote() }
            } else {
                items(app.modelPermissions) { perm ->
                    PermissionRow(perm, emphasised = true)
                }
            }
            item {
                SectionHeader(
                    "All declared permissions (${app.declaredPermissions.size})",
                    "Everything this app requests in its manifest.",
                )
            }
            items(app.declaredPermissions) { perm ->
                PermissionRow(perm, emphasised = false)
            }
        }
    }
}

@Composable
private fun RiskGaugeCard(app: AppScanResult) {
    val color = app.riskLevel.color()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${app.riskScorePercent}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = color,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("malicious", style = MaterialTheme.typography.labelSmall, color = color)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(app.riskLevel.label(), style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                riskExplanation(app),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AppInfoCard(app: AppScanResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoLine("Package", app.packageName)
            InfoLine("Version", app.versionName ?: "—")
            InfoLine("Scanned", formatTimestamp(app.scannedAt))
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.width(84.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun PermissionRow(permission: String, emphasised: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasised) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(prettyPermission(permission), style = MaterialTheme.typography.titleMedium)
                Text(
                    permission,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            if (emphasised) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyPermissionsNote() {
    Text(
        "This app declares none of the model's tracked permissions — a strong benign signal.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

private fun riskExplanation(app: AppScanResult): String = when (app.riskLevel) {
    com.guarddroid.app.data.RiskLevel.HIGH ->
        "The permission profile strongly matches known malware. Review and consider uninstalling."

    com.guarddroid.app.data.RiskLevel.MODERATE ->
        "Some requested permissions are common in malware. Keep an eye on this app."

    com.guarddroid.app.data.RiskLevel.LOW ->
        "A few sensitive permissions are requested, but overall risk is low."

    com.guarddroid.app.data.RiskLevel.SAFE ->
        "The permission profile looks typical of legitimate apps."
}
