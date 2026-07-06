package com.polyalerts.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.polyalerts.data.db.AlertKind
import com.polyalerts.data.db.AlertRule
import com.polyalerts.data.db.Comparator
import com.polyalerts.ui.theme.SurfaceElevated
import com.polyalerts.ui.theme.TextSecondary

@Composable
fun WatchlistScreen(vm: AppViewModel) {
    val rules by vm.rules.collectAsStateSafe()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<AlertRule?>(null) }

    val toast: (String) -> Unit = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { vm.exportRules(it, toast) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importRules(it, toast) } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Your alerts",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (rules.isNotEmpty()) {
                TextButton(onClick = { exportLauncher.launch("polyalerts-backup.json") }) { Text("Export") }
            }
            TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }) {
                Text("Import")
            }
        }

        if (rules.isEmpty()) {
            Text(
                "No alerts yet. Open the Markets tab, tap a market, and set an alert.",
                Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            items(rules, key = { it.id }) { rule ->
                AlertCard(
                    rule = rule,
                    onDelete = { pendingDelete = rule },
                    onOpen = { openOnPolymarket(context, "https://polymarket.com/event/${rule.slug}") },
                )
            }
        }
    }

    pendingDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this alert?") },
            text = { Text("You’ll stop getting notifications for “${rule.question}”.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAlert(rule); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AlertCard(
    rule: AlertRule,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    val cents = (rule.target * 100).toInt()
    val desc = when (rule.kind) {
        AlertKind.THRESHOLD -> {
            val arrow = if (rule.comparator == Comparator.ABOVE) "≥" else "≤"
            "Notify when ${rule.outcomeLabel} $arrow $cents%"
        }
        AlertKind.MOVEMENT -> "Notify when ${rule.outcomeLabel} moves ±$cents%"
    }
    Card(Modifier.fillMaxWidth().clickable { onOpen() }) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                if (!rule.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = rule.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Icon(Icons.Default.Notifications, contentDescription = null,
                        tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(rule.question, fontWeight = FontWeight.SemiBold)
                Text(
                    desc + if (rule.lastTriggeredAt != null) "  • fired" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Tap to open ↗", style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}
