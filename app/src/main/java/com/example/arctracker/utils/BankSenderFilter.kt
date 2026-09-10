package com.example.arctracker.utils

/**
 * Filters SMS senders so that only messages from recognized Indian bank senders
 * are imported. In India, transaction SMS come from alphanumeric DLT sender IDs
 * like "VM-SBIUPI-S", "JD-HDFCBK", "AX-ICICIB".
 *
 * Instead of assuming a fixed format, every dash-separated segment of the address
 * is scanned for known bank keywords/short codes, so variants like SBIUPI, SBIIN,
 * HDFCBK, ICICIB etc. are all recognized.
 */
object BankSenderFilter {

    /**
     * Known bank short codes / keywords found inside DLT sender IDs.
     * Format: keyword -> display name.
     */
    private val bankKeywords: Map<String, String> = mapOf(
        // Public sector banks
        "SBI" to "State Bank of India",
        "SBIIN" to "State Bank of India",
        "SBIP" to "State Bank of India",
        "PNB" to "Punjab National Bank",
        "PNBLD" to "Punjab National Bank",
        "CANARA" to "Canara Bank",
        "CNRB" to "Canara Bank",
        "UNION" to "Union Bank of India",
        "UNB" to "Union Bank of India",
        "UBI" to "Union Bank of India",
        "BANKOFBARODA" to "Bank of Baroda",
        "BOB" to "Bank of Baroda",
        "BOI" to "Bank of India",
        "INDIANB" to "Indian Bank",
        "INDB" to "Indian Bank",
        "IOB" to "Indian Overseas Bank",
        "CENTRALBK" to "Central Bank of India",
        "CBIN" to "Central Bank of India",
        "UCOBANK" to "UCO Bank",
        "UCO" to "UCO Bank",
        "BANKOFMAH" to "Bank of Maharashtra",
        "MAHABANK" to "Bank of Maharashtra",
        "IPBM" to "India Post Payments Bank",
        "IPPB" to "India Post Payments Bank",
        // Private banks
        "HDFC" to "HDFC Bank",
        "HDFCBK" to "HDFC Bank",
        "ICICI" to "ICICI Bank",
        "ICIC" to "ICICI Bank",
        "AXIS" to "Axis Bank",
        "KOTAK" to "Kotak Mahindra Bank",
        "KTAK" to "Kotak Mahindra Bank",
        "KMBL" to "Kotak Mahindra Bank",
        "IDFC" to "IDFC First Bank",
        "IDFCFB" to "IDFC First Bank",
        "YESB" to "Yes Bank",
        "INDUSIND" to "IndusInd Bank",
        "INDUSB" to "IndusInd Bank",
        "FEDERAL" to "Federal Bank",
        "FEDL" to "Federal Bank",
        "RBL" to "RBL Bank",
        "BANDHAN" to "Bandhan Bank",
        "BNDL" to "Bandhan Bank",
        "CSB" to "CSB Bank",
        "JNK" to "Jammu & Kashmir Bank",
        "JKBANK" to "Jammu & Kashmir Bank",
        "KARVB" to "Karur Vysya Bank",
        "KVB" to "Karur Vysya Bank",
        "TMB" to "Tamilnad Mercantile Bank",
        "CUB" to "City Union Bank",
        "DKB" to "Dhanlaxmi Bank",
        "AUB" to "AU Small Finance Bank",
        "AUFB" to "AU Small Finance Bank",
        "ESAF" to "ESAF Small Finance Bank",
        "UJJIVAN" to "Ujjivan Small Finance Bank",
        "SFB" to "Small Finance Bank",
        // Foreign banks
        "CITI" to "Citibank",
        "HSBC" to "HSBC Bank",
        "SCB" to "Standard Chartered Bank",
        "DBS" to "DBS Bank",
        "DEUTSCHE" to "Deutsche Bank",
        // Generic fallbacks (contain "bank" variants in the sender ID)
        "BANK" to "Bank",
        "BANKIN" to "Bank",
        "BNK" to "Bank"
    )

    // Words that mark a message as promotional / marketing, even from a bank sender.
    private val promotionalKeywords = listOf(
        "offer", "% off", "off on", "discount", "win ", "winner", "lucky draw",
        "click here", "apply now", "register now", "redeem", "limited period",
        "subscribe", "guaranteed", "shop now", "buy now", "shop & win",
        "cashback offer", "deal of", "use code", "coupon",
        "personal loan", "pre-approved", "preapproved", "investment plan",
        "fixed deposit rates", "fd rates", "credit card offer"
    )

    /** Extracts candidate sender segments from an address, e.g.
     *  "VM-SBIUPI-S" -> ["VM", "SBIUPI", "S"] */
    fun senderSegments(address: String): List<String> {
        val trimmed = address.trim().uppercase()
        if (trimmed.isBlank()) return emptyList()
        return trimmed.split('-', ' ', '_', '/').filter { it.isNotBlank() }
    }

    /** True if the sender is a recognized bank sender ID (e.g. "VM-SBIUPI-S"). */
    fun isBankSender(address: String): Boolean {
        val trimmed = address.trim().uppercase()
        if (trimmed.isBlank()) return false

        // Transaction SMS always come from an alphanumeric DLT sender.
        // Plain numbers are personal / promotional senders -> never import.
        if (trimmed.none { it.isLetter() }) return false

        return detectBankName(trimmed) != null
    }

    /**
     * Scans every segment of the sender address for a known bank keyword.
     * Returns the matched bank display name, or null if none found.
     */
    fun detectBankName(address: String): String? {
        val segments = senderSegments(address)
        for ((keyword, displayName) in bankKeywords.entries.sortedByDescending { it.key.length }) {
            if (segments.any { it.contains(keyword) }) {
                return displayName
            }
        }
        return null
    }

    /** True if the message body looks like a marketing / promotional blast. */
    fun isPromotional(body: String): Boolean {
        val lower = body.lowercase()
        return promotionalKeywords.any { lower.contains(it) }
    }
}