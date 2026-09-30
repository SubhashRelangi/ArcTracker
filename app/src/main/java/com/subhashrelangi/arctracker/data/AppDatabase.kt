package com.subhashrelangi.arctracker.data

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
        KnownFinancialAccount::class,
        TransactionCategory::class,
        UserCategoryRule::class,
        MerchantAlias::class,
        Budget::class,
        BudgetAlertState::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(KnownFinancialAccountConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun knownFinancialAccountDao(): KnownFinancialAccountDao
    abstract fun transactionCategoryDao(): TransactionCategoryDao
    abstract fun userCategoryRuleDao(): UserCategoryRuleDao
    abstract fun merchantAliasDao(): MerchantAliasDao
    abstract fun budgetDao(): BudgetDao

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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create transaction_categories table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transaction_categories` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `iconKey` TEXT NOT NULL,
                        `colorKey` TEXT NOT NULL,
                        `isSystem` INTEGER NOT NULL,
                        `isArchived` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_categories_isArchived` ON `transaction_categories` (`isArchived`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_categories_sortOrder` ON `transaction_categories` (`sortOrder`)")

                // 2. Add categoryId column to expenses
                db.execSQL("ALTER TABLE expenses ADD COLUMN categoryId TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_categoryId ON expenses (categoryId)")

                // 3. Seed built-in system categories
                val now = System.currentTimeMillis()
                for (cat in BuiltInCategories.ALL) {
                    val isSys = if (cat.isSystem) 1 else 0
                    val isArch = if (cat.isArchived) 1 else 0
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO transaction_categories (id, name, iconKey, colorKey, isSystem, isArchived, sortOrder, createdAt, updatedAt)
                        VALUES ('${cat.id}', '${cat.name}', '${cat.iconKey}', '${cat.colorKey}', $isSys, $isArch, ${cat.sortOrder}, $now, $now)
                        """.trimIndent()
                    )
                }

                // 4. Migrate existing Expense.tag data to Expense.categoryId
                db.execSQL("UPDATE expenses SET categoryId = 'food_dining' WHERE tag = 'Food & Dining' OR tag = 'Food'")
                db.execSQL("UPDATE expenses SET categoryId = 'shopping' WHERE tag = 'Shopping'")
                db.execSQL("UPDATE expenses SET categoryId = 'transport' WHERE tag = 'Transport'")
                db.execSQL("UPDATE expenses SET categoryId = 'bills_utilities' WHERE tag = 'Bills & Utilities' OR tag = 'Bills'")
                db.execSQL("UPDATE expenses SET categoryId = 'entertainment' WHERE tag = 'Entertainment'")
                db.execSQL("UPDATE expenses SET categoryId = 'healthcare' WHERE tag = 'Healthcare'")
                db.execSQL("UPDATE expenses SET categoryId = 'travel' WHERE tag = 'Travel'")
                db.execSQL("UPDATE expenses SET categoryId = 'education' WHERE tag = 'Education'")
                db.execSQL("UPDATE expenses SET categoryId = 'transfer' WHERE tag = 'Transfer'")
                db.execSQL("UPDATE expenses SET categoryId = 'cash_withdrawal' WHERE tag = 'Cash Withdrawal'")
                db.execSQL("UPDATE expenses SET categoryId = 'income' WHERE tag = 'Income' OR tag = 'Salary' OR tag = 'Allowance' OR tag = 'Refund' OR tag = 'Gift'")
                db.execSQL("UPDATE expenses SET categoryId = 'other' WHERE tag = 'Other'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add categorySource column to expenses
                db.execSQL("ALTER TABLE expenses ADD COLUMN categorySource TEXT NOT NULL DEFAULT 'NONE'")

                // 2. Mark existing categorized transactions as USER_ASSIGNED so inference won't overwrite them
                db.execSQL("UPDATE expenses SET categorySource = 'USER_ASSIGNED' WHERE categoryId IS NOT NULL")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create user_category_rules table and indices
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_category_rules` (
                        `id` TEXT NOT NULL,
                        `name` TEXT,
                        `matchType` TEXT NOT NULL,
                        `pattern` TEXT NOT NULL,
                        `normalizedPattern` TEXT NOT NULL,
                        `categoryId` TEXT NOT NULL,
                        `priority` INTEGER NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_category_rules_normalizedPattern` ON `user_category_rules` (`normalizedPattern`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_category_rules_categoryId` ON `user_category_rules` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_category_rules_isEnabled` ON `user_category_rules` (`isEnabled`)")

                // 2. Create merchant_aliases table and indices
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `merchant_aliases` (
                        `id` TEXT NOT NULL,
                        `alias` TEXT NOT NULL,
                        `canonicalMerchant` TEXT NOT NULL,
                        `normalizedAlias` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_merchant_aliases_normalizedAlias` ON `merchant_aliases` (`normalizedAlias`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_merchant_aliases_isEnabled` ON `merchant_aliases` (`isEnabled`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create budgets table and indices
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `budgets` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `amountLimit` REAL NOT NULL,
                        `categoryId` TEXT DEFAULT NULL,
                        `periodType` TEXT NOT NULL,
                        `periodAnchor` INTEGER NOT NULL DEFAULT 1,
                        `warningThreshold` REAL NOT NULL DEFAULT 80.0,
                        `exceededThreshold` REAL NOT NULL DEFAULT 100.0,
                        `isEnabled` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_isEnabled` ON `budgets` (`isEnabled`)")

                // 2. Create budget_alert_states table and indices
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `budget_alert_states` (
                        `id` TEXT NOT NULL,
                        `budgetId` TEXT NOT NULL,
                        `periodStart` INTEGER NOT NULL,
                        `warningSent` INTEGER NOT NULL DEFAULT 0,
                        `exceededSent` INTEGER NOT NULL DEFAULT 0,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_alert_states_budgetId` ON `budget_alert_states` (`budgetId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_alert_states_periodStart` ON `budget_alert_states` (`periodStart`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "arctracker_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
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
