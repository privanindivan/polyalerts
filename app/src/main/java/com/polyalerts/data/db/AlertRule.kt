package com.polyalerts.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Direction of the price-threshold comparison. */
enum class Comparator { ABOVE, BELOW }

/**
 * What kind of price alert this is.
 *  - THRESHOLD: fire when the outcome crosses a fixed price ([target]) in [comparator] direction.
 *  - MOVEMENT:  fire when the outcome moves by at least [target] (in price points, 0..1)
 *               away from a moving [baselinePrice]; after firing, the baseline resets to the
 *               current price so the next move re-arms automatically.
 */
enum class AlertKind { THRESHOLD, MOVEMENT }

/**
 * One price alert the user set on a market outcome.
 */
@Entity(tableName = "alert_rules")
data class AlertRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val marketId: String,
    val slug: String,
    val question: String,
    val imageUrl: String? = null,   // market thumbnail, captured when the alert is created
    val outcomeIndex: Int,      // 0 = first outcome (usually "Yes"), 1 = "No"
    val outcomeLabel: String,
    val kind: AlertKind = AlertKind.THRESHOLD,
    val comparator: Comparator, // used by THRESHOLD only
    val target: Double,         // THRESHOLD: crossing price 0..1; MOVEMENT: move size 0..1
    val baselinePrice: Double? = null, // MOVEMENT: reference price the move is measured from
    val enabled: Boolean = true,
    val lastTriggeredAt: Long? = null,  // epoch millis; null = never fired yet
)
