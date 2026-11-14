package com.subhashrelangi.arctracker.engine.pipeline

/**
 * Sender classification tier under Indian TRAI DLT and Android application sources.
 */
enum class SenderTier {
    /** Regulated Scheduled Commercial Banks, Payments Banks, Small Finance Banks (e.g. HDFC, SBI, ICICI). */
    TIER_1_BANK,
    /** Regulated Fintech Payment Apps and Wallets (e.g. GPay, PhonePe, Paytm, CRED). */
    TIER_2_FINTECH,
    /** Telecom Carriers, E-Commerce, Food Delivery, Utilities (e.g. Airtel, Jio, Swiggy, Amazon). */
    TIER_3_MERCHANT_OR_CARRIER,
    /** Unknown, personal phone numbers, or unclassified senders. */
    UNKNOWN
}

/**
 * TRAI DLT channel suffix (XY-ABCDEF-Z).
 */
enum class TraiChannelSuffix(val code: Char, val description: String) {
    PROMOTIONAL('P', "Promotional"),
    TRANSACTIONAL('T', "Transactional"),
    SERVICE('S', "Service"),
    GOVERNMENT('G', "Government"),
    UNKNOWN('?', "Unknown");

    companion object {
        fun fromChar(c: Char?): TraiChannelSuffix {
            return when (c?.uppercaseChar()) {
                'P' -> PROMOTIONAL
                'T' -> TRANSACTIONAL
                'S' -> SERVICE
                'G' -> GOVERNMENT
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Structured inspection of an SMS sender address or Android notification package name.
 */
data class TraiSenderInfo(
    val rawSender: String,
    val operatorCircle: String? = null,
    val entityCode: String? = null,
    val channelSuffix: TraiChannelSuffix = TraiChannelSuffix.UNKNOWN,
    val senderTier: SenderTier = SenderTier.UNKNOWN,
    val isPromotional: Boolean = false,
    val isPersonalNumber: Boolean = false
) {
    val isTier1Bank: Boolean get() = senderTier == SenderTier.TIER_1_BANK
    val isTier2Fintech: Boolean get() = senderTier == SenderTier.TIER_2_FINTECH
    val isTier3MerchantOrCarrier: Boolean get() = senderTier == SenderTier.TIER_3_MERCHANT_OR_CARRIER
    val isHardDrop: Boolean get() = isPromotional || (isPersonalNumber && senderTier == SenderTier.UNKNOWN)
}

/**
 * Authority registry and classifier for Indian TRAI DLT SMS sender headers (XY-ABCDEF-Z).
 *
 * In India, commercial SMS headers are mandated by TRAI (Telecom Regulatory Authority of India):
 * - XY: 2 alpha characters for telecom operator and circle code (e.g., AD = Airtel Delhi, VK = Vi Kolkata).
 * - ABCDEF: 3 to 10 alphanumeric characters identifying the registered principal entity.
 * - Z: Optional channel suffix (-P = Promotional, -T = Transactional, -S = Service, -G = Govt).
 */
object TraiSenderRegistry {

    private val TRAI_HEADER_PATTERN = Regex(
        """^(?:([A-Za-z]{2})-)?([A-Za-z0-9]{3,10})(?:-([A-Za-z]))?$"""
    )

    private val TIER_1_BANK_TOKENS = setOf(
        "HDFCBK", "HDFC",
        "SBINB", "SBIINB", "SBISMS", "SBIUPI", "SBIPSG", "SBI",
        "ICICIB", "ICICI",
        "AXISBK", "AXIS",
        "KOTAKB", "KOTAK",
        "PNBSMS", "PNB",
        "BOBSMS", "BARODA", "BOB",
        "CANBNK", "CANARA",
        "INDUSB", "INDUSIND",
        "YESBNK", "YESBANK", "YES",
        "UNIONB", "UNION",
        "IDFCFB", "IDFC",
        "FEDBNK", "FEDERAL",
        "SCISMS", "SCBL", "STANCHAR",
        "CITIBK", "CITI",
        "RBLBNK", "RBL",
        "HSBCIN", "HSBC",
        "BANDHN", "BANDHAN",
        "AUBANK", "AUFIN",
        "IOBCHN", "IOB",
        "UCOBNK", "UCO",
        "CORPBK",
        "CUBBNK",
        "KVBANK", "KVB",
        "SIBBNK", "SOUTHIN",
        "DBSBNK", "DBS",
        "PAYTMB",
        "AIRTELPB",
        "IPPBMS", "IPPB",
        "BOISMS", "BOIIND", "BOI",
        "CBIN", "CENTRAL",
        "INDBNK", "INDIANB",
        "JIOPAY", "JIOBNK"
    )

    private val TIER_2_FINTECH_TOKENS = setOf(
        "GPAY", "GOOGPAY", "GOOGLE",
        "PHONEPE", "PHNPE",
        "PAYTM", "PYTM",
        "CRED",
        "AMZPAY", "AMAZONPAY",
        "MBKWIK", "MOBIKWIK",
        "SLICE",
        "JUPITR", "JUPITER",
        "FIPAY", "FI",
        "NAVI",
        "BHIM", "NPCI"
    )

    private val TIER_3_MERCHANT_CARRIER_TOKENS = setOf(
        // Telecom Carriers & Service Info
        "AIRINF", "AIRTEL", "JIOINF", "JIORCH", "JIONET", "VODAIN", "BSNL", "VI", "IDEAIN", "MTNL",
        // Food & Quick-Commerce
        "SWIGGY", "ZOMATO", "ZEPTOR", "BLINKT", "BIGBSK", "DUNZO", "DOMINO", "PIZZAH", "EATCLB",
        // E-commerce & Retail
        "FLIPKT", "AMZIND", "AMAZON", "MEESHO", "MYNTRA", "AJIO", "NYKAA", "TATACLIQ", "TATA_CLIQ",
        // Mobility & Travel
        "UBER", "OLACAB", "RAPIDO", "IRCTC", "MAKEMYTRIP", "MMT", "GOIBIBO", "YATRA", "INDIGO", "SPICEJT", "AIRIND",
        // DTH & Cable
        "TATASKY", "DISHTV", "D2H", "NETFLIX", "HOTSTAR", "PRIME", "BOOKMYSHOW", "BMS"
    )

    private val FINTECH_PACKAGE_NAMES = setOf(
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "net.one97.paytm",
        "com.dreamplug.androidapp",
        "in.amazon.mShop.android.shopping",
        "com.mobikwik_new",
        "org.npci.upi.security.pinactivity"
    )

    /**
     * Checks if a sender string represents a personal 10-12 digit mobile phone number.
     */
    fun isPersonalMobileSender(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val clean = sender.trim().removePrefix("+").removePrefix("91").trimStart('0')
        return clean.isNotEmpty() && clean.all { it.isDigit() } && clean.length in 10..12
    }

    /**
     * Determines whether the sender is a confirmed TRAI Promotional (-P) channel.
     */
    fun isPromotionalTrai(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val clean = sender.trim().uppercase()
        return clean.endsWith("-P")
    }

    /**
     * Classifies any sender string (SMS address or Android package name) into a [TraiSenderInfo].
     */
    fun classify(sender: String?): TraiSenderInfo {
        if (sender.isNullOrBlank()) {
            return TraiSenderInfo(rawSender = "", senderTier = SenderTier.UNKNOWN)
        }

        val clean = sender.trim()

        // 1. Check for Android Fintech package names
        if (clean.contains(".")) {
            val tier = if (FINTECH_PACKAGE_NAMES.contains(clean)) {
                SenderTier.TIER_2_FINTECH
            } else {
                SenderTier.UNKNOWN
            }
            return TraiSenderInfo(
                rawSender = clean,
                senderTier = tier,
                isPromotional = false,
                isPersonalNumber = false
            )
        }

        // 2. Check for personal mobile numbers (e.g., +919876543210 or 9876543210)
        if (isPersonalMobileSender(clean)) {
            return TraiSenderInfo(
                rawSender = clean,
                senderTier = SenderTier.UNKNOWN,
                isPromotional = false,
                isPersonalNumber = true
            )
        }

        // 3. Match TRAI DLT header pattern XY-ABCDEF-Z or ABCDEF-Z
        val match = TRAI_HEADER_PATTERN.matchEntire(clean)
        if (match != null) {
            val operator = match.groupValues[1].takeIf { it.isNotBlank() }
            val entity = match.groupValues[2]
            val suffixChar = match.groupValues[3].firstOrNull()
            val channelSuffix = TraiChannelSuffix.fromChar(suffixChar)
            val isPromotional = (channelSuffix == TraiChannelSuffix.PROMOTIONAL)

            val upperEntity = entity.uppercase()

            // Resolve Tier
            val tier = when {
                matchesToken(upperEntity, TIER_1_BANK_TOKENS) -> SenderTier.TIER_1_BANK
                matchesToken(upperEntity, TIER_2_FINTECH_TOKENS) -> SenderTier.TIER_2_FINTECH
                matchesToken(upperEntity, TIER_3_MERCHANT_CARRIER_TOKENS) -> SenderTier.TIER_3_MERCHANT_OR_CARRIER
                else -> SenderTier.UNKNOWN
            }

            return TraiSenderInfo(
                rawSender = clean,
                operatorCircle = operator,
                entityCode = entity,
                channelSuffix = channelSuffix,
                senderTier = tier,
                isPromotional = isPromotional,
                isPersonalNumber = false
            )
        }

        // 4. Fallback: Check if clean string contains known bank or merchant tokens
        val upperClean = clean.uppercase()
        val fallbackTier = when {
            matchesToken(upperClean, TIER_1_BANK_TOKENS) -> SenderTier.TIER_1_BANK
            matchesToken(upperClean, TIER_2_FINTECH_TOKENS) -> SenderTier.TIER_2_FINTECH
            matchesToken(upperClean, TIER_3_MERCHANT_CARRIER_TOKENS) -> SenderTier.TIER_3_MERCHANT_OR_CARRIER
            else -> SenderTier.UNKNOWN
        }

        val fallbackPromotional = upperClean.endsWith("-P")

        return TraiSenderInfo(
            rawSender = clean,
            senderTier = fallbackTier,
            isPromotional = fallbackPromotional,
            isPersonalNumber = false
        )
    }

    private fun matchesToken(entity: String, tokens: Set<String>): Boolean {
        if (tokens.contains(entity)) return true
        return tokens.any { token ->
            entity.startsWith(token) || entity.endsWith(token) || (token.length >= 4 && entity.contains(token))
        }
    }
}
