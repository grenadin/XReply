package dev.grenadin.xreply

import java.text.Normalizer

object PostLength {
    const val LIMIT = 280
    private const val URL_LENGTH = 23
    private val URL = Regex("""https?://\S+|www\.\S+""", RegexOption.IGNORE_CASE)

    fun count(text: String): Int {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
        var total = 0
        var last = 0
        for (match in URL.findAll(normalized)) {
            total += countPlain(normalized.substring(last, match.range.first)) + URL_LENGTH
            last = match.range.last + 1
        }
        return total + countPlain(normalized.substring(last))
    }

    private fun countPlain(text: String): Int {
        val cps = text.codePoints().toArray()
        var total = 0
        var i = 0
        while (i < cps.size) {
            if (!startsEmoji(cps, i)) {
                total += weight(cps[i++])
                continue
            }
            val first = cps[i++]
            if (first in REGIONAL && i < cps.size && cps[i] in REGIONAL) i++
            while (i < cps.size) {
                val c = cps[i]
                i += when {
                    c == 0xFE0F || c == 0x20E3 || c in 0x1F3FB..0x1F3FF || c in 0xE0020..0xE007F -> 1
                    c == 0x200D && i + 1 < cps.size -> 2
                    else -> break
                }
            }
            total += 2
        }
        return total
    }

    private val REGIONAL = 0x1F1E6..0x1F1FF

    private fun startsEmoji(cps: IntArray, i: Int): Boolean {
        val c = cps[i]
        if (c in 0x1F000..0x1FAFF || c in 0x2600..0x27BF || c in 0x2B00..0x2BFF || c in 0x2300..0x23FF) return true
        val next = cps.getOrNull(i + 1)
        return next == 0xFE0F || next == 0x20E3
    }

    private fun weight(codePoint: Int) = when (codePoint) {
        in 0x0000..0x10FF, in 0x2000..0x200D, in 0x2010..0x201F, in 0x2032..0x2037 -> 1
        else -> 2
    }
}
