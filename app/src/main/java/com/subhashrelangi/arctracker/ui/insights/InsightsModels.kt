package com.subhashrelangi.arctracker.ui.insights

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * UI State Models for the ArcTracker Insights Page.
 */

data class NetPositionVelocityData(
    val netAmount: Double = 0.0,
    val totalInflow: Double = 0.0,
    val totalOutflow: Double = 0.0,
    val savingsRate: Double = 0.0,
    val savingsStatus: String = "Balanced",
    val dailyRunRate: Double = 0.0,
    val dailyRunRateChangeText: String = "On Track",
    val isDailyRatePositive: Boolean = true,
    val predictedMonthEnd: Double = 0.0,
    val targetStatusText: String = "Under Target",
    val isTargetAchieved: Boolean = true
)

data class CategoryAllocationItem(
    val id: String?,
    val name: String,
    val percentage: Double,
    val amount: Double,
    val color: Color,
    val iconKey: String = "category",
    val transactionCount: Int = 0
)

data class CategoryAllocationData(
    val categories: List<CategoryAllocationItem> = emptyList(),
    val primaryDriverName: String = "None",
    val primaryDriverPercentage: Double = 0.0,
    val primaryDriverDetails: String = "",
    val totalSpendFormatted: String = "0",
    val periodLabel: String = "",
    val isEmpty: Boolean = true
)

data class MerchantSpendItem(
    val name: String,
    val amount: Double,
    val percentage: Double,
    val orderCount: Int,
    val averageAmount: Double,
    val paymentBadge: String,
    val iconType: String
)

data class AutomationHealthData(
    val handsFreePercentage: Int = 100,
    val automatedCount: Int = 0,
    val totalEvents: Int = 0,
    val notificationPercentage: Int = 0,
    val smsPercentage: Int = 0,
    val manualPercentage: Int = 0,
    val notificationSources: String = "GPay, PhonePe",
    val smsSources: String = "HDFC, SBI SMS",
    val manualSources: String = "Cash / Offline"
)

data class HashIntegrityData(
    val duplicateCount: Int = 0,
    val integrityPercentage: Int = 100,
    val description: String = ""
)
