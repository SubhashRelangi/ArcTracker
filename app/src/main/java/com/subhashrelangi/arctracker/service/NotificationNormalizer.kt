package com.subhashrelangi.arctracker.service

/**
 * Pure, deterministic normalizer that converts [CapturedNotificationInfo] into [NormalizedNotification].
 *
 * Normalization Rules:
 * 1. Whitespace:
 *    - Collapses consecutive horizontal whitespace (spaces, tabs) into a single space.
 *    - Replaces \r\n and \r with \n.
 *    - Collapses consecutive empty lines into a single newline.
 *    - Trims leading and trailing whitespace per line and overall.
 * 2. Unicode:
 *    - Safely replaces non-breaking space variations (\u00A0, \u202F, \u2007) with standard spaces.
 *    - Strips zero-width invisible formatting characters (\u200B, \u200C, \u200D, \uFEFF).
 *    - Strictly preserves currency symbols (₹, $, €, £), currency codes (Rs, INR), numbers, punctuation,
 *      transaction IDs, UPI IDs, account suffixes, dates, and times.
 * 3. Text lines:
 *    - Normalizes each line independently while strictly preserving order.
 *    - Discards empty or blank lines.
 * 4. Combined Text:
 *    - Deterministically combines non-blank fields in priority order:
 *      1. normalizedTitle
 *      2. normalizedText
 *      3. normalizedBigText
 *      4. normalizedTextLines (in order)
 *      5. normalizedSubText
 *      6. normalizedSummaryText
 *      7. normalizedInfoText
 *    - Eliminates duplicate entries (case-insensitive line matching) to prevent repeating identical text.
 *    - Joins unique sections with newline "\n".
 * 5. Case Normalization:
 *    - Preserves original clean casing in normalized fields and combined text.
 *    - Provides lowercased versions for case-insensitive downstream matching.
 * 6. Non-Interpretation:
 *    - Does NOT infer financial transaction status, debit/credit, amounts, or merchants.
 */
object NotificationNormalizer {

    private val HORIZONTAL_WHITESPACE_REGEX = Regex("[ \\t\\x0B\\f]+")

    /**
     * Cleans an individual single or multi-line text string according to whitespace and unicode rules.
     * Returns null if the resulting string is blank or the input was null.
     */
    fun cleanText(text: String?): String? {
        if (text == null) return null
        if (text.isBlank()) return null

        val sanitized = text
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace('\u2007', ' ')
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        val lines = sanitized.split('\n')
            .map { line -> line.replace(HORIZONTAL_WHITESPACE_REGEX, " ").trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) return null
        return lines.joinToString("\n").trim()
    }

    /**
     * Normalizes a list of text lines independently while preserving order and filtering out blank lines.
     */
    fun cleanTextLines(lines: List<String>?): List<String> {
        if (lines.isNullOrEmpty()) return emptyList()
        val result = mutableListOf<String>()
        for (line in lines) {
            val cleaned = cleanText(line)
            if (!cleaned.isNullOrEmpty()) {
                result.add(cleaned)
            }
        }
        return result
    }

    /**
     * Normalizes a [CapturedNotificationInfo] into a [NormalizedNotification].
     *
     * @param raw The raw captured notification. If null, returns null.
     * @return [NormalizedNotification] containing clean fields, source metadata, and combined text.
     */
    fun normalize(raw: CapturedNotificationInfo?): NormalizedNotification? {
        if (raw == null) return null

        return try {
            val normTitle = cleanText(raw.title)
            val normText = cleanText(raw.text)
            val normBigText = cleanText(raw.bigText)
            val normSubText = cleanText(raw.subText)
            val normSummaryText = cleanText(raw.summaryText)
            val normInfoText = cleanText(raw.infoText)
            val normTextLines = cleanTextLines(raw.textLines)

            val combined = buildCombinedText(
                title = normTitle,
                text = normText,
                bigText = normBigText,
                textLines = normTextLines,
                subText = normSubText,
                summaryText = normSummaryText,
                infoText = normInfoText
            )

            NormalizedNotification(
                raw = raw,
                packageName = raw.packageName,
                notificationKey = raw.notificationKey,
                postTime = raw.postTime,
                category = raw.category,
                channelId = raw.channelId,
                groupKey = raw.groupKey,
                isGroup = raw.isGroup,
                isGroupSummary = raw.isGroupSummary,
                flags = raw.flags,
                isUpdate = raw.isUpdate,
                normalizedTitle = normTitle,
                normalizedText = normText,
                normalizedBigText = normBigText,
                normalizedSubText = normSubText,
                normalizedSummaryText = normSummaryText,
                normalizedInfoText = normInfoText,
                normalizedTextLines = normTextLines,
                normalizedCombinedText = combined,
                normalizedCombinedTextLower = combined.lowercase()
            )
        } catch (e: Exception) {
            // Safety fallback: never throw or crash the capture pipeline
            val fallbackCombined = listOfNotNull(raw.title, raw.text, raw.bigText).joinToString("\n")
            NormalizedNotification(
                raw = raw,
                normalizedTitle = raw.title,
                normalizedText = raw.text,
                normalizedBigText = raw.bigText,
                normalizedCombinedText = fallbackCombined,
                normalizedCombinedTextLower = fallbackCombined.lowercase()
            )
        }
    }

    /**
     * Builds deterministic combined text from fields in priority order without duplicating identical lines.
     *
     * Priority Order:
     * 1. title
     * 2. text
     * 3. bigText
     * 4. textLines (in order)
     * 5. subText
     * 6. summaryText
     * 7. infoText
     */
    fun buildCombinedText(
        title: String?,
        text: String?,
        bigText: String?,
        textLines: List<String>,
        subText: String?,
        summaryText: String?,
        infoText: String?
    ): String {
        val uniqueSections = mutableListOf<String>()
        val seenLower = mutableSetOf<String>()

        fun addPiece(piece: String?) {
            if (piece.isNullOrBlank()) return
            val lines = piece.split('\n')
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    val key = trimmed.lowercase()
                    if (!seenLower.contains(key)) {
                        seenLower.add(key)
                        uniqueSections.add(trimmed)
                    }
                }
            }
        }

        addPiece(title)
        addPiece(text)
        addPiece(bigText)
        for (line in textLines) {
            addPiece(line)
        }
        addPiece(subText)
        addPiece(summaryText)
        addPiece(infoText)

        return uniqueSections.joinToString("\n")
    }
}
