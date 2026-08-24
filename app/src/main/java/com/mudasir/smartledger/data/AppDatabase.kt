package com.mudasir.smartledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CalcHistory::class, Expense::class, Electricity::class, MilkRecord::class, CustomLedger::class, CustomEntry::class, CustomDailyRecord::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calcDao(): CalcDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun electricityDao(): ElectricityDao
    abstract fun milkDao(): MilkDao
    abstract fun customLedgerDao(): CustomLedgerDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // Note: SQLite technically supports DROP COLUMN in newer versions,
        // but creating a migration to explicitly ignore them is safer for Room integrity.
        // Assuming you have removed 'firebaseId' from your Entity classes,
        // we run SQL to clean the database structure.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
            CREATE TABLE IF NOT EXISTS custom_ledgers (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                name TEXT NOT NULL, 
                iconName TEXT NOT NULL, 
                fields TEXT NOT NULL, 
                hasPhotos INTEGER NOT NULL, 
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())

                database.execSQL("""
            CREATE TABLE IF NOT EXISTS custom_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                ledgerId INTEGER NOT NULL, 
                date INTEGER NOT NULL, 
                amount REAL, 
                dataJson TEXT NOT NULL, 
                imagePaths TEXT NOT NULL, 
                isDeleted INTEGER NOT NULL, 
                deletedAt INTEGER
            )
        """.trimIndent())

                database.execSQL("CREATE INDEX IF NOT EXISTS index_custom_entries_ledgerId ON custom_entries (ledgerId)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_ledger_db"
                )
                    .addMigrations(MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}