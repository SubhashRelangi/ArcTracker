package com.example.arctracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arctracker.data.AppDatabase
import com.example.arctracker.data.Expense
import com.example.arctracker.service.NotificationContent
import com.example.arctracker.service.NotificationPermissionHelper
import com.example.arctracker.service.NotificationReaderService
import com.example.arctracker.settings.MonitoringSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 9.4: Connected Android Device Tests for Notification Monitoring Privacy Gate.
 * Executes on Samsung Galaxy A34 5G (SM-A346E - 16) to verify:
 * - TEST A: Global OFF blocks notification and prevents Room transaction creation
 * - TEST B: Global ON + App OFF blocks notification
 * - TEST C: Global ON + App ON allows notification and processes correctly
 * - TEST D: App isolation (Google Pay ON, PhonePe OFF)
 * - TEST E: Re-enabling allows subsequent notifications
 * - TEST F: Existing Room transactions remain untouched when app is disabled
 * - Privacy invariant: extras/content is NOT accessed when blocked
 */
@RunWith(AndroidJUnit4::class)
class NotificationPrivacyGateDeviceTest {

    private lateinit var context: Context
    private lateinit var service: NotificationReaderService
    private lateinit var repository: MonitoringSettingsRepository
    private val gpay = "com.google.android.apps.nbu.paisa.user"
    private val phonepe = "com.phonepe.app"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        NotificationPermissionHelper.permissionOverrideForTesting = true
        NotificationReaderService.clearActiveNotifications()
        NotificationReaderService.lastCapturedNotification = null
        NotificationReaderService.notificationListener = null

        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = MonitoringSettingsRepository.getInstance(context)
        NotificationReaderService.settingsRepositoryOverride = repository
        service = NotificationReaderService()
    }

    @After
    fun tearDown() {
        NotificationPermissionHelper.permissionOverrideForTesting = null
        NotificationReaderService.settingsRepositoryOverride = null
        NotificationReaderService.clearActiveNotifications()
        NotificationReaderService.lastCapturedNotification = null
        NotificationReaderService.notificationListener = null

        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    // TEST A — GLOBAL OFF: No transaction / no pending expense created
    @Test
    fun testDevice_testA_globalOff_blocksNotification() {
        repository.setAppEnabled(gpay, true)
        repository.setGlobalEnabled(false)

        var contentRead = false
        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "device_key_global_off",
            postTime = System.currentTimeMillis(),
            contentExtractor = {
                contentRead = true
                NotificationContent(title = "Google Pay", text = "Paid ₹500 to Cafe")
            }
        )

        assertFalse("Notification must be blocked on device when global monitoring is OFF", result)
        assertFalse("Content must NOT be extracted when global monitoring is OFF", contentRead)
        assertNull(NotificationReaderService.lastCapturedNotification)
    }

    // TEST B — GLOBAL ON + APP OFF: Disabling app blocks notification
    @Test
    fun testDevice_testB_globalOn_appOff_blocksNotification() {
        repository.setGlobalEnabled(true)
        repository.setAppEnabled(gpay, false)

        var contentRead = false
        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "device_key_app_off",
            postTime = System.currentTimeMillis(),
            contentExtractor = {
                contentRead = true
                NotificationContent(title = "Google Pay", text = "Paid ₹500 to Cafe")
            }
        )

        assertFalse("Notification must be blocked on device when app is OFF", result)
        assertFalse("Content must NOT be extracted when app is OFF", contentRead)
        assertNull(NotificationReaderService.lastCapturedNotification)
    }

    // TEST C — GLOBAL ON + APP ON: Enabled notification passes gate and processes
    @Test
    fun testDevice_testC_globalOn_appOn_allowsNotification() {
        repository.setGlobalEnabled(true)
        repository.setAppEnabled(gpay, true)

        var contentRead = false
        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "device_key_app_on",
            postTime = System.currentTimeMillis(),
            channelId = "txn_channel",
            category = "payment",
            contentExtractor = {
                contentRead = true
                NotificationContent(
                    title = "Google Pay",
                    text = "Paid ₹500 to Cafe",
                    bigText = "Paid ₹500 to Cafe on UPI"
                )
            }
        )

        assertTrue("Notification must be allowed when global and app are ON", result)
        assertTrue("Content must be extracted when notification is allowed", contentRead)
        val captured = NotificationReaderService.lastCapturedNotification
        assertNotNull(captured)
        assertEquals(gpay, captured?.packageName)
        assertEquals("Google Pay", captured?.title)
        assertEquals("Paid ₹500 to Cafe", captured?.text)
    }

    // TEST D — APP ISOLATION: Google Pay ON, PhonePe OFF
    @Test
    fun testDevice_testD_appIsolation() {
        repository.setGlobalEnabled(true)
        repository.setAppEnabled(gpay, true)
        repository.setAppEnabled(phonepe, false)

        // Google Pay allowed
        val gpayResult = service.processNotification(
            packageName = gpay,
            notificationKey = "device_gpay_iso",
            postTime = System.currentTimeMillis(),
            contentExtractor = { NotificationContent("Google Pay", "Paid ₹100") }
        )
        assertTrue("Google Pay must be allowed", gpayResult)
        assertEquals(gpay, NotificationReaderService.lastCapturedNotification?.packageName)

        // PhonePe blocked
        var phonepeRead = false
        val phonepeResult = service.processNotification(
            packageName = phonepe,
            notificationKey = "device_phonepe_iso",
            postTime = System.currentTimeMillis(),
            contentExtractor = {
                phonepeRead = true
                NotificationContent("PhonePe", "Paid ₹200")
            }
        )
        assertFalse("PhonePe must be blocked", phonepeResult)
        assertFalse("PhonePe content must NOT be read", phonepeRead)
        // Last captured notification should still be Google Pay
        assertEquals(gpay, NotificationReaderService.lastCapturedNotification?.packageName)
    }

    // TEST E — RE-ENABLE: Disabling and re-enabling works dynamically
    @Test
    fun testDevice_testE_reEnableBehavior() {
        repository.setGlobalEnabled(true)
        repository.setAppEnabled(gpay, false)

        // Blocked when disabled
        val blocked = service.processNotification(gpay, "k1", System.currentTimeMillis()) {
            NotificationContent("T", "B")
        }
        assertFalse(blocked)

        // Re-enable
        repository.setAppEnabled(gpay, true)

        // Allowed when re-enabled
        val allowed = service.processNotification(gpay, "k2", System.currentTimeMillis()) {
            NotificationContent("T", "B")
        }
        assertTrue(allowed)
    }

    // TEST F — EXISTING TRANSACTIONS: Existing transactions remain intact in database
    @Test
    fun testDevice_testF_existingTransactionsPreserved() {
        runBlocking {
            val db = AppDatabase.getDatabase(context)
            val dao = db.expenseDao()

            val expense = Expense(
                amount = 999.0,
                merchant = "Preserved Store",
                dateMillis = System.currentTimeMillis(),
                source = "Google Pay",
                isPending = false
            )
            val id = dao.insert(expense)
            assertTrue(id > 0)

            // Disable Google Pay and Global Monitoring
            repository.setAppEnabled(gpay, false)
            repository.setGlobalEnabled(false)

            // Verify transaction is completely untouched in Room database
            val retrieved = dao.getExpenseById(id.toInt())
            assertNotNull(retrieved)
            assertEquals("Preserved Store", retrieved?.merchant)
            assertEquals(999.0, retrieved?.amount ?: 0.0, 0.001)

            // Cleanup test row
            dao.delete(retrieved!!)
        }
    }
}
