package com.polyalerts.alerts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.polyalerts.data.Repository
import com.polyalerts.data.db.AlertKind
import com.polyalerts.data.db.Comparator
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Runs periodically. For each enabled rule it fetches the market's current price
 * and, if the price has crossed the user's target, posts a notification.
 *
 * A rule fires at most once per crossing: after firing it records lastTriggeredAt
 * and won't fire again until the price moves back to the non-crossed side.
 */
class AlertWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = Repository.get(applicationContext)
        val rules = runCatching { repo.enabledRules() }.getOrElse { return Result.retry() }
        if (rules.isEmpty()) return Result.success()

        // Group by market so we hit the API once per market, not once per rule.
        val byMarket = rules.groupBy { it.marketId }

        for ((marketId, marketRules) in byMarket) {
            val market = runCatching { repo.market(marketId) }.getOrNull() ?: continue
            val prices = market.priceList()

            for (rule in marketRules) {
                val price = prices.getOrNull(rule.outcomeIndex) ?: continue
                when (rule.kind) {
                    AlertKind.THRESHOLD -> handleThreshold(repo, rule, market, price)
                    AlertKind.MOVEMENT -> handleMovement(repo, rule, market, price)
                }
            }
        }
        return Result.success()
    }

    /** Fixed-price crossing: fire once when crossed, re-arm when price moves back. */
    private suspend fun handleThreshold(
        repo: Repository,
        rule: com.polyalerts.data.db.AlertRule,
        market: com.polyalerts.data.api.Market,
        price: Double,
    ) {
        val crossed = when (rule.comparator) {
            Comparator.ABOVE -> price >= rule.target
            Comparator.BELOW -> price <= rule.target
        }
        if (crossed && rule.lastTriggeredAt == null) {
            val pct = (price * 100).toInt()
            val targetPct = (rule.target * 100).toInt()
            val arrow = if (rule.comparator == Comparator.ABOVE) "≥" else "≤"
            Notifications.show(
                context = applicationContext,
                notifId = rule.id.toInt(),
                title = market.question ?: rule.question,
                body = "${rule.outcomeLabel} is at $pct% ($arrow your $targetPct% target). Tap to trade on Polymarket.",
                webUrl = market.webUrl,
            )
            repo.markTriggered(rule.id, System.currentTimeMillis())
        } else if (!crossed && rule.lastTriggeredAt != null) {
            repo.updateRule(rule.copy(lastTriggeredAt = null))
        }
    }

    /**
     * Movement: fire when the price has moved at least [rule.target] points away from the
     * stored baseline. After firing, reset the baseline to the current price so the next
     * equal-sized move re-arms automatically. First run just records the baseline.
     */
    private suspend fun handleMovement(
        repo: Repository,
        rule: com.polyalerts.data.db.AlertRule,
        market: com.polyalerts.data.api.Market,
        price: Double,
    ) {
        val baseline = rule.baselinePrice
        if (baseline == null) {
            repo.updateRule(rule.copy(baselinePrice = price))
            return
        }
        val delta = price - baseline
        if (abs(delta) >= rule.target) {
            val movePts = (abs(delta) * 100).roundToInt()
            val fromC = (baseline * 100).roundToInt()
            val toC = (price * 100).roundToInt()
            val dir = if (delta > 0) "up" else "down"
            Notifications.show(
                context = applicationContext,
                notifId = rule.id.toInt(),
                title = market.question ?: rule.question,
                body = "${rule.outcomeLabel} moved $dir $movePts% (from $fromC% to $toC%). Tap to trade on Polymarket.",
                webUrl = market.webUrl,
            )
            // Re-baseline to current and record the fire time.
            repo.updateRule(rule.copy(baselinePrice = price, lastTriggeredAt = System.currentTimeMillis()))
        }
    }
}
