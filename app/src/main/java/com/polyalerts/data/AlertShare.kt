package com.polyalerts.data

import android.util.Base64
import com.polyalerts.data.db.AlertRule
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Compact wire form of one alert for offline QR transfer between devices.
 * Short keys keep the QR small; the market's question and image are re-fetched
 * by [m] (marketId) on the receiving device, so they don't bloat the code.
 */
@Serializable
data class ShareRule(
    val m: String,          // marketId
    val s: String,          // slug (kept so the "open on Polymarket" link works immediately)
    val o: Int,             // outcomeIndex
    val l: String,          // outcomeLabel
    val k: String,          // kind (THRESHOLD / MOVEMENT)
    val c: String,          // comparator (ABOVE / BELOW)
    val t: Double,          // target
    val b: Double? = null,  // baselinePrice (movement only)
)

@Serializable
data class SharePayload(val v: Int = 1, val a: List<ShareRule>)

/** Marks the payload as a PolyAlerts v1 share code, so a random QR isn't mistaken for one. */
private const val PREFIX = "PAQ1:"
private val shareJson = Json { encodeDefaults = false; ignoreUnknownKeys = true }

fun AlertRule.toShareRule() = ShareRule(
    m = marketId, s = slug, o = outcomeIndex, l = outcomeLabel,
    k = kind.name, c = comparator.name, t = target, b = baselinePrice,
)

/** Encode selected alerts into a compact, prefixed string suitable for a QR code. */
fun encodeShare(rules: List<AlertRule>): String {
    val json = shareJson.encodeToString(SharePayload(a = rules.map { it.toShareRule() }))
    val gz = ByteArrayOutputStream().also { bos ->
        GZIPOutputStream(bos).use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }.toByteArray()
    return PREFIX + Base64.encodeToString(gz, Base64.NO_WRAP or Base64.URL_SAFE)
}

/** Decode a scanned string back into share rules, or null if it isn't a PolyAlerts code. */
fun decodeShare(text: String): List<ShareRule>? {
    if (!text.startsWith(PREFIX)) return null
    return runCatching {
        val gz = Base64.decode(text.removePrefix(PREFIX), Base64.NO_WRAP or Base64.URL_SAFE)
        val json = GZIPInputStream(ByteArrayInputStream(gz)).use { it.readBytes() }.toString(Charsets.UTF_8)
        shareJson.decodeFromString<SharePayload>(json).a
    }.getOrNull()
}
