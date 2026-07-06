package com.polyalerts.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {

    @Query("SELECT * FROM alert_rules ORDER BY id DESC")
    fun observeAll(): Flow<List<AlertRule>>

    /** One-shot snapshot of every rule — used by JSON export. */
    @Query("SELECT * FROM alert_rules ORDER BY id DESC")
    suspend fun allRules(): List<AlertRule>

    @Query("SELECT * FROM alert_rules WHERE enabled = 1")
    suspend fun enabledRules(): List<AlertRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AlertRule): Long

    @Update
    suspend fun update(rule: AlertRule)

    @Delete
    suspend fun delete(rule: AlertRule)

    @Query("UPDATE alert_rules SET lastTriggeredAt = :ts WHERE id = :id")
    suspend fun markTriggered(id: Long, ts: Long)
}
