package com.prayag.phonetic_speech_recognizer

import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.codec.language.DoubleMetaphone
import org.apache.commons.text.similarity.JaroWinklerDistance
import org.apache.commons.text.similarity.LevenshteinDistance
import kotlin.math.*

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

    // Acoustic similarity mappings for common confusions
    private val acousticSimilarities = mapOf(
        // Consonant confusions
        "J" to listOf("K", "G", "CH"),
        "K" to listOf("J", "G", "C"),
        "G" to listOf("J", "K", "C"),
        "P" to listOf("B", "F"),
        "B" to listOf("P", "V"),
        "T" to listOf("D", "TH"),
        "D" to listOf("T", "TH"),
        "S" to listOf("Z", "SH", "TH"),
        "Z" to listOf("S", "SH"),
        "F" to listOf("V", "TH", "P"),
        "V" to listOf("F", "B"),
        "M" to listOf("N"),
        "N" to listOf("M"),

        // Vowel-like confusions
        "A" to listOf("E", "I"),
        "E" to listOf("A", "I"),
        "I" to listOf("A", "E"),
        "O" to listOf("U"),
        "U" to listOf("O"),

        // Double consonants
        "MP" to listOf("M", "NP"),
        "MS" to listOf("MZ", "NS"),
        "PS" to listOf("S", "FS"),
    )

    fun calculatePhoneticSimilarity(phrase1: String, phrase2: String): Double {
        val words1 = phrase1.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val words2 = phrase2.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }

        if (words1.isEmpty() && words2.isEmpty()) return 1.0
        if (words1.isEmpty() || words2.isEmpty()) return 0.0

        // Calculate different similarity metrics
        val metaphoneSim = calculateDynamicPhoneticSimilarity(words1, words2)
        val acousticSim = calculateAcousticSimilarity(words1, words2)
        val editSim = calculateEditDistanceSimilarity(phrase1, phrase2)
        val wordOrderSim = calculateWordOrderSimilarity(words1, words2)

        println("🔍 Enhanced Metric Breakdown:")
        println("   Metaphone (Dynamic): ${String.format("%.2f", metaphoneSim * 100)}%")
        println("   Acoustic Similarity: ${String.format("%.2f", acousticSim * 100)}%")
        println("   Edit Distance: ${String.format("%.2f", editSim * 100)}%")
        println("   Word Order: ${String.format("%.2f", wordOrderSim * 100)}%")

        // Show detailed analysis
        showDetailedPhoneticBreakdown(words1, words2)

        // Enhanced weighted combination
//        val finalScore = (metaphoneSim * 0.6 + acousticSim * 0.3 + editSim * 0.1)

        val finalScore = acousticSim

        return minOf(1.0, finalScore)
    }

    private fun calculateAcousticSimilarity(words1: List<String>, words2: List<String>): Double {
        if (words1.isEmpty() && words2.isEmpty()) return 1.0
        if (words1.isEmpty() || words2.isEmpty()) return 0.0

        val metaphone1 = words1.map { doubleMetaphone.doubleMetaphone(it) }
        val metaphone2 = words2.map { doubleMetaphone.doubleMetaphone(it) }

        val matched2 = mutableSetOf<Int>()
        var totalScore = 0.0

        for (i in metaphone1.indices) {
            val word1 = words1[i]
            val isStopWord = isStopWord(word1)

            var bestScore = 0.0
            var bestMatch = -1

            // Find best acoustic match
            for (j in metaphone2.indices) {
                if (j in matched2) continue

                val acousticScore = calculateAcousticCodeSimilarity(metaphone1[i], metaphone2[j])
                if (acousticScore > bestScore) {
                    bestScore = acousticScore
                    bestMatch = j
                }
            }

            val threshold = if (isStopWord) 0.4 else 0.6
            if (bestMatch != -1 && bestScore > threshold) {
                matched2.add(bestMatch)
                val weight = if (isStopWord) 0.3 else 1.0
                totalScore += bestScore * weight
            } else if (isStopWord) {
                totalScore += 0.3
            }
        }

        val contentWords = words1.count { !isStopWord(it) }
        val stopWordCount = words1.size - contentWords
        val weightedTotal = contentWords + (stopWordCount * 0.3)

        return if (weightedTotal > 0) {
            minOf(1.0, totalScore / weightedTotal)
        } else {
            0.0
        }
    }

    private fun calculateAcousticCodeSimilarity(code1: String, code2: String): Double {
        if (code1 == code2) return 1.0
        if (code1.isEmpty() || code2.isEmpty()) return 0.0

        // Start with basic edit distance
        val maxLen = maxOf(code1.length, code2.length)
        val editDistance = levenshtein.apply(code1, code2)
        var baseScore = maxOf(0.0, 1.0 - (editDistance.toDouble() / maxLen))

        // Apply acoustic similarity bonuses
        var acousticBonus = 0.0
        val chars1 = code1.toCharArray()
        val chars2 = code2.toCharArray()

        for (i in chars1.indices) {
            for (j in chars2.indices) {
                val char1 = chars1[i].toString()
                val char2 = chars2[j].toString()

                if (areAcousticallySimilar(char1, char2)) {
                    acousticBonus += 0.1
                }
            }
        }

        // Check for common patterns
        acousticBonus += checkCommonPatterns(code1, code2)

        // Combine base score with acoustic bonuses
        val finalScore = baseScore + (acousticBonus * 0.3)
        return minOf(1.0, finalScore)
    }

    private fun areAcousticallySimilar(char1: String, char2: String): Boolean {
        if (char1 == char2) return true

        return acousticSimilarities[char1]?.contains(char2) == true ||
                acousticSimilarities[char2]?.contains(char1) == true
    }

    private fun checkCommonPatterns(code1: String, code2: String): Double {
        var bonus = 0.0

        // Check for common ending patterns
        val endingPatterns = mapOf(
            "MS" to listOf("MZ", "NS", "NZ"),
            "PS" to listOf("S", "FS", "BS"),
            "MP" to listOf("M", "NP", "MB"),
            "ST" to listOf("S", "T", "SD"),
            "NT" to listOf("N", "ND", "MT")
        )

        for ((pattern, alternatives) in endingPatterns) {
            if (code1.endsWith(pattern) && alternatives.any { code2.endsWith(it) }) {
                bonus += 0.2
            }
            if (code2.endsWith(pattern) && alternatives.any { code1.endsWith(it) }) {
                bonus += 0.2
            }
        }

        // Check for vowel sound similarities
        val vowelPatterns = listOf("A", "E", "I", "O", "U")
        for (vowel in vowelPatterns) {
            if (code1.contains(vowel) && code2.contains(vowel)) {
                bonus += 0.05
            }
        }

        return bonus
    }

    private fun calculateDynamicPhoneticSimilarity(words1: List<String>, words2: List<String>): Double {
        if (words1.isEmpty() && words2.isEmpty()) return 1.0
        if (words1.isEmpty() || words2.isEmpty()) return 0.0

        val metaphone1 = words1.map { doubleMetaphone.doubleMetaphone(it) }
        val metaphone2 = words2.map { doubleMetaphone.doubleMetaphone(it) }

        val matched2 = mutableSetOf<Int>()
        var totalScore = 0.0

        for (i in metaphone1.indices) {
            val word1 = words1[i]
            val isStopWord = isStopWord(word1)

            var bestScore = 0.0
            var bestMatch = -1
            var matchedWords = 1
            var matchedPhrase = ""

            // Try single word match
            for (j in metaphone2.indices) {
                if (j in matched2) continue

                val similarity = calculateMetaphoneSimilarity(metaphone1[i], metaphone2[j])
                if (similarity > bestScore) {
                    bestScore = similarity
                    bestMatch = j
                    matchedWords = 1
                    matchedPhrase = words2[j]
                }
            }

            // Try multi-word combinations (for compound sounds)
            for (j in metaphone2.indices) {
                if (j in matched2) continue

                if (j + 1 < metaphone2.size && (j + 1) !in matched2) {
                    val combinedPhonetic = metaphone2[j] + metaphone2[j + 1]
                    val similarity = calculateMetaphoneSimilarity(metaphone1[i], combinedPhonetic)
                    if (similarity > bestScore) {
                        bestScore = similarity
                        bestMatch = j
                        matchedWords = 2
                        matchedPhrase = "${words2[j]} ${words2[j + 1]}"
                    }
                }
            }

            val threshold = if (isStopWord) 0.5 else 0.7
            if (bestMatch != -1 && bestScore > threshold) {
                for (k in bestMatch until (bestMatch + matchedWords)) {
                    matched2.add(k)
                }

                val weight = if (isStopWord) 0.3 else 1.0
                totalScore += bestScore * weight
            } else if (isStopWord) {
                totalScore += 0.3
            }
        }

        val contentWords = words1.count { !isStopWord(it) }
        val stopWordCount = words1.size - contentWords
        val weightedTotal = contentWords + (stopWordCount * 0.3)

        return if (weightedTotal > 0) {
            minOf(1.0, totalScore / weightedTotal)
        } else {
            0.0
        }
    }

    private fun showDetailedPhoneticBreakdown(words1: List<String>, words2: List<String>) {
        println("🔍 Detailed Phonetic Analysis:")

        val metaphone1 = words1.map { doubleMetaphone.doubleMetaphone(it) }
        val metaphone2 = words2.map { doubleMetaphone.doubleMetaphone(it) }

        println("📋 Expected words (phrase1):")
        for (i in words1.indices) {
            val word1 = words1[i]
            val phone1 = metaphone1[i]
            val isStopWordFlag = isStopWord(word1)
            val wordType = if (isStopWordFlag) " (stop)" else ""
            println("   Expected: '$word1' → [$phone1]$wordType")
        }

        println("📋 Recognized words (phrase2):")
        for (j in words2.indices) {
            val word2 = words2[j]
            val phone2 = metaphone2[j]
            val isStopWordFlag = isStopWord(word2)
            val wordType = if (isStopWordFlag) " (stop)" else ""
            println("   Recognized: '$word2' → [$phone2]$wordType")
        }

        println("🔍 Word-by-Word Analysis:")
        val matched2 = mutableSetOf<Int>()

        for (i in words1.indices) {
            val word1 = words1[i]
            val phone1 = metaphone1[i]
            val isStopWordFlag = isStopWord(word1)

            var bestMatch = -1
            var bestPhoneticScore = 0.0
            var bestAcousticScore = 0.0
            var matchedWord = ""

            for (j in words2.indices) {
                if (j in matched2) continue

                val word2 = words2[j]
                val phone2 = metaphone2[j]

                val phoneticSim = calculateMetaphoneSimilarity(phone1, phone2)
                val acousticSim = calculateAcousticCodeSimilarity(phone1, phone2)

                if (phoneticSim > bestPhoneticScore ||
                    (phoneticSim == bestPhoneticScore && acousticSim > bestAcousticScore)) {
                    bestPhoneticScore = phoneticSim
                    bestAcousticScore = acousticSim
                    bestMatch = j
                    matchedWord = word2
                }
            }

            if (bestMatch != -1) {
                matched2.add(bestMatch)
                val phone2 = metaphone2[bestMatch]

                val status = when {
                    bestPhoneticScore >= 0.95 -> "✅ EXACT"
                    bestPhoneticScore >= 0.8 -> "🟡 SIMILAR"
                    bestAcousticScore >= 0.7 -> "🟠 ACOUSTIC"
                    else -> "🔴 WEAK"
                }

                val wordType = if (isStopWordFlag) " (stop)" else ""
                println("   • '$word1' [$phone1] ↔ '$matchedWord' [$phone2] → $status")
                println("     Phonetic: ${String.format("%.0f", bestPhoneticScore * 100)}%, Acoustic: ${String.format("%.0f", bestAcousticScore * 100)}%$wordType")
            } else {
                val wordType = if (isStopWordFlag) "🔹 UNMATCHED STOP" else "❌ NO MATCH"
                println("   • '$word1' [$phone1] → $wordType")
            }
        }

        // Show unmatched recognized words
        for (j in words2.indices) {
            if (j !in matched2) {
                val word2 = words2[j]
                val phone2 = metaphone2[j]
                val isStopWordFlag = isStopWord(word2)
                val wordType = if (isStopWordFlag) "🔹 EXTRA STOP" else "➕ EXTRA WORD"
                println("   • $wordType: '$word2' [$phone2]")
            }
        }
    }

    private fun isStopWord(word: String): Boolean {
        return word.lowercase().replace(Regex("[^a-zA-Z]"), "") in stopWords
    }

    private fun calculateMetaphoneSimilarity(code1: String, code2: String): Double {
        return when {
            code1 == code2 -> 1.0
            code1.isEmpty() || code2.isEmpty() -> 0.0
            else -> {
                val maxLen = maxOf(code1.length, code2.length)
                val distance = levenshtein.apply(code1, code2)
                maxOf(0.0, 1.0 - (distance.toDouble() / maxLen))
            }
        }
    }

    private fun calculateEditDistanceSimilarity(phrase1: String, phrase2: String): Double {
        val maxLen = maxOf(phrase1.length, phrase2.length)
        if (maxLen == 0) return 1.0

        val distance = levenshtein.apply(phrase1.lowercase(), phrase2.lowercase())
        return 1.0 - (distance.toDouble() / maxLen)
    }

    private fun calculateWordOrderSimilarity(words1: List<String>, words2: List<String>): Double {
        if (words1.isEmpty() || words2.isEmpty()) return 0.0

        val cleanWords1 = words1.map { it.lowercase().replace(Regex("[^a-zA-Z]"), "") }.filter { it.isNotEmpty() }
        val cleanWords2 = words2.map { it.lowercase().replace(Regex("[^a-zA-Z]"), "") }.filter { it.isNotEmpty() }

        val set1 = cleanWords1.toSet()
        val set2 = cleanWords2.toSet()

        val intersection = set1.intersect(set2).size
        val union = set1.union(set2).size

        return if (union > 0) intersection.toDouble() / union else 0.0
    }
}