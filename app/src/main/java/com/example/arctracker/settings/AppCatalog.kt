package com.example.arctracker.settings

/**
 * Authoritative built-in catalog of supported financial, banking, and SMS applications.
 * All package identifiers are verified real Android package names.
 */
object AppCatalog {

    val allApps: List<SupportedApp> = listOf(
        // UPI & Payment Apps
        SupportedApp(
            packageName = "com.google.android.apps.nbu.paisa.user",
            displayName = "Google Pay",
            description = "UPI payments, bills, recharges",
            category = AppCategory.UPI_PAYMENT,
            defaultEnabled = true
        ),
        SupportedApp(
            packageName = "com.phonepe.app",
            displayName = "PhonePe",
            description = "UPI payments, bills, recharges",
            category = AppCategory.UPI_PAYMENT,
            defaultEnabled = true
        ),
        SupportedApp(
            packageName = "net.one97.paytm",
            displayName = "Paytm",
            description = "UPI payments, wallet, bills",
            category = AppCategory.UPI_PAYMENT,
            defaultEnabled = true
        ),
        SupportedApp(
            packageName = "in.amazon.mShop.android.shopping",
            displayName = "Amazon Pay",
            description = "Shopping, UPI, bills",
            category = AppCategory.UPI_PAYMENT,
            defaultEnabled = true
        ),
        SupportedApp(
            packageName = "com.dreamplug.androidapp",
            displayName = "CRED",
            description = "Credit card payments",
            category = AppCategory.UPI_PAYMENT,
            defaultEnabled = true
        ),
        SupportedApp(
            packageName = "in.org.npci.upiapp",
            displayName = "BHIM UPI",
            description = "UPI Payments",
            category = AppCategory.UPI_PAYMENT,
            defaultEnabled = true
        ),
        SupportedApp(
            packageName = "com.mobikwik_new",
            displayName = "MobiKwik",
            description = "Wallet, UPI, bills",
            category = AppCategory.UPI_PAYMENT,
            defaultEnabled = true
        ),

        // Banking Apps
        SupportedApp(
            packageName = "com.sbi.SBIAnywhere",
            displayName = "YONO SBI",
            description = "Banking, UPI",
            category = AppCategory.BANKING,
            defaultEnabled = true
        ),
        SupportedApp(
            packageName = "com.snapwork.hdfc",
            displayName = "HDFC Bank MobileBanking",
            description = "Banking, UPI",
            category = AppCategory.BANKING,
            defaultEnabled = true
        ),
        SupportedApp(
            packageName = "com.csam.icici.bank.imobile",
            displayName = "iMobile Pay by ICICI",
            description = "Banking, UPI",
            category = AppCategory.BANKING,
            defaultEnabled = true
        ),
        SupportedApp(
            packageName = "com.axis.mobile",
            displayName = "Axis Mobile",
            description = "Banking, UPI",
            category = AppCategory.BANKING,
            defaultEnabled = true
        ),
        SupportedApp(
            packageName = "com.msf.kbank.mobile",
            displayName = "Kotak 811",
            description = "Banking, UPI",
            category = AppCategory.BANKING,
            defaultEnabled = true
        ),
        SupportedApp(
            packageName = "money.jupiter",
            displayName = "Jupiter",
            description = "Neobank, UPI",
            category = AppCategory.BANKING,
            defaultEnabled = true
        ),

        // SMS & Messenger Apps
        SupportedApp(
            packageName = "com.google.android.apps.messaging",
            displayName = "Google Messages",
            description = "Default SMS App",
            category = AppCategory.SMS_MESSENGER,
            defaultEnabled = true
        ),
        SupportedApp(
            packageName = "com.samsung.android.messaging",
            displayName = "Samsung Messages",
            description = "SMS App",
            category = AppCategory.SMS_MESSENGER,
            defaultEnabled = true
        ),
        SupportedApp(
            packageName = "com.truecaller",
            displayName = "Truecaller",
            description = "Caller ID & SMS",
            category = AppCategory.SMS_MESSENGER,
            defaultEnabled = true
        )
    )

    /**
     * Default set of enabled package identifiers for fresh installations.
     */
    val defaultEnabledPackages: Set<String> = allApps
        .filter { it.defaultEnabled }
        .map { it.packageName }
        .toSet()

    /**
     * Retrieves all supported apps grouped by category.
     */
    fun getAppsByCategory(category: AppCategory): List<SupportedApp> {
        return allApps.filter { it.category == category }
    }

    /**
     * Finds an app in the catalog by package name.
     */
    fun findByPackage(packageName: String): SupportedApp? {
        return allApps.find { it.packageName.equals(packageName, ignoreCase = true) }
    }

    /**
     * Checks whether a package name belongs to the catalog.
     */
    fun containsPackage(packageName: String): Boolean {
        return allApps.any { it.packageName.equals(packageName, ignoreCase = true) }
    }
}
