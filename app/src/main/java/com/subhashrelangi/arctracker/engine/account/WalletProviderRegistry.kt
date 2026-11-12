package com.subhashrelangi.arctracker.engine.account

import android.content.Context
import android.content.pm.PackageManager
import com.subhashrelangi.arctracker.data.AccountSource
import com.subhashrelangi.arctracker.data.KnownFinancialAccount

/**
 * Representation of a supported digital wallet or payment app provider.
 */
data class WalletProvider(
    val id: String,
    val canonicalName: String,
    val packageName: String,
    val keywords: List<String>,
    val senderTokens: List<String>
)

/**
 * Industrial registry for digital wallets and liquid cash instruments.
 *
 * Guarantees:
 * 1. Digital wallets have a single instance per payment provider (no arbitrary suffixes needed).
 * 2. Installed payment apps (PhonePe, Paytm, Google Pay, Amazon Pay, etc.) can be detected on-device.
 * 3. Liquid Cash is modeled as a first-class, zero-input account ("cash_liquid").
 * 4. Deterministic canonical IDs: "${providerId}_wallet" for wallets, "cash_liquid" for cash.
 */
object WalletProviderRegistry {

    val PHONEPE = WalletProvider(
        id = "phonepe",
        canonicalName = "PhonePe Wallet",
        packageName = "com.phonepe.app",
        keywords = listOf("phonepe wallet", "phonepe gift card", "phonepe balance"),
        senderTokens = listOf("PHONEPE", "PHNPE")
    )

    val PAYTM = WalletProvider(
        id = "paytm",
        canonicalName = "Paytm Wallet",
        packageName = "net.one97.paytm",
        keywords = listOf("paytm wallet", "paytm balance"),
        senderTokens = listOf("PAYTM", "PYTM")
    )

    val AMAZON_PAY = WalletProvider(
        id = "amazon_pay",
        canonicalName = "Amazon Pay Balance",
        packageName = "in.amazon.mShop.android.shopping",
        keywords = listOf("amazon pay balance", "amazon pay wallet", "amazon balance", "amazon gift card"),
        senderTokens = listOf("AMAZON", "AMZPAY")
    )

    val GPAY = WalletProvider(
        id = "gpay",
        canonicalName = "Google Pay Balance",
        packageName = "com.google.android.apps.nbu.paisa.user",
        keywords = listOf("google pay balance", "gpay wallet", "gpay balance"),
        senderTokens = listOf("GOOGLE", "GOOGPAY")
    )

    val MOBIKWIK = WalletProvider(
        id = "mobikwik",
        canonicalName = "MobiKwik Wallet",
        packageName = "com.mobikwik_new",
        keywords = listOf("mobikwik wallet", "mobikwik balance"),
        senderTokens = listOf("MBKWIK", "MOBIKWIK")
    )

    val CRED = WalletProvider(
        id = "cred",
        canonicalName = "CRED Balance",
        packageName = "com.dreamplug.androidapp",
        keywords = listOf("cred balance", "cred cash", "cred wallet"),
        senderTokens = listOf("CRED")
    )

    private val ALL_PROVIDERS = listOf(
        PHONEPE,
        PAYTM,
        AMAZON_PAY,
        GPAY,
        MOBIKWIK,
        CRED
    )

    fun getAllSupportedWallets(): List<WalletProvider> = ALL_PROVIDERS

    /**
     * Inspects device packages to determine which supported payment apps are installed.
     */
    fun getInstalledWallets(context: Context): List<WalletProvider> {
        val pm = context.packageManager
        return ALL_PROVIDERS.filter { provider ->
            isPackageInstalled(pm, provider.packageName)
        }
    }

    /**
     * Checks if a specific payment app package is installed on the device.
     */
    fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Finds a registered wallet provider by its canonical ID.
     */
    fun getProviderById(id: String): WalletProvider? {
        val clean = id.trim().lowercase()
        return ALL_PROVIDERS.firstOrNull { it.id == clean }
    }

    /**
     * Matches text or sender tokens against known wallet providers.
     */
    fun findProviderFromText(text: String, sender: String? = null): WalletProvider? {
        val lowerText = text.lowercase()
        // 1. Check body keywords
        for (provider in ALL_PROVIDERS) {
            if (provider.keywords.any { lowerText.contains(it) }) {
                return provider
            }
        }

        // 2. Check sender tokens if sender is present
        if (!sender.isNullOrBlank()) {
            val cleanSender = sender.substringAfter("-").trim().uppercase()
            for (provider in ALL_PROVIDERS) {
                if (provider.senderTokens.any { cleanSender.contains(it) }) {
                    // Only match sender if text also contains "wallet" or "balance" or "gift"
                    if (lowerText.contains("wallet") || lowerText.contains("balance") || lowerText.contains("gift card")) {
                        return provider
                    }
                }
            }
        }

        return null
    }

    /**
     * Creates a new [KnownFinancialAccount] for a digital wallet.
     */
    fun createWalletAccount(
        providerId: String,
        customName: String? = null,
        source: AccountSource = AccountSource.USER_CONFIRMED
    ): KnownFinancialAccount {
        val provider = getProviderById(providerId)
        val name = customName?.takeUnless { it.isBlank() } ?: provider?.canonicalName ?: "${providerId.replaceFirstChar { it.uppercase() }} Wallet"
        return KnownFinancialAccount.create(
            institutionId = providerId.trim().lowercase(),
            institutionName = name,
            accountSuffix = "",
            instrumentType = InstrumentType.WALLET,
            confidence = IdentityConfidence.HIGH,
            source = source
        )
    }

    /**
     * Creates a new [KnownFinancialAccount] for liquid physical cash.
     */
    fun createLiquidCashAccount(
        customName: String? = null,
        source: AccountSource = AccountSource.USER_CONFIRMED
    ): KnownFinancialAccount {
        val name = customName?.takeUnless { it.isBlank() } ?: "Liquid Cash"
        return KnownFinancialAccount.create(
            institutionId = "cash",
            institutionName = name,
            accountSuffix = "",
            instrumentType = InstrumentType.CASH,
            confidence = IdentityConfidence.HIGH,
            source = source
        )
    }
}
