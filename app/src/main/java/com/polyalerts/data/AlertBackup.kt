package com.polyalerts.data

import com.polyalerts.data.db.AlertKind
import com.polyalerts.data.db.AlertRule
import com.polyalerts.data.db.Comparator
import kotlinx.serialization.Serializable

/**
 * Versioned, on-device JSON backup of the user's alert rules.
 * Pure local file — no network, no account, no backend. `version` lets us
 * evolve the format later without breaking old exports.
 */
@Serializable
data class AlertBackup(
    val version: Int = 1,
    val rules: List<AlertDto> = emptyList(),
)

/** Transport shape of one rule. Decoupled from the Room entity so the export format is stable. */
@Serializable
data class AlertDto(
    val marketId: String,
    val slug: String,
    val question: String,
    val imageUrl: String? = null,
    val outcomeIndex: Int,
    val outcomeLabel: String,
    val kind: String = "THRESHOLD",
    val comparator: String = "ABOVE",
    val target: Double,
    val baselinePrice: Double? = null,
)

fun AlertRule.toDto() = AlertDto(
    marketId = marketId,
    slug = slug,
    question = question,
    imageUrl = imageUrl,
    outcomeIndex = outcomeIndex,
    outcomeLabel = outcomeLabel,
    kind = kind.name,
    comparator = comparator.name,
    target = target,
    baselinePrice = baselinePrice,
)

fun AlertDto.toEntity() = AlertRule(
    id = 0,                       // 0 = let Room assign a fresh id on insert
    marketId = marketId,
    slug = slug,
    question = question,
    imageUrl = imageUrl,
    outcomeIndex = outcomeIndex,
    outcomeLabel = outcomeLabel,
    kind = runCatching { AlertKind.valueOf(kind) }.getOrDefault(AlertKind.THRESHOLD),
    comparator = runCatching { Comparator.valueOf(comparator) }.getOrDefault(Comparator.ABOVE),
    target = target,
    baselinePrice = baselinePrice,
    enabled = true,
    lastTriggeredAt = null,       // a restored alert starts fresh, not "already fired"
)

/** Stable identity used to skip re-importing a rule that already exists. */
fun AlertRule.signature(): String =
    "$marketId|$outcomeIndex|${kind.name}|${comparator.name}|$target"
