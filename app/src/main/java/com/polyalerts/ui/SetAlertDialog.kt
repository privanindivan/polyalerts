package com.polyalerts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.polyalerts.data.api.Market
import com.polyalerts.data.db.AlertKind
import com.polyalerts.data.db.AlertRule
import com.polyalerts.data.db.Comparator

@Composable
fun SetAlertDialog(
    market: Market,
    initialOutcome: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (AlertRule) -> Unit,
) {
    val outcomes = market.outcomeList().ifEmpty { listOf("Yes", "No") }
    var outcomeIndex by remember { mutableIntStateOf(initialOutcome.coerceIn(0, (outcomes.size - 1).coerceAtLeast(0))) }
    var kind by remember { mutableStateOf(AlertKind.THRESHOLD) }
    var comparator by remember { mutableStateOf(Comparator.ABOVE) }
    val startCents = market.priceFor(outcomeIndex)?.let { (it * 100).toInt().coerceIn(1, 99) } ?: 50
    var centsText by remember { mutableStateOf(startCents.toString()) }
    var moveText by remember { mutableStateOf("10") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alert me when…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(market.question ?: market.id)

                Text("Outcome")
                outcomes.forEachIndexed { i, label ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.selectable(
                            selected = outcomeIndex == i, onClick = { outcomeIndex = i })) {
                        RadioButton(selected = outcomeIndex == i, onClick = { outcomeIndex = i })
                        Text(label)
                    }
                }

                // Which kind of alert.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == AlertKind.THRESHOLD,
                        onClick = { kind = AlertKind.THRESHOLD }, label = { Text("Reaches price") })
                    FilterChip(selected = kind == AlertKind.MOVEMENT,
                        onClick = { kind = AlertKind.MOVEMENT }, label = { Text("Moves by") })
                }

                if (kind == AlertKind.THRESHOLD) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = comparator == Comparator.ABOVE,
                            onClick = { comparator = Comparator.ABOVE }, label = { Text("Rises to ≥") })
                        FilterChip(selected = comparator == Comparator.BELOW,
                            onClick = { comparator = Comparator.BELOW }, label = { Text("Falls to ≤") })
                    }
                    OutlinedTextField(
                        value = centsText,
                        onValueChange = { centsText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Probability % (1–99)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                } else {
                    OutlinedTextField(
                        value = moveText,
                        onValueChange = { moveText = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Move size in % (± either way)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text("Notifies whenever the odds move this many percentage points up or down from now.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val outcomeLabel = outcomes.getOrElse(outcomeIndex) { "Outcome" }
                val rule = if (kind == AlertKind.THRESHOLD) {
                    val cents = centsText.toIntOrNull()?.coerceIn(1, 99) ?: 50
                    AlertRule(
                        marketId = market.id,
                        slug = market.slug ?: "",
                        question = market.question ?: market.id,
                        imageUrl = market.image,
                        outcomeIndex = outcomeIndex,
                        outcomeLabel = outcomeLabel,
                        kind = AlertKind.THRESHOLD,
                        comparator = comparator,
                        target = cents / 100.0,
                    )
                } else {
                    val moveCents = moveText.toIntOrNull()?.coerceIn(1, 99) ?: 10
                    AlertRule(
                        marketId = market.id,
                        slug = market.slug ?: "",
                        question = market.question ?: market.id,
                        imageUrl = market.image,
                        outcomeIndex = outcomeIndex,
                        outcomeLabel = outcomeLabel,
                        kind = AlertKind.MOVEMENT,
                        comparator = Comparator.ABOVE, // unused for movement
                        target = moveCents / 100.0,
                        baselinePrice = market.priceFor(outcomeIndex),
                    )
                }
                onConfirm(rule)
            }) { Text("Save alert") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
