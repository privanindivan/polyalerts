package com.polyalerts.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.polyalerts.data.api.Market
import com.polyalerts.data.db.AlertKind
import com.polyalerts.data.db.AlertRule
import com.polyalerts.data.db.Comparator
import com.polyalerts.data.decodeShare
import com.polyalerts.data.encodeShare
import com.polyalerts.ui.theme.NoRed
import com.polyalerts.ui.theme.SurfaceElevated
import com.polyalerts.ui.theme.TextMuted
import com.polyalerts.ui.theme.TextSecondary
import com.polyalerts.ui.theme.YesGreen

// A generous cap so the QR stays comfortably scannable phone-to-phone.
private const val MAX_QR_CHARS = 2000

@Composable
fun WatchlistScreen(vm: AppViewModel) {
    val rules by vm.rules.collectAsStateSafe()
    val liveMarkets by vm.alertMarkets.collectAsStateSafe()
    val incoming by vm.incoming.collectAsStateSafe()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<AlertRule?>(null) }
    var showSend by remember { mutableStateOf(false) }
    var qrText by remember { mutableStateOf<String?>(null) }

    val toast: (String) -> Unit = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }

    // Keep the live probabilities fresh while this tab is open (and re-fetch when alerts change).
    LaunchedEffect(rules.size) {
        vm.refreshAlertPrices()
        while (true) {
            delay(20_000L)
            vm.refreshAlertPrices()
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents != null) {
            val shared = decodeShare(contents)
            if (shared.isNullOrEmpty()) toast("That isn't a PolyAlerts code") else vm.prepareIncoming(shared)
        }
    }
    fun startScan() = scanLauncher.launch(
        ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Point at a PolyAlerts QR code")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
            .setCaptureActivity(PortraitCaptureActivity::class.java),
    )

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Your alerts",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { startScan() }) {
                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Receive")
            }
            if (rules.isNotEmpty()) {
                TextButton(onClick = { showSend = true }) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Send")
                }
            }
        }

        Text(
            "Copy alerts between phones with a QR code: on the phone with the alerts tap Send, then tap Receive on the other phone to scan the code.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(top = 2.dp),
        )

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
                    liveMarket = liveMarkets[rule.marketId],
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
                TextButton(onClick = { vm.deleteAlert(rule); pendingDelete = null }) {
                    Text("Delete", color = NoRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (showSend) {
        SendPickerDialog(
            rules = rules,
            onDismiss = { showSend = false },
            onShowQr = { selectedRules ->
                val text = encodeShare(selectedRules)
                if (text.length > MAX_QR_CHARS) {
                    toast("Too many alerts for one QR — pick fewer")
                } else {
                    qrText = text
                    showSend = false
                }
            },
        )
    }

    qrText?.let { text -> QrDialog(text = text, onDismiss = { qrText = null }) }

    incoming?.let { list ->
        IncomingPreviewDialog(
            incoming = list,
            onCancel = { vm.cancelIncoming() },
            onAdd = {
                vm.confirmIncoming { n ->
                    toast(if (n > 0) "Added $n alert(s)" else "You already have these")
                }
            },
        )
    }
}

@Composable
private fun SendPickerDialog(
    rules: List<AlertRule>,
    onDismiss: () -> Unit,
    onShowQr: (List<AlertRule>) -> Unit,
) {
    // Everything selected by default; the sender unticks what they don't want to share.
    val selected = remember { mutableStateListOf<Long>().apply { addAll(rules.map { it.id }) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send alerts") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Pick which alerts to put in the QR code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.weight(1f),
                    )
                    val allSelected = selected.size == rules.size
                    TextButton(onClick = {
                        selected.clear()
                        if (!allSelected) selected.addAll(rules.map { it.id })
                    }) { Text(if (allSelected) "Deselect all" else "Select all") }
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(rules, key = { it.id }) { rule ->
                        val checked = rule.id in selected
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { if (checked) selected.remove(rule.id) else selected.add(rule.id) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { c -> if (c) selected.add(rule.id) else selected.remove(rule.id) },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                rule.question,
                                maxLines = 2,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = { onShowQr(rules.filter { it.id in selected }) },
            ) { Text("Show QR (${selected.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun QrDialog(text: String, onDismiss: () -> Unit) {
    val bitmap = remember(text) {
        runCatching { BarcodeEncoder().encodeBitmap(text, BarcodeFormat.QR_CODE, 900, 900) }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Receive on the other phone") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (bitmap != null) {
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Alerts QR code",
                            modifier = Modifier.size(240.dp),
                        )
                    }
                } else {
                    Text("Couldn’t generate the code.", color = TextMuted)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "On the other phone: Alerts → Receive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun IncomingPreviewDialog(
    incoming: List<AlertRule>,
    onCancel: () -> Unit,
    onAdd: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add ${incoming.size} alert(s)?") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(incoming, key = { "${it.marketId}|${it.outcomeIndex}|${it.kind}|${it.target}" }) { r ->
                    val cents = (r.target * 100).toInt()
                    val desc = when (r.kind) {
                        AlertKind.THRESHOLD -> {
                            val arrow = if (r.comparator == Comparator.ABOVE) "≥" else "≤"
                            "${r.outcomeLabel} $arrow $cents%"
                        }
                        AlertKind.MOVEMENT -> "${r.outcomeLabel} moves ±$cents%"
                    }
                    Column {
                        Text(
                            r.question,
                            maxLines = 2,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onAdd) { Text("Add") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun AlertCard(
    rule: AlertRule,
    liveMarket: Market?,
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
    val livePrice = liveMarket?.priceFor(rule.outcomeIndex)
    val livePct = livePrice?.let { (it * 100).roundToInt() }
    val reached = livePrice != null && rule.kind == AlertKind.THRESHOLD &&
        (if (rule.comparator == Comparator.ABOVE) livePrice >= rule.target else livePrice <= rule.target)
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
            // Live probability, refreshed while the tab is open.
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 6.dp)) {
                Text(
                    livePct?.let { "$it%" } ?: "—",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = if (reached) YesGreen else MaterialTheme.colorScheme.onSurface,
                )
                Text(if (reached) "reached" else "now", color = TextMuted, fontSize = 10.sp)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}
