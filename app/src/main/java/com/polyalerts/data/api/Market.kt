package com.polyalerts.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A market as returned by gamma-api.polymarket.com/markets.
 * `outcomes` and `outcomePrices` arrive as JSON *strings* containing a JSON array,
 * e.g. outcomes = "[\"Yes\", \"No\"]", outcomePrices = "[\"0.52\", \"0.48\"]".
 * Use [outcomeList] / [priceList] to get them parsed.
 */
/** Response shape of gamma-api `/public-search`: events, each holding nested markets. */
@Serializable
data class SearchResponse(val events: List<SearchEvent> = emptyList())

@Serializable
data class SearchEvent(
    val closed: Boolean = false,
    val markets: List<Market> = emptyList(),
)

@Serializable
data class Market(
    val id: String,
    val slug: String? = null,
    val question: String? = null,
    val outcomes: String? = null,
    val outcomePrices: String? = null,
    val lastTradePrice: Double? = null,
    val bestBid: Double? = null,
    val bestAsk: Double? = null,
    val volume: String? = null,
    val active: Boolean = false,
    val closed: Boolean = false,
    @SerialName("endDate") val endDate: String? = null,
    val image: String? = null,
) {
    fun outcomeList(): List<String> = parseStringArray(outcomes)
    fun priceList(): List<Double> = parseStringArray(outcomePrices).mapNotNull { it.toDoubleOrNull() }

    /** Total traded volume as a number (0 for untraded placeholder markets). */
    fun volumeValue(): Double = volume?.toDoubleOrNull() ?: 0.0

    /** Current price (0..1) for the given outcome index, or null. */
    fun priceFor(outcomeIndex: Int): Double? = priceList().getOrNull(outcomeIndex)

    val webUrl: String
        get() = "https://polymarket.com/event/${slug ?: ""}"

    companion object {
        private val lax = Json { ignoreUnknownKeys = true; isLenient = true }
        private fun parseStringArray(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching { lax.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
        }
    }
}
