package com.polyalerts.data

import android.content.Context
import com.polyalerts.data.api.Market
import com.polyalerts.data.api.Network
import com.polyalerts.data.db.AlertDao
import com.polyalerts.data.db.AlertDb
import com.polyalerts.data.db.AlertRule

/** Single entry point for data: public Gamma API + local alert rules. */
class Repository(context: Context) {

    private val api = Network.gamma
    private val dao: AlertDao = AlertDb.get(context).alertDao()

    // --- Markets (remote) ---
    suspend fun browse(limit: Int = 50, offset: Int = 0, tagId: Int? = null): List<Market> =
        api.markets(limit = limit, offset = offset, tagId = tagId)

    suspend fun market(id: String): Market = api.market(id)

    /**
     * Server-side search: flatten the events' nested markets, drop closed and untraded
     * placeholder markets (e.g. "Will Party O win…" with $0 volume), de-dupe, and sort
     * real markets to the top by traded volume.
     */
    suspend fun search(query: String): List<Market> =
        api.search(query).events
            .filter { !it.closed }
            .flatMap { it.markets }
            .filter { !it.closed && it.volumeValue() > 0.0 }
            .distinctBy { it.id }
            .sortedByDescending { it.volumeValue() }

    // --- Alert rules (local) ---
    fun observeRules() = dao.observeAll()
    suspend fun enabledRules() = dao.enabledRules()
    suspend fun saveRule(rule: AlertRule) = dao.upsert(rule)
    suspend fun updateRule(rule: AlertRule) = dao.update(rule)
    suspend fun deleteRule(rule: AlertRule) = dao.delete(rule)
    suspend fun markTriggered(id: Long, ts: Long) = dao.markTriggered(id, ts)

    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }
    }
}
