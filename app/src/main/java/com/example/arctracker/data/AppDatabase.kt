package com.example.arctracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Main Room Database for ArcTracker.
 */
@Database(
    entities = [
        Expense::class,
        KnownFinancialAccount::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(KnownFinancialAccountConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun knownFinancialAccountDao(): KnownFinancialAccountDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN relationshipType TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE expenses ADD COLUMN relationshipId TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_relationshipId ON expenses (relationshipId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `known_financial_accounts` (
                        `id` TEXT NOT NULL,
                        `institutionId` TEXT,
                        `institutionName` TEXT,
                        `accountSuffix` TEXT NOT NULL,
                        `instrumentType` TEXT NOT NULL,
                        `confidence` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_financial_accounts_accountSuffix` ON `known_financial_accounts` (`accountSuffix`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_financial_accounts_institutionId` ON `known_financial_accounts` (`institutionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_financial_accounts_instrumentType` ON `known_financial_accounts` (`instrumentType`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN accountId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE expenses ADD COLUMN accountSuffix TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_accountId ON expenses (accountId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_accountSuffix ON expenses (accountSuffix)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "arctracker_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Creates an isolated in-memory database for fast, isolated unit and integration testing.
         */
        fun createInMemoryDatabase(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            )
            .allowMainThreadQueries()
            .build()
        }
    }
}
