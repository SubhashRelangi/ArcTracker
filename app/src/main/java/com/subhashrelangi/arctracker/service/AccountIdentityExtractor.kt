package com.subhashrelangi.arctracker.service

/**
 * Pure, deterministic extractor for financial institution and account/instrument identity (Step 3).
 *
 * Guarantees:
 * 1. Extraction happens strictly AFTER candidate validation.
 * 2. Missing bank or missing account NEVER invalidates a transaction.
 * 3. Never retains full account or card numbers (normalized to safe suffixes).
 * 4. Cards are explicitly distinguished from bank accounts.
 * 5. Deterministic identity generation without guessing.
 */
object AccountIdentityExtractor {

    private data class BankDefinition(
        val id: String,
        val canonicalName: String,
        val bodyPatterns: List<Regex>,
        val senderTokens: List<String>
    )

    private val BANK_DEFINITIONS: List<BankDefinition> = listOf(
        BankDefinition(
            id = "hdfc",
            canonicalName = "HDFC Bank",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:HDFC\s*Bank|HDFCBK|HDFC)\b"""),
                Regex("""(?i)[-–—]\s*HDFC(?:Bank|BK)?\b""")
            ),
            senderTokens = listOf("HDFCBK", "HDFC")
        ),
        BankDefinition(
            id = "sbi",
            canonicalName = "SBI",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:State\s+Bank\s+of\s+India|SBIINB|SBIUPI|SBI)\b"""),
                Regex("""(?i)[-–—]\s*SBI\b""")
            ),
            senderTokens = listOf("SBIINB", "SBIUPI", "SBIPSG", "SBI")
        ),
        BankDefinition(
            id = "icici",
            canonicalName = "ICICI Bank",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:ICICI\s*Bank|ICICIB|ICICI)\b"""),
                Regex("""(?i)[-–—]\s*ICICI\b""")
            ),
            senderTokens = listOf("ICICIB", "ICICI")
        ),
        BankDefinition(
            id = "axis",
            canonicalName = "Axis Bank",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:Axis\s*Bank|AXISBK|Axis)\b"""),
                Regex("""(?i)[-–—]\s*AXIS\b""")
            ),
            senderTokens = listOf("AXISBK", "AXIS")
        ),
        BankDefinition(
            id = "kotak",
            canonicalName = "Kotak Bank",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:Kotak\s*(?:Mahindra)?(?:\s*Bank)?|KOTAKB)\b"""),
                Regex("""(?i)[-–—]\s*KOTAK\b""")
            ),
            senderTokens = listOf("KOTAKB", "KOTAK")
        ),
        BankDefinition(
            id = "pnb",
            canonicalName = "PNB",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:Punjab\s+National\s+Bank|PNBSMS|PNB)\b"""),
                Regex("""(?i)[-–—]\s*PNB\b""")
            ),
            senderTokens = listOf("PNBSMS", "PNB")
        ),
        BankDefinition(
            id = "bob",
            canonicalName = "Bank of Baroda",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:Bank\s+of\s+Baroda|BOBSMS|BOB)\b"""),
                Regex("""(?i)[-–—]\s*BOB\b""")
            ),
            senderTokens = listOf("BOBSMS", "BARODA", "BOB")
        ),
        BankDefinition(
            id = "canara",
            canonicalName = "Canara Bank",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:Canara\s*Bank|CANBNK|Canara)\b"""),
                Regex("""(?i)[-–—]\s*CANARA\b""")
            ),
            senderTokens = listOf("CANBNK", "CANARA")
        ),
        BankDefinition(
            id = "ippb",
            canonicalName = "IPPB",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:India\s+Post(?:\s*Payments\s*Bank)?|IPPB)\b"""),
                Regex("""(?i)[-–—]\s*IPPB\b""")
            ),
            senderTokens = listOf("IPPB", "IPPBMS")
        ),
        BankDefinition(
            id = "apgb",
            canonicalName = "APGBank",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:APGBank|APGB)\b"""),
                Regex("""(?i)[-–—]\s*APGB(?:ank)?\b""")
            ),
            senderTokens = listOf("APGB", "APGBANK")
        ),
        BankDefinition(
            id = "union",
            canonicalName = "Union Bank",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:Union\s*Bank(?:\s*of\s*India)?|UNIONB)\b"""),
                Regex("""(?i)[-–—]\s*UNIONB?\b""")
            ),
            senderTokens = listOf("UNIONB", "UBIN")
        ),
        BankDefinition(
            id = "indusind",
            canonicalName = "IndusInd Bank",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:IndusInd(?:\s*Bank)?|INDUSB)\b"""),
                Regex("""(?i)[-–—]\s*INDUS(?:IND)?\b""")
            ),
            senderTokens = listOf("INDUSB", "INDUSIND")
        ),
        BankDefinition(
            id = "yesbank",
            canonicalName = "Yes Bank",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:Yes\s*Bank|YESBNK)\b"""),
                Regex("""(?i)[-–—]\s*YESBANK?\b""")
            ),
            senderTokens = listOf("YESBNK", "YESBANK")
        ),
        BankDefinition(
            id = "idfc",
            canonicalName = "IDFC FIRST Bank",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:IDFC\s*(?:FIRST)?(?:\s*Bank)?|IDFCFB)\b"""),
                Regex("""(?i)[-–—]\s*IDFC(?:FIRST)?\b""")
            ),
            senderTokens = listOf("IDFCFB", "IDFC")
        ),
        BankDefinition(
            id = "federal",
            canonicalName = "Federal Bank",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:Federal\s*Bank|FEDBNK)\b"""),
                Regex("""(?i)[-–—]\s*FEDERAL\b""")
            ),
            senderTokens = listOf("FEDBNK", "FEDERAL")
        ),
        BankDefinition(
            id = "boi",
            canonicalName = "Bank of India",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:Bank\s+of\s+India|BOISMS|BOI)\b"""),
                Regex("""(?i)[-–—]\s*BOI\b""")
            ),
            senderTokens = listOf("BOISMS", "BOIIND")
        ),
        BankDefinition(
            id = "cbin",
            canonicalName = "Central Bank of India",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:Central\s+Bank(?:\s*of\s*India)?|CBIN)\b"""),
                Regex("""(?i)[-–—]\s*CBIN\b""")
            ),
            senderTokens = listOf("CBIN", "CENTRAL")
        ),
        BankDefinition(
            id = "indianbank",
            canonicalName = "Indian Bank",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:Indian\s*Bank|INDBNK)\b"""),
                Regex("""(?i)[-–—]\s*INDIANB\b""")
            ),
            senderTokens = listOf("INDBNK", "INDIANB")
        ),
        BankDefinition(
            id = "rbl",
            canonicalName = "RBL Bank",
            bodyPatterns = listOf(
                Regex("""(?i)\b(?:RBL\s*Bank|RBLBNK|RBL)\b"""),
                Regex("""(?i)[-–—]\s*RBL\b""")
            ),
            senderTokens = listOf("RBLBNK", "RBL")
        )
    )

    // Regex for card suffix in raw SMS body
    private val CARD_PATTERN = Regex(
        """(?i)\b(?:card)\s*(?:no\.?|num(?:ber)?\.?)?\s*(?:ending\s*(?:with|in)?)?\s*[:\-]?\s*([xX*]*\d{3,20})\b"""
    )

    // Regex for bank account suffix in raw SMS body
    private val ACCOUNT_PATTERN = Regex(
        """(?i)\b(?:a/c|account|acct)\s*(?:no\.?|num(?:ber)?\.?)?\s*(?:ending\s*(?:with|in)?)?\s*[:\-]?\s*([xX*]*\d{3,20})\b"""
    )

    // Masked standalone account pattern e.g. "XX4381", "**1234", "XXXXXX1234"
    private val MASKED_ACCOUNT_PATTERN = Regex(
        """(?i)\b([xX*]{2,}\d{3,6})\b"""
    )

    /**
     * Extracts [FinancialAccountIdentity] from a validated transaction candidate and raw SMS info.
     *
     * @param candidate The validated transaction candidate from Step 6.
     * @param rawBody The original body of the SMS.
     * @param sender The sender address (e.g. "AD-HDFCBK" or phone number).
     */
    fun extractIdentity(
        candidate: ValidatedTransactionCandidate,
        rawBody: String,
        sender: String?
    ): FinancialAccountIdentity {
        val struct = candidate.candidate

        // 1. Instrument Type & Suffix Extraction
        var instrumentType = InstrumentType.UNKNOWN
        var accountSuffix: String? = null
        var cardSuffix: String? = null
        var suffixSource = "NONE"

        // Priority A: Candidate already captured a card suffix
        if (!struct.cardSuffix.isNullOrBlank()) {
            instrumentType = InstrumentType.CARD
            cardSuffix = safeSuffix(struct.cardSuffix)
            suffixSource = "CANDIDATE_CARD"
        }
        // Priority B: Body explicitly indicates a card
        else {
            val cardMatch = CARD_PATTERN.find(rawBody)
            if (cardMatch != null) {
                val rawSuffix = cardMatch.groupValues[1]
                val safe = safeSuffix(rawSuffix)
                if (safe != null) {
                    instrumentType = InstrumentType.CARD
                    cardSuffix = safe
                    suffixSource = "BODY_CARD"
                }
            }
        }

        // If not a card, check for bank account
        if (instrumentType != InstrumentType.CARD) {
            // Priority C: Candidate already captured an account suffix
            if (!struct.accountSuffix.isNullOrBlank()) {
                instrumentType = InstrumentType.BANK_ACCOUNT
                accountSuffix = safeSuffix(struct.accountSuffix)
                suffixSource = "CANDIDATE_ACCOUNT"
            }
            // Priority D: Body explicitly indicates an account
            else {
                val accMatch = ACCOUNT_PATTERN.find(rawBody)
                if (accMatch != null) {
                    val rawSuffix = accMatch.groupValues[1]
                    val safe = safeSuffix(rawSuffix)
                    if (safe != null) {
                        instrumentType = InstrumentType.BANK_ACCOUNT
                        accountSuffix = safe
                        suffixSource = "BODY_ACCOUNT"
                    }
                } else {
                    val maskedMatch = MASKED_ACCOUNT_PATTERN.find(rawBody)
                    if (maskedMatch != null) {
                        val safe = safeSuffix(maskedMatch.groupValues[1])
                        if (safe != null) {
                            instrumentType = InstrumentType.BANK_ACCOUNT
                            accountSuffix = safe
                            suffixSource = "BODY_MASKED_ACCOUNT"
                        }
                    }
                }
            }
        }

        // 2. Institution / Bank Extraction
        var institutionId: String? = null
        var institutionName: String? = null
        var bankSource = "NONE"

        // Priority 1: Explicit bank match in SMS body
        for (bank in BANK_DEFINITIONS) {
            if (bank.bodyPatterns.any { it.containsMatchIn(rawBody) }) {
                institutionId = bank.id
                institutionName = bank.canonicalName
                bankSource = "BODY_EXPLICIT"
                break
            }
        }

        // Priority 2: Pre-extracted bank from candidate if not found in body
        if (institutionName == null && !struct.bank.isNullOrBlank()) {
            val matchedBank = BANK_DEFINITIONS.firstOrNull { bank ->
                bank.canonicalName.equals(struct.bank, ignoreCase = true) ||
                        bank.id.equals(struct.bank, ignoreCase = true) ||
                        bank.senderTokens.any { it.equals(struct.bank, ignoreCase = true) }
            }
            if (matchedBank != null) {
                institutionId = matchedBank.id
                institutionName = matchedBank.canonicalName
                bankSource = "CANDIDATE_BANK"
            } else {
                institutionId = struct.bank.lowercase().filter { it.isLetterOrDigit() }
                institutionName = struct.bank
                bankSource = "CANDIDATE_BANK_CUSTOM"
            }
        }

        // Priority 3: Recognized Bank Sender as supporting evidence
        if (institutionName == null && !sender.isNullOrBlank()) {
            val cleanSender = sender.substringAfter("-").trim().uppercase()
            val matchedBank = BANK_DEFINITIONS.firstOrNull { bank ->
                bank.senderTokens.any { token -> cleanSender.contains(token) }
            }
            if (matchedBank != null) {
                institutionId = matchedBank.id
                institutionName = matchedBank.canonicalName
                bankSource = "SENDER_HEADER"
            }
        }

        // 3. Identity Confidence Assessment
        val hasInst = !institutionName.isNullOrBlank()
        val hasSuffix = !accountSuffix.isNullOrBlank() || !cardSuffix.isNullOrBlank()

        val confidence = when {
            hasInst && hasSuffix -> IdentityConfidence.HIGH
            hasInst || hasSuffix -> IdentityConfidence.MEDIUM
            else -> IdentityConfidence.UNKNOWN
        }

        val evidenceSource = "bank=$bankSource;suffix=$suffixSource"

        return FinancialAccountIdentity(
            institutionId = institutionId,
            institutionName = institutionName,
            accountSuffix = accountSuffix,
            cardSuffix = cardSuffix,
            instrumentType = instrumentType,
            confidence = confidence,
            evidenceSource = evidenceSource
        )
    }

    /**
     * Reduces any extracted account/card number representation to a safe trailing suffix (e.g. 4 digits).
     *
     * Never retains full account numbers or sensitive credentials.
     */
    fun safeSuffix(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length >= 4 -> digits.takeLast(4)
            digits.length == 3 -> digits
            else -> null
        }
    }

    /**
     * Deterministically extracts canonical bank name from free-form text snippets (e.g. note or rawText).
     */
    fun extractBankFromText(vararg texts: String?): String? {
        val combined = texts.filterNotNull().joinToString(" ")
        if (combined.isBlank()) return null
        for (bank in BANK_DEFINITIONS) {
            if (bank.bodyPatterns.any { it.containsMatchIn(combined) }) {
                return bank.canonicalName
            }
        }
        return null
    }

    /**
     * Generates a stable, deterministic group identifier from an account identity (Step 4).
     */
    fun generateGroupId(identity: FinancialAccountIdentity): String {
        val inst = identity.institutionId?.trim()?.lowercase() ?: "unknown"
        val instrument = identity.instrumentType.name.lowercase()
        val suffix = identity.accountSuffix ?: identity.cardSuffix
        return when {
            inst == "unknown" && suffix.isNullOrBlank() -> "unidentified_account"
            !suffix.isNullOrBlank() -> "${inst}_${instrument}_${suffix}"
            else -> "${inst}_${instrument}_unknown"
        }
    }
}

/**
 * Pure in-memory grouper for validated & deduplicated transaction candidates (Step 4).
 *
 * Guarantees:
 * 1. Zero Room database writes.
 * 2. Deterministic group IDs across repeated scans.
 * 3. Separate groups for different accounts and banks.
 * 4. Card and bank-account identities are never merged.
 * 5. Duplicates and rejected candidates never inflate transaction counts or totals.
 */
object FinancialAccountGrouper {

    /**
     * Groups scanned transaction candidates into stable [FinancialAccountGroup]s.
     */
    fun groupTransactions(
        scannedItems: List<ScannedTransactionItem>
    ): List<FinancialAccountGroup> {
        // Only VALID and DEDUPLICATED transactions count towards account groups
        val eligibleItems = scannedItems.filter { it.plannedDecision != DedupDecision.DUPLICATE }
        if (eligibleItems.isEmpty()) return emptyList()

        val groupMap = linkedMapOf<String, MutableList<ScannedTransactionItem>>()
        val identityMap = mutableMapOf<String, FinancialAccountIdentity>()

        for (item in eligibleItems) {
            val identity = item.accountIdentity
            val groupId = AccountIdentityExtractor.generateGroupId(identity)
            groupMap.getOrPut(groupId) { mutableListOf() }.add(item)
            if (!identityMap.containsKey(groupId)) {
                identityMap[groupId] = identity
            }
        }

        return groupMap.map { (groupId, items) ->
            val identity = identityMap[groupId] ?: FinancialAccountIdentity()
            val totalDebit = items
                .filter { it.candidate.candidate.direction == TransactionDirection.DEBIT }
                .sumOf { it.candidate.candidate.amount ?: 0.0 }
            val totalCredit = items
                .filter { it.candidate.candidate.direction == TransactionDirection.CREDIT }
                .sumOf { it.candidate.candidate.amount ?: 0.0 }

            FinancialAccountGroup(
                groupId = groupId,
                identity = identity,
                transactions = items,
                transactionCount = items.size,
                totalDebit = totalDebit,
                totalCredit = totalCredit
            )
        }.sortedBy { it.groupId }
    }
}
