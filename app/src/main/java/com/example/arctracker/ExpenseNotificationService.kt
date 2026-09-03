package com.example.arctracker

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.arctracker.data.AppDatabase
import com.example.arctracker.data.Expense
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExpenseNotificationService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    
    private val targetPackages = setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.truecaller",
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "com.google.android.apps.walletnfcrel" // Other GPay variant
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        val prefs = applicationContext.getSharedPreferences("ArcTrackerPrefs", android.content.Context.MODE_PRIVATE)
        val isAutoTrackingEnabled = prefs.getBoolean("isAutoTrackingEnabled", true)
        if (!isAutoTrackingEnabled) return
        
        if (sbn == null) return
        val packageName = sbn.packageName
        if (!targetPackages.contains(packageName)) return

        val notification = sbn.notification
        val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = notification.extras.getString(Notification.EXTRA_TEXT) ?: ""
        val notifKey = sbn.key // Unique key for this notification

        // Check if it's a financial message
        val lowerText = text.lowercase()
        val isFinancial = lowerText.contains("paid") || lowerText.contains("debited") || 
                          lowerText.contains("sent") || lowerText.contains("payment") || 
                          lowerText.contains("spent") || lowerText.contains("deducted") ||
                          lowerText.contains("received") || lowerText.contains("credited") ||
                          lowerText.contains("rs") || lowerText.contains("inr") || lowerText.contains("₹")
                          
        val isPaymentApp = packageName.contains("paisa") || packageName.contains("phonepe") || packageName.contains("wallet")
        
        // Check specific tracking toggles based on source
        if (isPaymentApp) {
            val isNotifTrackingEnabled = prefs.getBoolean("isNotificationTrackingEnabled", true)
            if (!isNotifTrackingEnabled) return
        } else {
            // Assume it's an SMS app
            val isSmsTrackingEnabled = prefs.getBoolean("isSmsTrackingEnabled", true)
            if (!isSmsTrackingEnabled) return
        }
        
        // Always dump if it's from a payment app, otherwise check keywords for SMS
        if (!isFinancial && !isPaymentApp) return

        Log.d("ArcTracker", "Processing Notification: $packageName | Key: $notifKey | Text: $text")

        val expenseData = parseExpenseData(text, title)
        val amount = expenseData?.amount ?: 0.0
        val merchant = expenseData?.merchant ?: title.ifBlank { "Unknown Merchant" }
        val isPending = expenseData == null // If parsing failed, mark as pending
        val type = expenseData?.type ?: if (lowerText.contains("received") || lowerText.contains("credited")) "Credit" else "Debit"

        processExpense(amount, merchant, notifKey, text, isPending, type, isPaymentApp)
    }

    private fun processExpense(amount: Double, merchant: String, key: String, rawText: String, isPending: Boolean, type: String, isPaymentApp: Boolean) {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.expenseDao()
        
        serviceScope.launch {
            // 1. App Update Duplicate: ONLY apply to Payment Apps (GPay/PhonePe). 
            // SMS apps use the same key for an entire conversation thread!
            if (isPaymentApp) {
                val existingByKey = dao.getExpenseByKey(key)
                if (existingByKey != null && (System.currentTimeMillis() - existingByKey.dateMillis) < 60000) {
                    existingByKey.amount = if (existingByKey.amount == 0.0) amount else existingByKey.amount
                    existingByKey.merchant = if (existingByKey.merchant == "Unknown Merchant") merchant else existingByKey.merchant
                    existingByKey.rawText = rawText
                    existingByKey.type = type // Update type just in case
                    existingByKey.isPending = isPending && existingByKey.isPending
                    dao.updateExpense(existingByKey)
                    Log.d("ArcTracker", "Updated existing expense by key: $key")
                    return@launch
                }
            }

            // 2. Double Source Duplicate: Check if there's a recent pending expense within 60 seconds
            // (e.g. GPay and Bank SMS arriving at the same time for the same transaction)
            val sixtySecondsAgo = System.currentTimeMillis() - 60000
            val recentPending = dao.getRecentPendingExpense(sixtySecondsAgo)
            
            if (recentPending != null && recentPending.type == type) {
                recentPending.amount = if (recentPending.amount == 0.0) amount else recentPending.amount
                recentPending.merchant = if (recentPending.merchant == "Unknown Merchant") merchant else recentPending.merchant
                recentPending.rawText = recentPending.rawText + " | " + rawText
                recentPending.isPending = isPending && recentPending.isPending
                dao.updateExpense(recentPending)
                Log.d("ArcTracker", "Merged with recent pending expense")
                return@launch
            }

            // 3. Otherwise, insert a new expense
            dao.insertExpense(
                Expense(
                    amount,
                    merchant,
                    System.currentTimeMillis(),
                    type,
                    key,
                    isPending,
                    rawText
                )
            )
            Log.d("ArcTracker", "Inserted new expense: $amount to $merchant")
        }
    }
    
    data class ParsedExpense(val amount: Double, val merchant: String, val type: String)

    private fun parseExpenseData(text: String, title: String): ParsedExpense? {
        val lowerText = text.lowercase()
        val amountRegex = Regex("(?i)(?:rs\\.?|inr|₹)\\s*([0-9,]+\\.?[0-9]*)")
        val amountMatch = amountRegex.find(text)
        
        if (amountMatch != null) {
            val amountStr = amountMatch.groupValues[1].replace(",", "")
            val amount = amountStr.toDoubleOrNull()
            
            if (amount != null) {
                var merchant = "Unknown"
                
                val terminators = "(?:\\.|\\n| on | thru | by |,|;|\\s+Info|\\s+UPI)"
                val nameChars = "([a-zA-Z0-9\\s@&\\-]+?)"
                
                val merchantPatterns = listOf(
                    Regex("(?i)(?:paid to|sent to|payment to)\\s+$nameChars$terminators"),
                    Regex("(?i)payment of .*? to\\s+$nameChars$terminators"),
                    Regex("(?i)(?:received from|from)\\s+(?!a/c|ac\\b|account)$nameChars$terminators"),
                    Regex("(?i)to\\s+(?!a/c|ac\\b|account)$nameChars$terminators")
                )
                
                for (pattern in merchantPatterns) {
                    val match = pattern.find(text)
                    if (match != null) {
                        val extracted = match.groupValues[1].trim()
                        if (extracted.length > 2 && !extracted.equals("a", ignoreCase = true)) {
                            merchant = extracted
                            break
                        }
                    }
                }
                
                if (merchant == "Unknown" && title.isNotBlank() && title.lowercase() != "messages" && !title.contains("new message")) {
                    merchant = title
                }
                
                val type = if (lowerText.contains("received") || lowerText.contains("credited")) "Credit" else "Debit"
                
                return ParsedExpense(amount, merchant, type)
            }
        }
        return null
    }
}
