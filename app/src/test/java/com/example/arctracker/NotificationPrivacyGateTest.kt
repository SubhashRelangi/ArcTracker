package com.example.arctracker

import com.example.arctracker.data.Expense
import com.example.arctracker.service.CapturedNotificationInfo
import com.example.arctracker.service.NotificationContent
import com.example.arctracker.service.NotificationPermissionHelper
import com.example.arctracker.service.NotificationReaderService
import com.example.arctracker.settings.AppCatalog
import com.example.arctracker.settings.InMemoryMonitoringSettingsRepository
import com.example.arctracker.settings.MonitoringSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Step 9.4: Unit tests for Notification Monitoring Privacy Gate.
 * Verifies that global monitoring and per-app toggles strictly control notification
 * ingress, and that notification content/extras are NEVER accessed for blocked notifications.
 */
class NotificationPrivacyGateTest {

    private lateinit var service: NotificationReaderService
    private lateinit var repository: InMemoryMonitoringSettingsRepository
    private val gpay = "com.google.android.apps.nbu.paisa.user"
    private val phonepe = "com.phonepe.app"
    private val paytm = "net.one97.paytm"

    @Before
    fun setUp() {
        NotificationPermissionHelper.permissionOverrideForTesting = true
        NotificationReaderService.isConnected = false
        NotificationReaderService.lastCapturedNotification = null
        NotificationReaderService.notificationListener = null
        NotificationReaderService.clearActiveNotifications()

        repository = InMemoryMonitoringSettingsRepository(
            initialGlobalEnabled = true,
            initialEnabledPackages = setOf(gpay, phonepe, paytm)
        )
        NotificationReaderService.settingsRepositoryOverride = repository
        service = NotificationReaderService()
    }

    @After
    fun tearDown() {
        NotificationPermissionHelper.permissionOverrideForTesting = null
        NotificationReaderService.settingsRepositoryOverride = null
        NotificationReaderService.lastCapturedNotification = null
        NotificationReaderService.notificationListener = null
        NotificationReaderService.clearActiveNotifications()
    }

    // 1. Global OFF blocks notification
    @Test
    fun test01_globalOff_blocksNotification() {
        repository.setGlobalEnabled(false)

        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "key_1",
            postTime = 1000L,
            contentExtractor = { NotificationContent(title = "Paid", text = "Rs 100") }
        )

        assertFalse("Notification must be blocked when global monitoring is OFF", result)
        assertNull(NotificationReaderService.lastCapturedNotification)
    }

    // 2. Global ON + enabled package allows notification
    @Test
    fun test02_globalOn_enabledPackage_allowsNotification() {
        repository.setGlobalEnabled(true)
        repository.setAppEnabled(gpay, true)

        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "key_2",
            postTime = 2000L,
            contentExtractor = { NotificationContent(title = "Paid", text = "Rs 200") }
        )

        assertTrue("Notification must be allowed when global is ON and package is enabled", result)
        assertNotNull(NotificationReaderService.lastCapturedNotification)
        assertEquals(gpay, NotificationReaderService.lastCapturedNotification?.packageName)
    }

    // 3. Global ON + disabled package blocks notification
    @Test
    fun test03_globalOn_disabledPackage_blocksNotification() {
        repository.setGlobalEnabled(true)
        repository.setAppEnabled(gpay, false)

        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "key_3",
            postTime = 3000L,
            contentExtractor = { NotificationContent(title = "Paid", text = "Rs 300") }
        )

        assertFalse("Notification must be blocked when package is disabled", result)
        assertNull(NotificationReaderService.lastCapturedNotification)
    }

    // 4. Global OFF blocks enabled Google Pay
    @Test
    fun test04_globalOff_blocksEnabledGooglePay() {
        repository.setAppEnabled(gpay, true)
        repository.setGlobalEnabled(false)

        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "key_4",
            postTime = 4000L,
            contentExtractor = { NotificationContent(title = "Google Pay", text = "Paid 500") }
        )

        assertFalse("Google Pay must be blocked when global monitoring is OFF", result)
    }

    // 5. Global OFF blocks enabled PhonePe
    @Test
    fun test05_globalOff_blocksEnabledPhonePe() {
        repository.setAppEnabled(phonepe, true)
        repository.setGlobalEnabled(false)

        val result = service.processNotification(
            packageName = phonepe,
            notificationKey = "key_5",
            postTime = 5000L,
            contentExtractor = { NotificationContent(title = "PhonePe", text = "Paid 600") }
        )

        assertFalse("PhonePe must be blocked when global monitoring is OFF", result)
    }

    // 6. Global OFF blocks every selected package
    @Test
    fun test06_globalOff_blocksEverySelectedPackage() {
        repository.setEnabledPackages(AppCatalog.defaultEnabledPackages)
        repository.setGlobalEnabled(false)

        for (app in AppCatalog.allApps) {
            val result = service.processNotification(
                packageName = app.packageName,
                notificationKey = "key_${app.packageName}",
                postTime = 1000L,
                contentExtractor = { NotificationContent(title = app.displayName, text = "Test") }
            )
            assertFalse("All packages must be blocked when global is OFF: ${app.packageName}", result)
        }
        assertNull(NotificationReaderService.lastCapturedNotification)
    }

    // 7. Google Pay OFF does not block PhonePe
    @Test
    fun test07_googlePayOff_doesNotBlockPhonePe() {
        repository.setAppEnabled(gpay, false)
        repository.setAppEnabled(phonepe, true)

        val result = service.processNotification(
            packageName = phonepe,
            notificationKey = "key_7",
            postTime = 7000L,
            contentExtractor = { NotificationContent(title = "PhonePe", text = "Received 700") }
        )

        assertTrue("PhonePe must be processed even if Google Pay is disabled", result)
        assertEquals(phonepe, NotificationReaderService.lastCapturedNotification?.packageName)
    }

    // 8. PhonePe OFF does not block Google Pay
    @Test
    fun test08_phonePeOff_doesNotBlockGooglePay() {
        repository.setAppEnabled(phonepe, false)
        repository.setAppEnabled(gpay, true)

        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "key_8",
            postTime = 8000L,
            contentExtractor = { NotificationContent(title = "Google Pay", text = "Received 800") }
        )

        assertTrue("Google Pay must be processed even if PhonePe is disabled", result)
        assertEquals(gpay, NotificationReaderService.lastCapturedNotification?.packageName)
    }

    // 9. Enabling a package allows future notifications
    @Test
    fun test09_enablingPackage_allowsFutureNotifications() {
        repository.setAppEnabled(gpay, false)
        assertFalse(service.processNotification(gpay, "k1", 100L) { NotificationContent("T", "B") })

        repository.setAppEnabled(gpay, true)
        assertTrue(service.processNotification(gpay, "k2", 200L) { NotificationContent("T", "B") })
    }

    // 10. Disabling a package blocks future notifications
    @Test
    fun test10_disablingPackage_blocksFutureNotifications() {
        repository.setAppEnabled(gpay, true)
        assertTrue(service.processNotification(gpay, "k1", 100L) { NotificationContent("T", "B") })

        repository.setAppEnabled(gpay, false)
        assertFalse(service.processNotification(gpay, "k2", 200L) { NotificationContent("T", "B") })
    }

    // 11. Re-enabling a package allows future notifications
    @Test
    fun test11_reenablingPackage_allowsFutureNotifications() {
        repository.setAppEnabled(gpay, false)
        assertFalse(service.processNotification(gpay, "k1", 100L) { NotificationContent("T", "B") })

        repository.setAppEnabled(gpay, true)
        assertTrue(service.processNotification(gpay, "k2", 200L) { NotificationContent("T", "B") })

        repository.setAppEnabled(gpay, false)
        assertFalse(service.processNotification(gpay, "k3", 300L) { NotificationContent("T", "B") })

        repository.setAppEnabled(gpay, true)
        assertTrue(service.processNotification(gpay, "k4", 400L) { NotificationContent("T", "B") })
    }

    // 12. Disabled package does not create CapturedNotificationInfo
    @Test
    fun test12_disabledPackage_doesNotCreateCapturedNotificationInfo() {
        repository.setAppEnabled(gpay, false)
        var listenerInvoked = false
        NotificationReaderService.notificationListener = { listenerInvoked = true }

        service.processNotification(gpay, "key_12", 1200L) { NotificationContent("T", "B") }

        assertFalse("Listener must not be invoked for disabled package", listenerInvoked)
        assertNull(NotificationReaderService.lastCapturedNotification)
        assertNull(NotificationReaderService.getActiveNotification("key_12"))
    }

    // 13. Global OFF does not create CapturedNotificationInfo
    @Test
    fun test13_globalOff_doesNotCreateCapturedNotificationInfo() {
        repository.setGlobalEnabled(false)
        var listenerInvoked = false
        NotificationReaderService.notificationListener = { listenerInvoked = true }

        service.processNotification(gpay, "key_13", 1300L) { NotificationContent("T", "B") }

        assertFalse("Listener must not be invoked when global OFF", listenerInvoked)
        assertNull(NotificationReaderService.lastCapturedNotification)
        assertNull(NotificationReaderService.getActiveNotification("key_13"))
    }

    // 14. Blocked notification does not reach normalization
    @Test
    fun test14_blockedNotification_doesNotReachNormalization() {
        repository.setAppEnabled(gpay, false)
        var contentExtracted = false

        val result = service.processNotification(gpay, "key_14", 1400L) {
            contentExtracted = true
            NotificationContent("Paid Rs 500", "to Cafe")
        }

        assertFalse(result)
        assertFalse("Content must never be extracted for normalization", contentExtracted)
    }

    // 15. Blocked notification does not reach classification
    @Test
    fun test15_blockedNotification_doesNotReachClassification() {
        repository.setGlobalEnabled(false)
        var contentExtracted = false

        val result = service.processNotification(gpay, "key_15", 1500L) {
            contentExtracted = true
            NotificationContent("Debited INR 500", "SBI UPI")
        }

        assertFalse(result)
        assertFalse("Classification must not run", contentExtracted)
    }

    // 16. Blocked notification does not reach extraction
    @Test
    fun test16_blockedNotification_doesNotReachExtraction() {
        repository.setAppEnabled(phonepe, false)
        var contentExtracted = false

        service.processNotification(phonepe, "key_16", 1600L) {
            contentExtracted = true
            NotificationContent("Paid INR 1,200", "Transaction 998877")
        }

        assertFalse("Candidate extraction must not run", contentExtracted)
    }

    // 17. Blocked notification does not reach validation
    @Test
    fun test17_blockedNotification_doesNotReachValidation() {
        repository.setGlobalEnabled(false)
        var contentExtracted = false

        service.processNotification(paytm, "key_17", 1700L) {
            contentExtracted = true
            NotificationContent("Sent Rs 350", "Wallet UPI")
        }

        assertFalse("Validation must not run", contentExtracted)
    }

    // 18. Blocked notification does not reach deduplication
    @Test
    fun test18_blockedNotification_doesNotReachDeduplication() {
        repository.setAppEnabled(gpay, false)
        var contentExtracted = false

        service.processNotification(gpay, "key_18", 1800L) {
            contentExtracted = true
            NotificationContent("Paid Rs 100", "Coffee")
        }

        assertFalse("Deduplication must not run", contentExtracted)
    }

    // 19. Blocked notification does not reach persistence
    @Test
    fun test19_blockedNotification_doesNotReachPersistence() {
        repository.setGlobalEnabled(false)
        var listenerInvoked = false
        NotificationReaderService.notificationListener = { listenerInvoked = true }

        service.processNotification(gpay, "key_19", 1900L) { NotificationContent("Paid", "100") }

        assertFalse("Persistence trigger must not fire", listenerInvoked)
    }

    // 20. Blocked notification does not create a Room record
    @Test
    fun test20_blockedNotification_doesNotCreateRoomRecord() = runBlocking {
        val fakeDao = FakeExpenseDao()
        repository.setAppEnabled(gpay, false)

        service.processNotification(gpay, "key_20", 2000L) { NotificationContent("Paid Rs 500", "Amazon") }

        val count = fakeDao.getCount()
        assertEquals("Room database must remain empty", 0, count)
    }

    // 21. Existing Room transactions remain untouched when package is disabled
    @Test
    fun test21_existingRoomTransactions_remainUntouchedWhenPackageDisabled() = runBlocking {
        val fakeDao = FakeExpenseDao()
        val existingExpense = Expense(
            amount = 150.0,
            merchant = "Existing Google Pay Coffee",
            dateMillis = 1700000000000L,
            source = "Google Pay",
            isPending = false
        )
        fakeDao.insert(existingExpense)
        assertEquals(1, fakeDao.getCount())

        // Disable Google Pay
        repository.setAppEnabled(gpay, false)

        // Verify existing transaction survived completely intact
        val expenses = fakeDao.getAllExpensesList()
        assertEquals(1, expenses.size)
        assertEquals("Existing Google Pay Coffee", expenses[0].merchant)
        assertEquals(150.0, expenses[0].amount, 0.001)
        assertFalse(expenses[0].isPending)
    }

    // 22. Global OFF does not delete existing transactions
    @Test
    fun test22_globalOff_doesNotDeleteExistingTransactions() = runBlocking {
        val fakeDao = FakeExpenseDao()
        fakeDao.insert(Expense(amount = 250.0, merchant = "Lunch", dateMillis = 1700000000000L, source = "PhonePe"))
        fakeDao.insert(Expense(amount = 1200.0, merchant = "Groceries", dateMillis = 1700000001000L, source = "Paytm"))
        assertEquals(2, fakeDao.getCount())

        // Turn global monitoring OFF
        repository.setGlobalEnabled(false)

        val expenses = fakeDao.getAllExpensesList()
        assertEquals(2, expenses.size)
        assertTrue(expenses.any { it.merchant == "Lunch" && it.amount == 250.0 })
        assertTrue(expenses.any { it.merchant == "Groceries" && it.amount == 1200.0 })
    }

    // 23. Re-enabling does not retroactively process ignored notifications
    @Test
    fun test23_reenablingDoesNotRetroactivelyProcessIgnoredNotifications() {
        repository.setAppEnabled(gpay, false)
        val ignoredKey = "ignored_during_disable"

        service.processNotification(gpay, ignoredKey, 2300L) { NotificationContent("Old", "Ignored") }
        assertNull(NotificationReaderService.getActiveNotification(ignoredKey))

        // Re-enable
        repository.setAppEnabled(gpay, true)

        // Active notifications cache should NOT contain old ignored notifications
        assertNull("Old ignored notification must not be retroactively added",
            NotificationReaderService.getActiveNotification(ignoredKey))
    }

    // 24. Unknown package is blocked
    @Test
    fun test24_unknownPackage_isBlocked() {
        val unknown = "com.arbitrary.malicious.app"
        assertFalse("App not in enabledPackages must be blocked", repository.isAppEnabled(unknown))

        val result = service.processNotification(unknown, "unknown_key", 2400L) {
            NotificationContent("Secret Title", "Secret Body")
        }

        assertFalse("Unknown package must be rejected by privacy gate", result)
        assertNull(NotificationReaderService.lastCapturedNotification)
    }

    // 25. Package name comparison uses exact package identity
    @Test
    fun test25_packageNameComparison_usesExactPackageIdentity() {
        repository.setAppEnabled(gpay, true)

        // Similar names or prefixes/suffixes must NOT match
        val fakeSubPackage = "$gpay.imposter"
        val fakePrefix = "prefix.$gpay"
        val typo = gpay.replace("paisa", "paisa1")

        assertFalse(service.processNotification(fakeSubPackage, "k1", 100L) { NotificationContent("T", "B") })
        assertFalse(service.processNotification(fakePrefix, "k2", 200L) { NotificationContent("T", "B") })
        assertFalse(service.processNotification(typo, "k3", 300L) { NotificationContent("T", "B") })
    }

    // 26. Content-Access Test: Notification content is not accessed before the gate
    @Test
    fun test26_contentAccessTest_contentNotAccessedBeforeGate() {
        var globalOffContentAccessed = false
        repository.setGlobalEnabled(false)

        service.processNotification(gpay, "secret_key_1", 2600L) {
            globalOffContentAccessed = true
            NotificationContent(title = "SECRET_TEST_TITLE", text = "SECRET_TEST_BODY")
        }

        assertFalse("Content extractor MUST NOT be invoked when global monitoring is OFF", globalOffContentAccessed)

        var packageOffContentAccessed = false
        repository.setGlobalEnabled(true)
        repository.setAppEnabled(phonepe, false)

        service.processNotification(phonepe, "secret_key_2", 2601L) {
            packageOffContentAccessed = true
            NotificationContent(title = "SECRET_TEST_TITLE", text = "SECRET_TEST_BODY")
        }

        assertFalse("Content extractor MUST NOT be invoked when package is disabled", packageOffContentAccessed)
    }

    // 27. Blocked notification does not log notification content
    @Test
    fun test27_blockedNotification_doesNotLogNotificationContent() {
        repository.setAppEnabled(gpay, false)
        var secretRead = false

        service.processNotification(gpay, "secret_log_key", 2700L) {
            secretRead = true
            NotificationContent(title = "SECRET_BANK_BALANCE_100000", text = "OTP 123456")
        }

        assertFalse("Payload must never be read into any logs or state", secretRead)
        assertNull(NotificationReaderService.lastCapturedNotification)
    }

    // 28. Enabled notification continues through the existing pipeline
    @Test
    fun test28_enabledNotification_continuesThroughExistingPipeline() {
        repository.setGlobalEnabled(true)
        repository.setAppEnabled(gpay, true)

        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "key_28",
            postTime = 2800L,
            channelId = "txn_channel",
            category = "payment",
            contentExtractor = {
                NotificationContent(
                    title = "Google Pay",
                    text = "Paid ₹750 to Merchant",
                    bigText = "Paid ₹750 at Coffee Shop. UPI ID: 123",
                    textLines = listOf("Line 1", "Line 2")
                )
            }
        )

        assertTrue(result)
        val captured = NotificationReaderService.lastCapturedNotification
        assertNotNull(captured)
        assertEquals(gpay, captured?.packageName)
        assertEquals("Google Pay", captured?.title)
        assertEquals("Paid ₹750 to Merchant", captured?.text)
        assertEquals("Paid ₹750 at Coffee Shop. UPI ID: 123", captured?.bigText)
        assertEquals(2, captured?.textLines?.size)
        assertEquals("txn_channel", captured?.channelId)
    }

    // 29. Existing notification update handling remains functional
    @Test
    fun test29_existingNotificationUpdateHandling_remainsFunctional() {
        repository.setAppEnabled(gpay, true)

        // First notification
        val firstResult = service.processNotification(gpay, "key_update_test", 2900L) {
            NotificationContent(title = "Payment in progress", text = "Sending ₹500")
        }
        assertTrue(firstResult)
        assertFalse("First notification is not an update",
            NotificationReaderService.lastCapturedNotification?.isUpdate == true)

        // Updated notification with same key
        val updateResult = service.processNotification(gpay, "key_update_test", 2901L) {
            NotificationContent(title = "Payment Successful", text = "Paid ₹500")
        }
        assertTrue(updateResult)
        assertTrue("Second notification with same key must be marked as update",
            NotificationReaderService.lastCapturedNotification?.isUpdate == true)
    }

    // 30. Existing group notification handling remains functional
    @Test
    fun test30_existingGroupNotificationHandling_remainsFunctional() {
        repository.setAppEnabled(gpay, true)

        val groupResult = service.processNotification(
            packageName = gpay,
            notificationKey = "key_group_test",
            postTime = 3000L,
            groupKey = "group_upi_summary",
            isGroup = true,
            isGroupSummary = true,
            flags = android.app.Notification.FLAG_GROUP_SUMMARY,
            contentExtractor = { NotificationContent(title = "Summary", text = "2 payments") }
        )

        assertTrue(groupResult)
        val captured = NotificationReaderService.lastCapturedNotification
        assertNotNull(captured)
        assertTrue(captured?.isGroup == true)
        assertTrue(captured?.isGroupSummary == true)
        assertEquals("group_upi_summary", captured?.groupKey)
    }

    // 31. Fail-closed error handling: repository failure blocks notification
    @Test
    fun test31_failClosed_repositoryFailureBlocksNotification() {
        val failingRepo = object : MonitoringSettingsRepository {
            override fun getSettings() = throw RuntimeException("Settings storage corrupt")
            override fun setGlobalEnabled(enabled: Boolean) = Unit
            override fun setAppEnabled(packageName: String, enabled: Boolean) = Unit
            override fun setEnabledPackages(packages: Set<String>) = Unit
            override fun resetToDefaults() = Unit
        }
        NotificationReaderService.settingsRepositoryOverride = failingRepo

        var contentRead = false
        val result = service.processNotification(gpay, "fail_closed_key", 3100L) {
            contentRead = true
            NotificationContent("Secret", "Secret")
        }

        assertFalse("Must fail closed and return false when settings cannot be read", result)
        assertFalse("Content extractor must NEVER be invoked when settings cannot be read", contentRead)
    }
}
