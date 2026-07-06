package com.polyalerts.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter fun toComparator(v: String): Comparator = Comparator.valueOf(v)
    @TypeConverter fun fromComparator(c: Comparator): String = c.name
    @TypeConverter fun toKind(v: String): AlertKind = AlertKind.valueOf(v)
    @TypeConverter fun fromKind(k: AlertKind): String = k.name
}

@Database(entities = [AlertRule::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AlertDb : RoomDatabase() {
    abstract fun alertDao(): AlertDao

    companion object {
        // v2 -> v3: add the market thumbnail column without wiping existing alerts.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alert_rules ADD COLUMN imageUrl TEXT")
            }
        }

        @Volatile private var instance: AlertDb? = null

        fun get(context: Context): AlertDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AlertDb::class.java,
                    "polyalerts.db"
                ).addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
