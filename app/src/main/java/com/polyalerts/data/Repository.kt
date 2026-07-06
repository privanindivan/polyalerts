package com.polyalerts.data

import android.content.Context
import com.polyalerts.data.api.Market
import com.polyalerts.data.api.Network
import com.polyalerts.data.db.AlertDao
import com.polyalerts.data.db.AlertDb
import com.polyalerts.data.db.AlertRule
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Single entry point for data: public Gamma API + local alert rules. */
class Repository(context: Context) {

    private val api = Network.gamma
    private val dao: AlertDao = AlertDb.get(context).alertDao()

    // --- Markets (remote) ---
    suspend fun browse(limit: Int = 50, offset: Int = 0, tagId: Int? = null): List<Market> =
        api.markets(limit = limit, offset = offset, tagId = tagId)

    suspend fun market(id: String): Market = api.market(id)

    /** Server-side search: flatten the events' nested markets, drop closed, de-dupe. */
    suspend fun search(query: String): List<Market> =
        api.search(query).events
            .flatMap { it.markets }
            .filter { !it.closed }
            .distinctBy { it.id }

    // --- Backup / transfer (local file, no network) ---
    private val backupJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** Serialize all rules to a JSON string for the user to save/share. */
    suspend fun exportRulesJson(): String =
        backupJson.encodeToString(AlertBackup(rules = dao.allRules().map { it.toDto() }))

    /** Merge rules from a JSON backup, skipping ones that already exist. Returns how many were added. */
    suspend fun importRulesJson(text: String): Int {
        val backup = backupJson.decodeFromString<AlertBackup>(text)
        val seen = dao.allRules().map { it.signature() }.toHashSet()
        var added = 0
        for (dto in backup.rules) {
            val entity = dto.toEntity()
            if (seen.add(entity.signature())) { dao.upsert(entity); added++ }
        }
        return added
    }

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
