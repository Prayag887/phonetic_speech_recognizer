package com.prayag.phonetic_speech_recognizer

import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.codec.language.DoubleMetaphone
import org.apache.commons.text.similarity.JaroWinklerDistance
import org.apache.commons.text.similarity.LevenshteinDistance
import kotlin.math.*

data class WordMatchResult(
    val word: String,
    val phoneticCode: String,
    val isStopWord: Boolean,
    val isMetaphoneZero: Boolean,
    val bestMatch: String?,
    val bestScore: Double,
    val meetsThreshold: Boolean,
    val confidence: Double,
    val phoneticContentSimilarity: Double
)

data class WordAnalysisResult(
    val recognizedWord: String,
    val confidence: Double,
    val phoneticContentSimilarity: Double
)

class PhoneticSimilarity {
    private val doubleMetaphone = DoubleMetaphone()
    private val levenshtein = LevenshteinDistance()
    private val jaro = JaroWinklerDistance()

    // Common English stop words
    private val stopWords = setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with",
        "by", "is", "are", "was", "were", "be", "been", "have", "has", "had", "do", "does",
        "did", "will", "would", "could", "should", "may", "might", "can", "must", "shall",
        "this", "that", "these", "those", "i", "you", "he", "she", "it", "we", "they",
        "me", "him", "her", "us", "them", "my", "your", "his", "its", "our", "their"
    )

    /**
     * Main function that returns both overall similarity and word-level analysis
     */
    fun calculatePhoneticSimilarityWithWordAnalysis(phrase1: String, phrase2: String): Pair<Double, List<WordAnalysisResult>> {
        println("🔍 DEBUG INPUT:")
        println("   phrase1 (expected): '$phrase1'")
        println("   phrase2 (recognized): '$phrase2'")

        val words1 = phrase1.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val words2 = phrase2.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }

        println("   words1 count: ${words1.size} -> $words1")
        println("   words2 count: ${words2.size} -> $words2")

        if (words1.isEmpty() && words2.isEmpty()) return Pair(1.0, emptyList())
        if (words1.isEmpty() || words2.isEmpty()) return Pair(0.0, emptyList())

        // Generate phonetic codes and concatenate (removing spaces)
        val expectedPhonetic = words1.joinToString("") { word ->
            val code = doubleMetaphone.doubleMetaphone(word.replace(Regex("[^a-zA-Z]"), ""))
            if (code == "0" || code.isEmpty()) "" else code
        }

        val recognizedPhonetic = words2.joinToString("") { word ->
            val code = doubleMetaphone.doubleMetaphone(word.replace(Regex("[^a-zA-Z]"), ""))
            if (code == "0" || code.isEmpty()) "" else code
        }

        println("   expected phonetic: '$expectedPhonetic'")
        println("   recognized phonetic: '$recognizedPhonetic'")

        // Generate word analysis results for recognized words
        val wordAnalysisResults = generateWordAnalysisResults(words2, words1)

        // Calculate similarity using the space-removal approach
        val phoneticSimilarity = calculateSpacelessPhoneticSimilarity(expectedPhonetic, recognizedPhonetic)

        println("   Spaceless Phonetic Similarity: ${String.format("%.2f", phoneticSimilarity * 100)}%")

        // Set a threshold for acceptance (you can adjust this)
        val threshold = 0.8 // 80% similarity threshold

        if (phoneticSimilarity >= threshold) {
            println("   SPACELESS PHONETIC MATCH ACHIEVED")
            println("   SPEECH CORRECTION APPLIED: Converting recognized speech to expected phrase")
            println("   Corrected Output: '$phrase1'")
            return Pair(phoneticSimilarity, wordAnalysisResults)
        } else {
            println("   SPACELESS PHONETIC MATCH FAILED - Below threshold")
            return Pair(phoneticSimilarity, wordAnalysisResults)
        }
    }

    /**
     * Legacy function for backward compatibility
     */
    fun calculatePhoneticSimilarity(phrase1: String, phrase2: String): Double {
        return calculatePhoneticSimilarityWithWordAnalysis(phrase1, phrase2).first
    }

    /**
     * Calculate similarity between two spaceless phonetic strings
     */
    private fun calculateSpacelessPhoneticSimilarity(phonetic1: String, phonetic2: String): Double {
        if (phonetic1 == phonetic2) return 1.0
        if (phonetic1.isEmpty() && phonetic2.isEmpty()) return 1.0
        if (phonetic1.isEmpty() || phonetic2.isEmpty()) return 0.0

        // Primary method: Levenshtein distance
        val maxLen = maxOf(phonetic1.length, phonetic2.length)
        val distance = levenshtein.apply(phonetic1, phonetic2)
        val levenshteinSimilarity = 1.0 - (distance.toDouble() / maxLen)

        // Secondary method: Longest Common Subsequence
        val lcs = longestCommonSubsequence(phonetic1, phonetic2)
        val lcsSimilarity = (2.0 * lcs) / (phonetic1.length + phonetic2.length)

        // Tertiary method: Character overlap ratio
        val chars1 = phonetic1.toSet()
        val chars2 = phonetic2.toSet()
        val commonChars = chars1.intersect(chars2).size
        val totalChars = chars1.union(chars2).size
        val overlapSimilarity = if (totalChars > 0) commonChars.toDouble() / totalChars else 0.0

        // Bonus for identical prefixes/suffixes
        val prefixBonus = calculatePrefixSimilarity(phonetic1, phonetic2)
        val suffixBonus = calculateSuffixSimilarity(phonetic1, phonetic2)

        // Weighted combination
        val baseSimilarity = (levenshteinSimilarity * 0.5 + lcsSimilarity * 0.3 + overlapSimilarity * 0.2)
        val finalSimilarity = minOf(1.0, baseSimilarity + prefixBonus + suffixBonus)

        println("      Spaceless Similarity Breakdown:")
        println("      Levenshtein: ${String.format("%.2f", levenshteinSimilarity * 100)}%")
        println("      LCS: ${String.format("%.2f", lcsSimilarity * 100)}%")
        println("      Overlap: ${String.format("%.2f", overlapSimilarity * 100)}%")
        println("      Prefix Bonus: ${String.format("%.2f", prefixBonus * 100)}%")
        println("      Suffix Bonus: ${String.format("%.2f", suffixBonus * 100)}%")
        println("      Final: ${String.format("%.2f", finalSimilarity * 100)}%")

        return finalSimilarity
    }

    /**
     * Calculate longest common subsequence length
     */
    private fun longestCommonSubsequence(str1: String, str2: String): Int {
        val m = str1.length
        val n = str2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                if (str1[i - 1] == str2[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                } else {
                    dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        return dp[m][n]
    }

    /**
     * Calculate bonus for matching prefixes
     */
    private fun calculatePrefixSimilarity(str1: String, str2: String): Double {
        val minLen = minOf(str1.length, str2.length)
        if (minLen == 0) return 0.0

        var matchCount = 0
        for (i in 0 until minLen) {
            if (str1[i] == str2[i]) {
                matchCount++
            } else {
                break
            }
        }

        // Bonus increases with prefix length, but caps at reasonable amount
        return minOf(0.2, (matchCount.toDouble() / minLen) * 0.2)
    }

    /**
     * Calculate bonus for matching suffixes
     */
    private fun calculateSuffixSimilarity(str1: String, str2: String): Double {
        val minLen = minOf(str1.length, str2.length)
        if (minLen == 0) return 0.0

        var matchCount = 0
        for (i in 1..minLen) {
            if (str1[str1.length - i] == str2[str2.length - i]) {
                matchCount++
            } else {
                break
            }
        }

        // Bonus increases with suffix length, but caps at reasonable amount
        return minOf(0.15, (matchCount.toDouble() / minLen) * 0.15)
    }

    /**
     * Generate word analysis results for each recognized word
     */
    private fun generateWordAnalysisResults(
        recognizedWords: List<String>,
        expectedWords: List<String>
    ): List<WordAnalysisResult> {
        val results = mutableListOf<WordAnalysisResult>()

        for (recognizedWord in recognizedWords) {
            val recognizedPhonetic = doubleMetaphone.doubleMetaphone(recognizedWord.replace(Regex("[^a-zA-Z]"), ""))

            // Find best match among expected words
            var bestMatch = ""
            var bestScore = 0.0

            for (expectedWord in expectedWords) {
                val expectedPhonetic = doubleMetaphone.doubleMetaphone(expectedWord.replace(Regex("[^a-zA-Z]"), ""))

                // Calculate various similarity metrics
                val phoneticScore = calculateMetaphoneSimilarity(recognizedPhonetic, expectedPhonetic)
                val editScore = calculateWordEditSimilarity(recognizedWord, expectedWord)
                val jaroScore = jaro.apply(recognizedWord.lowercase(), expectedWord.lowercase())

                val combinedScore = (phoneticScore * 0.5 + editScore * 0.3 + jaroScore * 0.2)

                if (combinedScore > bestScore) {
                    bestScore = combinedScore
                    bestMatch = expectedWord
                }
            }

            // Calculate confidence and phonetic content similarity
            val confidence = if (bestMatch.isNotEmpty()) {
                calculateWordConfidence(bestMatch, recognizedWord, bestScore)
            } else {
                0.3
            }

            val phoneticContentSimilarity = if (bestMatch.isNotEmpty()) {
                calculatePurePhoneticSimilarity(bestMatch, recognizedWord)
            } else {
                0.2
            }

            results.add(WordAnalysisResult(
                recognizedWord = recognizedWord,
                confidence = confidence,
                phoneticContentSimilarity = phoneticContentSimilarity
            ))
        }

        return results
    }

    /**
     * Calculate confidence using combination of all algorithms
     */
    private fun calculateWordConfidence(
        expectedWord: String,
        recognizedWord: String,
        phoneticMatch: Double
    ): Double {
        // Exact match gets full confidence
        if (expectedWord.equals(recognizedWord, ignoreCase = true)) {
            return 1.0
        }

        // Calculate various similarity metrics
        val phoneticSimilarity = phoneticMatch
        val editDistanceSimilarity = calculateWordEditSimilarity(expectedWord, recognizedWord)
        val jaroWinklerSimilarity = jaro.apply(expectedWord.lowercase(), recognizedWord.lowercase())

        // Weight the different algorithms
        val confidence = (
                phoneticSimilarity * 0.5 +      // Phonetic matching is most important
                        jaroWinklerSimilarity * 0.3 +   // String similarity
                        editDistanceSimilarity * 0.2     // Edit distance
                )

        // Apply boost for stop words (they're often recognized correctly phonetically)
        val finalConfidence = if (isStopWord(expectedWord) || isStopWord(recognizedWord)) {
            minOf(1.0, confidence + 0.1)
        } else {
            confidence
        }

        return maxOf(0.0, minOf(1.0, finalConfidence))
    }

    /**
     * Calculate pure phonetic content similarity focusing only on pronunciation
     */
    private fun calculatePurePhoneticSimilarity(expectedWord: String, recognizedWord: String): Double {
        // Exact match
        if (expectedWord.equals(recognizedWord, ignoreCase = true)) {
            return 1.0
        }

        val expectedPhonetic = doubleMetaphone.doubleMetaphone(expectedWord.replace(Regex("[^a-zA-Z]"), ""))
        val recognizedPhonetic = doubleMetaphone.doubleMetaphone(recognizedWord.replace(Regex("[^a-zA-Z]"), ""))

        // Handle metaphone [0] codes
        if (expectedPhonetic == "0" && recognizedPhonetic == "0") {
            return 0.8 // Both are non-phonetic, give decent similarity
        }
        if (expectedPhonetic == "0" || recognizedPhonetic == "0") {
            return 0.3 // One is non-phonetic, lower similarity
        }

        // Pure phonetic similarity
        val phoneticSim = calculateMetaphoneSimilarity(expectedPhonetic, recognizedPhonetic)
        return phoneticSim
    }

    private fun calculateMetaphoneSimilarity(code1: String, code2: String): Double {
        return when {
            code1 == code2 -> 1.0
            code1.isEmpty() || code2.isEmpty() -> 0.0
            else -> {
                val maxLen = maxOf(code1.length, code2.length)
                val distance = levenshtein.apply(code1, code2)

                // Base similarity using Levenshtein distance
                val baseSimilarity = maxOf(0.0, 1.0 - (distance.toDouble() / maxLen))

                // Bonus for shared starting sound (important for phonetic similarity)
                val startBonus = if (code1.isNotEmpty() && code2.isNotEmpty() && code1[0] == code2[0]) 0.15 else 0.0

                // Bonus for shared ending sound
                val endBonus = if (code1.isNotEmpty() && code2.isNotEmpty() && code1.last() == code2.last()) 0.1 else 0.0

                // Containment bonus - if shorter code is contained in longer one
                val longer = if (code1.length > code2.length) code1 else code2
                val shorter = if (code1.length <= code2.length) code1 else code2
                val containmentBonus = if (longer.contains(shorter) && shorter.length >= 2) 0.2 else 0.0

                // Apply bonuses but cap at 1.0
                minOf(1.0, baseSimilarity + startBonus + endBonus + containmentBonus)
            }
        }
    }

    /**
     * Calculate word-level edit distance similarity
     */
    private fun calculateWordEditSimilarity(word1: String, word2: String): Double {
        val maxLen = maxOf(word1.length, word2.length)
        if (maxLen == 0) return 1.0

        val distance = levenshtein.apply(word1.lowercase(), word2.lowercase())
        return 1.0 - (distance.toDouble() / maxLen)
    }

    private fun isStopWord(word: String): Boolean {
        return word.lowercase().replace(Regex("[^a-zA-Z]"), "") in stopWords
    }
}