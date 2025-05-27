package com.prayag.phonetic_speech_recognizer

import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.codec.language.Soundex

class PhoneticSimilarity {
    private val soundex = Soundex()
    private val levenshtein = LevenshteinDistance()

    fun calculatePhoneticSimilarity(phrase1: String, phrase2: String): Double {
        val words1 = phrase1.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val words2 = phrase2.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }

        if (words1.isEmpty() && words2.isEmpty()) return 1.0
        if (words1.isEmpty() || words2.isEmpty()) return 0.0

        val phonetics1 = words1.map { soundex.encode(it) }
        val phonetics2 = words2.map { soundex.encode(it) }

        return calculateSequenceSimilarity(phonetics1, phonetics2)
    }

    private fun calculateSequenceSimilarity(seq1: List<String>, seq2: List<String>): Double {
        val m = seq1.size
        val n = seq2.size

        val dp = Array(m + 1) { DoubleArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                val match = calculateCodeSimilarity(seq1[i-1], seq2[j-1])

                dp[i][j] = maxOf(
                    dp[i-1][j-1] + match,  // Match
                    dp[i-1][j],            // Skip from seq1
                    dp[i][j-1]             // Skip from seq2
                )
            }
        }

        val maxLength = maxOf(m, n)
        return if (maxLength > 0) dp[m][n] / maxLength else 0.0
    }

    private fun calculateCodeSimilarity(code1: String, code2: String): Double {
        return when {
            code1 == code2 -> 1.0
            code1.isEmpty() || code2.isEmpty() -> 0.0
            else -> {
                val maxLen = maxOf(code1.length, code2.length)
                val distance = levenshtein.calculate(code1, code2)
                1.0 - (distance.toDouble() / maxLen)
            }
        }
    }
}