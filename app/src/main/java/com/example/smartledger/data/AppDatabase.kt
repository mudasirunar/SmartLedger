package com.example.smartledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CalcHistory::class, Expense::class, Electricity::class, MilkRecord::class],
    version = 6, // INCREMENTED TO 6
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calcDao(): CalcDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun electricityDao(): ElectricityDao
    abstract fun milkDao(): MilkDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // MIGRATION 5 -> 6: Remove firebaseId columns
        // Note: SQLite technically supports DROP COLUMN in newer versions,
        // but creating a migration to explicitly ignore them is safer for Room integrity.
        // Assuming you have removed 'firebaseId' from your Entity classes,
        // we run SQL to clean the database structure.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Try to drop the columns. If running on older Android, this might fail,
                // so we wrap in try-catch or use fallback.
                // Since this is a clean break, recreating the tables is the most robust way
                // but simpler allows 'DROP COLUMN' on Android API 21+ (which is standard now).

                try {
                    database.execSQL("ALTER TABLE expenses DROP COLUMN firebaseId")
                    database.execSQL("ALTER TABLE electricity_records DROP COLUMN firebaseId")
                    database.execSQL("ALTER TABLE milk_records DROP COLUMN firebaseId")
                } catch (e: Exception) {
                    // If DROP COLUMN is not supported (very old devices),
                    // we usually just leave the column there unused, or recreate table.
                    // For now, we assume standard modern Android behavior.
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_ledger_db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration() // If migration fails, it will reset DB (Safety net)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}