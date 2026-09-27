package com.example.arctracker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arctracker.data.AppDatabase
import com.example.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Milestone 5.2 Physical Device Verification Test on Samsung Galaxy A34 5G.
 *
 * Verifies:
 * 1. Exact real Telugu Jio promotional SMS does NOT create pending expense, review item, or Room record.
 * 2. English Jio promotional offer does NOT create pending expense, review item, or Room record.
 * 3. Genuine English completed Jio recharge creates a valid Room transaction record.
 * 4. Genuine English bank debit creates a valid Room transaction record.
 * 5. Mixed language bank SMS with strong English debit evidence is correctly accepted.
 * 6. Non-financial Spotify promo offer does NOT create a transaction.
 */
@RunWith(AndroidJUnit4::class)
class EnglishOnlyClassificationAndPromoFilteringDeviceTest {

    private lateinit var context: Context
    private lateinit var testDb: AppDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testDb = AppDatabase.createInMemoryDatabase(context)
        NotificationPermissionHelper.permissionOverrideForTesting = true
        NotificationReaderService.clearActiveNotifications()
    }

    @After
    fun tearDown() {
        testDb.close()
        NotificationPermissionHelper.permissionOverrideForTesting = null
        NotificationReaderService.clearActiveNotifications()
    }

    // =========================================================================
    // 1. Real Telugu Jio promotional SMS from physical device
    // =========================================================================
    @Test
    fun test01_device_exactTeluguJioPromotionalSms_zeroRoomRecords() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val exactTeluguMessage = "మీ Jio నంబర్ 9390545539 ప్లాన్ త్వరలో ముగియనుంది. Rs.949 తో రీచార్జ్ చేసుకోండి మరియు 90 రోజుల పాటు JioHotstar ని పొందండి. అపరిమిత 5G డేటా మరియు వాయిస్ కాల్స్, 84 రోజులకు 2 GB/రోజు డేటా ఆనందించండి. రీచార్జ్ చేయడానికి www.jio.com/r/EqormMs2o పైన క్లిక్ చేయండి. షరతులు  మరియు నిబంధనలు వర్తిస్తాయి ."

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            notificationKey = "sms_telugu_jio_promo_1",
            postTime = System.currentTimeMillis(),
            title = "JK-620014-P",
            text = exactTeluguMessage
        )

        // 1. Classification Verification
        val normalized = NotificationNormalizer.normalize(captured)!!
        val classification = FinancialClassifier.classify(normalized)
        assertEquals(FinancialRelevance.NON_FINANCIAL, classification.financialRelevance)
        assertTrue(classification.isNoise)
        assertEquals(NoiseCategory.UNSUPPORTED_LANGUAGE, classification.noiseCategory)

        // 2. Full pipeline processing verification
        val persistResults = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)
        assertEquals(1, persistResults.size)
        assertTrue("Result must be IgnoredNonFinancial", persistResults[0] is ExpensePersistenceResult.IgnoredNonFinancial)

        // 3. Database State: absolutely ZERO records, ZERO pending expenses
        assertEquals(0, dao.getCount())
        assertEquals(0, dao.getPendingExpensesList().size)
    }

    // =========================================================================
    // 2. English Jio promotional plan message
    // =========================================================================
    @Test
    fun test02_device_englishJioPromotionalPlan_zeroRoomRecords() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val promoMsg = "Recharge plan Rs.949 for 90 days with JioHotstar and 2GB/day. Recharge now."

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            notificationKey = "sms_english_jio_promo_2",
            postTime = System.currentTimeMillis(),
            title = "Jio Plan Offer",
            text = promoMsg
        )

        val normalized = NotificationNormalizer.normalize(captured)!!
        val classification = FinancialClassifier.classify(normalized)
        assertEquals(FinancialRelevance.NON_FINANCIAL, classification.financialRelevance)
        assertTrue(classification.isNoise)
        assertEquals(NoiseCategory.PROMOTIONAL_OR_OFFER, classification.noiseCategory)

        val persistResults = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)
        assertEquals(1, persistResults.size)
        assertTrue("Result must be IgnoredNonFinancial", persistResults[0] is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, dao.getCount())
        assertEquals(0, dao.getPendingExpensesList().size)
    }

    // =========================================================================
    // 3. Genuine English completed Jio recharge
    // =========================================================================
    @Test
    fun test03_device_englishCompletedJioRecharge_persistedAsSuccess() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val rechargeMsg = "Your recharge of Rs.949 was successful. Recharge ID: JIO12345."

        val captured = CapturedNotificationInfo(
            packageName = "com.jio.myjio",
            notificationKey = "jio_recharge_success_3",
            postTime = System.currentTimeMillis(),
            title = "Recharge Successful",
            text = rechargeMsg
        )

        val persistResults = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)
        assertEquals(1, persistResults.size)
        assertTrue("Result must be Inserted", persistResults[0] is ExpensePersistenceResult.Inserted)

        val allExpenses = dao.getAllExpensesList()
        assertEquals(1, allExpenses.size)
        val expense = allExpenses[0]
        assertEquals(949.0, expense.amount, 0.001)
        assertEquals("Debit", expense.type)
        assertFalse("Completed genuine transaction should not be pending review", expense.isPending)
    }

    // =========================================================================
    // 4. Genuine English bank debit
    // =========================================================================
    @Test
    fun test04_device_englishBankDebit_persistedAsDebit() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val bankMsg = "A/C XXXX debited Rs.949 for Jio. UPI Ref 987654321."

        val captured = CapturedNotificationInfo(
            packageName = "com.sbi.SBIAnywhere",
            notificationKey = "sbi_debit_4",
            postTime = System.currentTimeMillis(),
            title = "Bank Alert",
            text = bankMsg
        )

        val persistResults = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)
        assertEquals(1, persistResults.size)
        assertTrue("Result must be Inserted", persistResults[0] is ExpensePersistenceResult.Inserted)

        val allExpenses = dao.getAllExpensesList()
        assertEquals(1, allExpenses.size)
        val expense = allExpenses[0]
        assertEquals(949.0, expense.amount, 0.001)
        assertEquals("Debit", expense.type)
        assertFalse(expense.isPending)
    }

    // =========================================================================
    // 5. Mixed language bank SMS with strong English financial debit evidence
    // =========================================================================
    @Test
    fun test05_device_mixedLanguageBankDebit_persistedAsDebit() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val mixedMsg = "మీ A/C ending 1234 debited by Rs.949.00 on 27-Sep-24. UPI Ref 11223344."

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            notificationKey = "sms_mixed_bank_5",
            postTime = System.currentTimeMillis(),
            title = "VM-HDFCBK",
            text = mixedMsg
        )

        val persistResults = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)
        assertEquals(1, persistResults.size)
        assertTrue("Result must be Inserted", persistResults[0] is ExpensePersistenceResult.Inserted)

        val allExpenses = dao.getAllExpensesList()
        assertEquals(1, allExpenses.size)
        val expense = allExpenses[0]
        assertEquals(949.0, expense.amount, 0.001)
        assertEquals("Debit", expense.type)
    }

    // =========================================================================
    // 6. English non-financial promo offer (Spotify)
    // =========================================================================
    @Test
    fun test06_device_englishPromoOffer_zeroRoomRecords() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val spotifyPromo = "Get 3 months of Spotify Premium for just ₹119. Subscribe now!"

        val captured = CapturedNotificationInfo(
            packageName = "com.spotify.music",
            notificationKey = "spotify_promo_6",
            postTime = System.currentTimeMillis(),
            title = "Special Offer",
            text = spotifyPromo
        )

        val persistResults = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)
        assertEquals(1, persistResults.size)
        assertTrue("Result must be IgnoredNonFinancial", persistResults[0] is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, dao.getCount())
    }
}
