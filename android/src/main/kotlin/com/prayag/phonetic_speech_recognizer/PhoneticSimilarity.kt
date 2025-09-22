package com.prayag.phonetic_speech_recognizer

import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.codec.language.DoubleMetaphone
import org.apache.commons.text.similarity.JaroWinklerDistance
import org.apache.commons.text.similarity.LevenshteinDistance
import kotlin.math.*
import kotlin.plus

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
        "the", "a", "an", "or", "but", "in", "on", "at", "to", "for", "of", "with",
        "by", "is", "are", "was", "were", "be", "been", "have", "has", "had", "do", "does",
        "did", "will", "would", "could", "should", "may", "might", "can", "must", "shall",
        "this", "that", "these", "those", "i", "you", "he", "she", "it", "we", "they",
        "me", "him", "her", "us", "them", "my", "your", "his", "its", "our", "their"
    )

    // Acoustic similarity mappings for common confusions
    private val acousticSimilarities = mapOf(
        "J" to listOf("K", "G", "CH"),
        "K" to listOf("J", "G", "C"),
        "G" to listOf("J", "K", "C"),
        "P" to listOf("B", "F"),
        "B" to listOf("P", "V"),
        "T" to listOf("D", "TH"),
        "D" to listOf("T", "TH"),
        "X" to listOf("S", "SH", "CH"),
        "S" to listOf("X", "SH", "CH", "Z", "SH", "TH"),
        "Z" to listOf("S", "SH"),
        "F" to listOf("V", "TH", "P"),
        "V" to listOf("F", "B"),
        "M" to listOf("N"),
        "N" to listOf("M"),
        "A" to listOf("E", "I"),
        "E" to listOf("A", "I"),
        "I" to listOf("A", "E"),
        "O" to listOf("U"),
        "U" to listOf("O"),
        "MP" to listOf("M", "NP"),
        "MS" to listOf("MZ", "NS"),
        "PS" to listOf("S", "FS"),
    )

    private val directSpeechCorrections = mapOf(
        "ccs" to "she sees",
        "cc" to "she sees",
        "cs" to "she sees",
        "c" to "see",
//        "processor" to "brushes her"
    )

    // Add this function to pre-process the recognized phrase
    private fun applyDirectSpeechCorrection(phrase: String): String {
        val words = phrase.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val correctedWords = words.map { word ->
            directSpeechCorrections[word.lowercase()] ?: word
        }
        return correctedWords.joinToString(" ")
    }

    /**
     * Main function that returns both overall similarity and word-level analysis
     */
    fun calculatePhoneticSimilarityWithWordAnalysis(phrase1: String, phrase2: String): Pair<Double, List<WordAnalysisResult>> {
        println("--------- DEBUG INPUT:")
        println("   phrase1 (expected): '$phrase1'")
        println("   phrase2 (recognized): '$phrase2'")

        // Apply direct speech correction to the recognized phrase
        val correctedPhrase2 = applyDirectSpeechCorrection(phrase2)
        println("   corrected phrase2: '$correctedPhrase2'")

        val words1 = phrase1.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val words2 = correctedPhrase2.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }

        println("   words1 count: ${words1.size} -> $words1")
        println("   words2 count: ${words2.size} -> $words2")

        if (words1.isEmpty() && words2.isEmpty()) return Pair(1.0, emptyList())
        if (words1.isEmpty() || words2.isEmpty()) return Pair(0.0, emptyList())

        // Analyze per-word accuracy with enhanced results
        val wordResults = analyzePerWordAccuracyEnhanced(words1, words2)

        // Generate word analysis results for recognized words
        val wordAnalysisResults = generateWordAnalysisResults(words2, wordResults, words1)

        // Check if all content words meet threshold
        val contentWordResults = wordResults.filter { !it.isMetaphoneZero }
        val failedWords = contentWordResults.filter { !it.meetsThreshold }

        println("--------- PER-WORD ACCURACY ANALYSIS:")
        println("   Required threshold: 0% for all words (as requested)")
        println("   Total content words: ${contentWordResults.size}")
        println("   Words meeting threshold: ${contentWordResults.count { it.meetsThreshold }}")
        println("   Words failing threshold: ${failedWords.size}")

        if (failedWords.isNotEmpty()) {
            println("    FAILED WORDS:")
            failedWords.forEach { result ->
                val matchInfo = result.bestMatch?.let { "→ '$it'" } ?: "→ NO MATCH"
                println("      • '${result.word}' [${result.phoneticCode}] $matchInfo (${String.format("%.1f", result.bestScore * 100)}%)")
            }
            println("    OVERALL RESULT: REJECTED - Not all words meet threshold")
            return Pair(0.0, wordAnalysisResults)
        }

        // Calculate overall metrics
        val metaphoneSim = calculateDynamicPhoneticSimilarity(words1, words2)
        val acousticSim = calculateAcousticSimilarity(words1, words2)
        val editSim = calculateEditDistanceSimilarity(phrase1, correctedPhrase2)
        val wordOrderSim = calculateWordOrderSimilarity(words1, words2)

        println("   Enhanced Metric Breakdown:")
        println("   Metaphone (Dynamic): ${String.format("%.2f", metaphoneSim * 100)}%")
        println("   Acoustic Similarity: ${String.format("%.2f", acousticSim * 100)}%")
        println("   Edit Distance: ${String.format("%.2f", editSim * 100)}%")
        println("   Word Order: ${String.format("%.2f", wordOrderSim * 100)}%")

        showDetailedPhoneticBreakdown(words1, words2)

        val finalScore = if (acousticSim > 0.90) {
            acousticSim
        } else {
            metaphoneSim * 0.2 + acousticSim * 0.3 + wordOrderSim * 0.5
        }


        println("   ALL WORDS MEET THRESHOLD")
        println("   SPEECH CORRECTION APPLIED: Converting recognized speech to expected phrase")
        println("   Corrected Output: '$phrase1'")

        return Pair(minOf(1.0, finalScore), wordAnalysisResults)
    }


    /**
     * Legacy function for backward compatibility
     */
    fun calculatePhoneticSimilarity(phrase1: String, phrase2: String): Double {
        return calculatePhoneticSimilarityWithWordAnalysis(phrase1, phrase2).first
    }

    /**
     * Generate word analysis results for each recognized word
     */
    private fun generateWordAnalysisResults(
        recognizedWords: List<String>,
        wordResults: List<WordMatchResult>,
        expectedWords: List<String>
    ): List<WordAnalysisResult> {
        val results = mutableListOf<WordAnalysisResult>()
        val usedExpectedWords = mutableSetOf<Int>()

        // Create reverse mapping from recognized words to expected words
        val recognizedToExpectedMap = mutableMapOf<Int, Int>()

        for (i in wordResults.indices) {
            val result = wordResults[i]
            if (result.bestMatch != null) {
                // Find which recognized word(s) this expected word matched with
                val matchedWords = result.bestMatch.split(" ")
                for (j in recognizedWords.indices) {
                    if (matchedWords.contains(recognizedWords[j]) && j !in recognizedToExpectedMap) {
                        recognizedToExpectedMap[j] = i
                        usedExpectedWords.add(i)
                        break
                    }
                }
            }
        }

        // Generate results for each recognized word
        for (j in recognizedWords.indices) {
            val recognizedWord = recognizedWords[j]
            val recognizedPhonetic = doubleMetaphone.doubleMetaphone(recognizedWord)

            if (j in recognizedToExpectedMap) {
                // This recognized word has a match in expected words
                val matchedResult = wordResults[recognizedToExpectedMap[j]!!]

                // Calculate confidence (combination of all algorithms)
                val confidence = calculateWordConfidence(
                    expectedWord = matchedResult.word,
                    recognizedWord = recognizedWord,
                    phoneticMatch = matchedResult.bestScore
                )

                // Calculate pure phonetic content similarity
                val phoneticContentSimilarity = calculatePurePhoneticSimilarity(
                    expectedWord = matchedResult.word,
                    recognizedWord = recognizedWord
                )

                results.add(WordAnalysisResult(
                    recognizedWord = recognizedWord,
                    confidence = confidence,
                    phoneticContentSimilarity = phoneticContentSimilarity
                ))
            } else {
                // This recognized word doesn't match any expected word
                // Find the best possible match for confidence calculation
                var bestExpectedMatch = ""
                var bestPhoneticScore = 0.0

                for (k in expectedWords.indices) {
                    if (k in usedExpectedWords) continue

                    val expectedPhonetic = doubleMetaphone.doubleMetaphone(expectedWords[k])
                    val phoneticScore = calculateMetaphoneSimilarity(recognizedPhonetic, expectedPhonetic)

                    if (phoneticScore > bestPhoneticScore) {
                        bestPhoneticScore = phoneticScore
                        bestExpectedMatch = expectedWords[k]
                    }
                }

                val confidence = if (bestExpectedMatch.isNotEmpty()) {
                    calculateWordConfidence(bestExpectedMatch, recognizedWord, bestPhoneticScore)
                } else {
                    0.3 // Base confidence for unmatched words
                }

                val phoneticContentSimilarity = if (bestExpectedMatch.isNotEmpty()) {
                    calculatePurePhoneticSimilarity(bestExpectedMatch, recognizedWord)
                } else {
                    0.2 // Low similarity for unmatched words
                }

                results.add(WordAnalysisResult(
                    recognizedWord = recognizedWord,
                    confidence = confidence,
                    phoneticContentSimilarity = phoneticContentSimilarity
                ))
            }
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
        val acousticSimilarity = calculateWordAcousticSimilarity(expectedWord, recognizedWord)

        // Weight the different algorithms
        val confidence = (
                phoneticSimilarity * 0.35 +      // Phonetic matching is most important
                        acousticSimilarity * 0.25 +      // Acoustic confusion patterns
                        jaroWinklerSimilarity * 0.25 +   // String similarity
                        editDistanceSimilarity * 0.15     // Edit distance
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

        val expectedPhonetic = doubleMetaphone.doubleMetaphone(expectedWord)
        val recognizedPhonetic = doubleMetaphone.doubleMetaphone(recognizedWord)

        // Handle metaphone [0] codes
        if (expectedPhonetic == "0" && recognizedPhonetic == "0") {
            return 0.8 // Both are non-phonetic, give decent similarity
        }
        if (expectedPhonetic == "0" || recognizedPhonetic == "0") {
            return 0.3 // One is non-phonetic, lower similarity
        }

        // Pure phonetic similarity
        val phoneticSim = calculateMetaphoneSimilarity(expectedPhonetic, recognizedPhonetic)

        // Add acoustic similarity for pronunciation confusions
        val acousticSim = calculateAcousticCodeSimilarity(expectedPhonetic, recognizedPhonetic)

        // Focus on pronunciation similarity with acoustic boost
        val pronunciationSimilarity = phoneticSim * 0.7 + acousticSim * 0.3

        // Special handling for common pronunciation patterns
        val pronunciationBoost = checkCommonPronunciationPatterns(expectedWord, recognizedWord)

        return minOf(1.0, pronunciationSimilarity + pronunciationBoost)
    }

    /**
     * Check for common pronunciation patterns and confusions
     */
    private fun checkCommonPronunciationPatterns(expected: String, recognized: String): Double {
        val exp = expected.lowercase()
        val rec = recognized.lowercase()
        var boost = 0.0

        // Common pronunciation confusions
        val pronunciationPairs = listOf(
            Pair("she", "c"),
            Pair("c", "she"),
            Pair("she sees", "ccs"),
            Pair("ccs", "she sees"),
            Pair("she sees", "cc s"),
            Pair("cc s", "she sees"),
            Pair("she sees", "c cs"),
            Pair("c cs", "she sees"),
            Pair("c c", "she see"),
            Pair("she see", "c c"),
            Pair("she", "sea"),
            Pair("sea", "she"),
            Pair("sheep", "she"),
            Pair("she", "sheep"),
            Pair("seas", "she"),
            Pair("she", "seas"),
            Pair("see", "c"),
            Pair("c", "see"),
            Pair("she", "see"),
            Pair("see", "she"),
            Pair("to", "two"),
            Pair("two", "to"),
            Pair("too", "to"),
            Pair("to", "too"),
            Pair("there", "their"),
            Pair("their", "there"),
            Pair("where", "wear"),
            Pair("wear", "where"),
            Pair("for", "four"),
            Pair("four", "for"),
            Pair("one", "won"),
            Pair("won", "one"),
            Pair("know", "no"),
            Pair("no", "know"),
            Pair("right", "write"),
            Pair("write", "right"),
            Pair("night", "knight"),
            Pair("knight", "night"),
            Pair("processor", "brushes her"),
            Pair("process or", "brushes her"),
            Pair("brushes her", "processor"),
            Pair("brushes her", "process or"),
        )

        for ((word1, word2) in pronunciationPairs) {
            if ((exp == word1 && rec == word2) || (exp == word2 && rec == word1)) {
                boost += 0.3 // Strong pronunciation similarity
                break
            }
            if ((exp.contains(word1) && rec.contains(word2)) ||
                (exp.contains(word2) && rec.contains(word1))) {
                boost += 0.1 // Partial pronunciation similarity
            }
        }

        // Similar starting sounds
        if (exp.isNotEmpty() && rec.isNotEmpty() &&
            calculateMetaphoneSimilarity(
                doubleMetaphone.doubleMetaphone(exp.take(2)),
                doubleMetaphone.doubleMetaphone(rec.take(2))
            ) > 0.8) {
            boost += 0.05
        }

        return boost
    }

    /**
     * Calculate word-level acoustic similarity
     */
    private fun calculateWordAcousticSimilarity(word1: String, word2: String): Double {
        val phone1 = doubleMetaphone.doubleMetaphone(word1)
        val phone2 = doubleMetaphone.doubleMetaphone(word2)
        return calculateAcousticCodeSimilarity(phone1, phone2)
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

    private fun analyzePerWordAccuracyEnhanced(words1: List<String>, words2: List<String>): List<WordMatchResult> {
        val metaphone1 = words1.map { doubleMetaphone.doubleMetaphone(it) }
        val metaphone2 = words2.map { doubleMetaphone.doubleMetaphone(it) }
        val matched2 = mutableSetOf<Int>()
        val results = mutableListOf<WordMatchResult>()

        for (i in words1.indices) {
            val word1 = words1[i]
            val phone1 = metaphone1[i]
            val isStopWord = isStopWord(word1)
            val isMetaphoneZero = phone1 == "0" || phone1.isEmpty()

            var bestScore = 0.0
            var bestMatch: String? = null
            var bestMatchIndex = -1
            var matchedWords = 1

            // Try single word matches
            for (j in words2.indices) {
                if (j in matched2) continue

                val combinedScore = calculateCombinedSimilarity(phone1, metaphone2[j])
                if (combinedScore > bestScore) {
                    bestScore = combinedScore
                    bestMatch = words2[j]
                    bestMatchIndex = j
                    matchedWords = 1
                }
            }

            // Try multi-word combinations for better phonetic matching
            for (j in words2.indices) {
                if (j in matched2) continue

                // Try 2-word combinations
                if (j + 1 < words2.size && (j + 1) !in matched2) {
                    val combinedPhonetic = metaphone2[j] + metaphone2[j + 1]
                    val combinedScore = calculateCombinedSimilarity(phone1, combinedPhonetic)
                    if (combinedScore > bestScore) {
                        bestScore = combinedScore
                        bestMatch = "${words2[j]} ${words2[j + 1]}"
                        bestMatchIndex = j
                        matchedWords = 2
                    }
                }

                // Try 3-word combinations for complex phonetic patterns
                if (j + 2 < words2.size && (j + 1) !in matched2 && (j + 2) !in matched2) {
                    val combinedPhonetic = metaphone2[j] + metaphone2[j + 1] + metaphone2[j + 2]
                    val combinedScore = calculateCombinedSimilarity(phone1, combinedPhonetic)
                    if (combinedScore > bestScore) {
                        bestScore = combinedScore
                        bestMatch = "${words2[j]} ${words2[j + 1]} ${words2[j + 2]}"
                        bestMatchIndex = j
                        matchedWords = 3
                    }
                }
            }

            // Mark matched words as used
            if (bestMatchIndex != -1) {
                for (k in bestMatchIndex until (bestMatchIndex + matchedWords)) {
                    matched2.add(k)
                }
            }

            val isSingleWordCase = words1.size == 1 || words2.size == 1

            // Determine if word meets threshold
            val threshold = when {
                isSingleWordCase -> 0.0 // Single word cases are automatically accepted
                isMetaphoneZero -> 0.0 // Metaphone [0] words are automatically accepted
                isStopWord -> 0.0 // Lower threshold for stop words (for now its 0, but if needed then put 0.6)
                else -> 0.5 // 50% threshold for content words (for now its 0, but if needed then put 0.5)
            }

            val meetsThreshold = bestScore >= threshold

            // Calculate confidence for this word match
            val confidence = if (bestMatch != null) {
                calculateWordConfidence(word1, bestMatch.split(" ")[0], bestScore)
            } else {
                0.2
            }

            // Calculate phonetic content similarity
            val phoneticContentSimilarity = if (bestMatch != null) {
                calculatePurePhoneticSimilarity(word1, bestMatch.split(" ")[0])
            } else {
                0.1
            }

            results.add(WordMatchResult(
                word = word1,
                phoneticCode = phone1,
                isStopWord = isStopWord,
                isMetaphoneZero = isMetaphoneZero,
                bestMatch = bestMatch,
                bestScore = bestScore,
                meetsThreshold = meetsThreshold,
                confidence = confidence,
                phoneticContentSimilarity = phoneticContentSimilarity
            ))
        }

        return results
    }


    private fun calculateCombinedSimilarity(code1: String, code2: String): Double {
        val phoneticSim = calculateMetaphoneSimilarity(code1, code2)
        val acousticSim = calculateAcousticCodeSimilarity(code1, code2)
        return (phoneticSim * 0.7 + acousticSim * 0.3)
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

        val maxLen = maxOf(code1.length, code2.length)
        val editDistance = levenshtein.apply(code1, code2)
        var baseScore = maxOf(0.0, 1.0 - (editDistance.toDouble() / maxLen))

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

        acousticBonus += checkCommonPatterns(code1, code2)
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
            val currentWord = words1[i]
            var matchedWords = 1
            var matchedPhrase = ""
            var bestScore = 0.0
            var bestMatch = -1

            // Single-word phonetic match
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

            // Enhanced compound (multi-word) phonetic match with strict validation
            for (j in metaphone2.indices) {
                if (j in matched2 || j + 1 >= metaphone2.size || (j + 1) in matched2) continue

                val combinedPhonetic = metaphone2[j] + metaphone2[j + 1]
                val similarity = calculateMetaphoneSimilarity(metaphone1[i], combinedPhonetic)

                // Very strict validation for compound matches
                val compoundThreshold = 0.90 // Very high threshold for compound matches
                val word1Length = words1[i].length
                val combinedLength = words2[j].length + words2[j + 1].length
                val lengthRatio = minOf(word1Length, combinedLength).toDouble() / maxOf(word1Length, combinedLength)

                // Syllable count validation
                val word1Syllables = estimateSyllableCount(words1[i])
                val combinedSyllables = estimateSyllableCount(words2[j]) + estimateSyllableCount(words2[j + 1])
                val syllableDiff = kotlin.math.abs(word1Syllables - combinedSyllables)

                // Character overlap validation - ensure significant common characters
                val word1Chars = words1[i].lowercase().toSet()
                val word2Chars = (words2[j] + words2[j + 1]).lowercase().toSet()
                val commonChars = word1Chars.intersect(word2Chars).size
                val totalUniqueChars = word1Chars.union(word2Chars).size
                val charOverlapRatio = commonChars.toDouble() / totalUniqueChars

                // Bidirectional similarity check for better accuracy
                val reverseSimilarity = calculateMetaphoneSimilarity(combinedPhonetic, metaphone1[i])
                val bidirectionalSimilarity = (similarity + reverseSimilarity) / 2.0

                // Additional validation: check if it's a meaningful compound word scenario
                val isLikelyCompound = isLikelyCompoundWordScenario(words1[i], words2[j], words2[j + 1])

                val isValidCompound = bidirectionalSimilarity > compoundThreshold &&
                        lengthRatio > 0.7 && // Stricter length ratio
                        syllableDiff <= 1 &&
                        charOverlapRatio > 0.4 && // Require significant character overlap
                        isLikelyCompound &&
                        similarity > bestScore // Must be better than single-word matches

                if (isValidCompound) {
                    bestScore = bidirectionalSimilarity
                    bestMatch = j
                    matchedWords = 2
                    matchedPhrase = "${words2[j]} ${words2[j + 1]}"
                }
            }

            val threshold = if (isStopWord(currentWord)) 0.5 else 0.7
            if (bestMatch != -1 && bestScore > threshold) {
                for (k in bestMatch until (bestMatch + matchedWords)) {
                    matched2.add(k)
                }

                val weight = if (isStopWord(currentWord)) 0.3 else 1.0
                totalScore += bestScore * weight
            } else if (isStopWord(currentWord)) {
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

    // Helper function to estimate syllable count
    private fun estimateSyllableCount(word: String): Int {
        val vowels = "aeiouAEIOU"
        var syllableCount = 0
        var previousWasVowel = false

        for (char in word) {
            val isVowel = char in vowels
            if (isVowel && !previousWasVowel) {
                syllableCount++
            }
            previousWasVowel = isVowel
        }

        // Handle silent 'e' at the end
        if (word.lowercase().endsWith("e") && syllableCount > 1) {
            syllableCount--
        }

        // Ensure minimum of 1 syllable
        return maxOf(1, syllableCount)
    }

    // Helper function to determine if compound word matching makes sense
    private fun isLikelyCompoundWordScenario(singleWord: String, word1: String, word2: String): Boolean {
        val single = singleWord.lowercase()
        val first = word1.lowercase()
        val second = word2.lowercase()

        // Check if single word contains parts of both words
        val containsFirst = single.contains(first.take(2)) || first.contains(single.take(2))
        val containsSecond = single.contains(second.take(2)) || second.contains(single.take(2))

        // Common compound word patterns
        val compoundPatterns = listOf(
            // Time-related compounds
            Pair("sunday", listOf("sun", "day")),
            Pair("monday", listOf("mon", "day")),
            Pair("tuesday", listOf("tues", "day")),
            Pair("wednesday", listOf("wed", "day")),
            Pair("thursday", listOf("thurs", "day")),
            Pair("friday", listOf("fri", "day")),
            Pair("saturday", listOf("sat", "day")),
            // Common compounds
            Pair("something", listOf("some", "thing")),
            Pair("everyone", listOf("every", "one")),
            Pair("someone", listOf("some", "one")),
            Pair("anybody", listOf("any", "body")),
            Pair("classroom", listOf("class", "room")),
            Pair("playground", listOf("play", "ground")),
            Pair("newspaper", listOf("news", "paper")),
            // Contractions that might be spoken as compounds
            Pair("cannot", listOf("can", "not")),
            Pair("will not", listOf("will", "not"))
        )

        // Check against known patterns
        for ((compound, parts) in compoundPatterns) {
            if (single.contains(compound) || compound.contains(single)) {
                if ((first.contains(parts[0]) || parts[0].contains(first)) &&
                    (second.contains(parts[1]) || parts[1].contains(second))) {
                    return true
                }
            }
        }

        // Only allow if there's substantial phonetic/character overlap
        return containsFirst && containsSecond &&
                (single.length >= 6) && // Compound words are usually longer
                (first.length + second.length >= single.length * 0.8) // Combined length makes sense
    }

    private fun showDetailedPhoneticBreakdown(words1: List<String>, words2: List<String>) {
        println(" Detailed Phonetic Analysis:")

        val metaphone1 = words1.map { doubleMetaphone.doubleMetaphone(it) }
        val metaphone2 = words2.map { doubleMetaphone.doubleMetaphone(it) }

        println(" Expected words (phrase1):")
        for (i in words1.indices) {
            val word1 = words1[i]
            val phone1 = metaphone1[i]
            val isStopWordFlag = isStopWord(word1)
            val isMetaphoneZero = phone1 == "0" || phone1.isEmpty()
            val wordType = when {
                isMetaphoneZero -> " ([0] - auto-pass)"
                isStopWordFlag -> " (stop)"
                else -> " (content - needs 60%)"
            }
            println("   Expected: '$word1' → [$phone1]$wordType")
        }

        println(" Recognized words (phrase2):")
        for (j in words2.indices) {
            val word2 = words2[j]
            val phone2 = metaphone2[j]
            val isStopWordFlag = isStopWord(word2)
            val wordType = if (isStopWordFlag) " (stop)" else ""
            println("   Recognized: '$word2' → [$phone2]$wordType")
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
                val minLen = minOf(code1.length, code2.length)
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

                // Character overlap bonus for similar length codes
                val overlapBonus = if (kotlin.math.abs(code1.length - code2.length) <= 1) {
                    val commonChars = code1.toSet().intersect(code2.toSet()).size
                    val totalChars = code1.toSet().union(code2.toSet()).size
                    (commonChars.toDouble() / totalChars) * 0.1
                } else 0.0

                // Apply bonuses but cap at 1.0
                minOf(1.0, baseSimilarity + startBonus + endBonus + containmentBonus + overlapBonus)
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

        // Method 1: Skip-tolerant position similarity (50% weight)
        val positionScore = calculatePositionBasedSimilarity(cleanWords1, cleanWords2)

        // Method 2: Content overlap with phonetic tolerance (30% weight)
        val contentScore = calculatePhoneticContentSimilarity(cleanWords1, cleanWords2)

        // Method 3: Sequence alignment for skip handling (20% weight)
        val sequenceScore = calculateSequenceAlignment(cleanWords1, cleanWords2)

        val finalScore = (positionScore * 0.5 + contentScore * 0.3 + sequenceScore * 0.2)

        println("      Word Order Breakdown:")
        println("      Position-based: ${String.format("%.1f", positionScore * 100)}%")
        println("      Content-based: ${String.format("%.1f", contentScore * 100)}%")
        println("      Sequence alignment: ${String.format("%.1f", sequenceScore * 100)}%")
        println("      Combined: ${String.format("%.1f", finalScore * 100)}%")

        return finalScore
    }

    private fun calculatePositionBasedSimilarity(words1: List<String>, words2: List<String>): Double {
        val maxLen = maxOf(words1.size, words2.size)
        if (maxLen == 0) return 1.0

        var matches = 0.0
        val used2 = BooleanArray(words2.size) { false }

        for (i in words1.indices) {
            val word1 = words1[i]
            val isStopWord1 = isStopWord(word1)
            var bestMatch = 0.0
            var bestIdx = -1

            // Calculate expected position in words2
            val expectedPos = (i.toDouble() / words1.size * words2.size).toInt()

            // Create search window - wider for stop words, narrower for content words
            val windowSize = if (isStopWord1) {
                maxOf(3, minOf(words1.size, words2.size) / 2)  // Stop words can move more
            } else {
                maxOf(1, minOf(words1.size, words2.size) / 4)  // Content words stay close
            }

            val startPos = maxOf(0, expectedPos - windowSize)
            val endPos = minOf(words2.size - 1, expectedPos + windowSize)

            // Search within window for best match
            for (j in startPos..endPos) {
                if (used2[j]) continue

                val word2 = words2[j]
                var similarity = 0.0

                if (word1 == word2) {
                    similarity = 1.0  // Exact match
                } else {
                    // Check phonetic similarity for partial credit
                    val phone1 = doubleMetaphone.doubleMetaphone(word1)
                    val phone2 = doubleMetaphone.doubleMetaphone(word2)
                    val phoneticSim = calculateMetaphoneSimilarity(phone1, phone2)

                    if (phoneticSim >= 0.8) {
                        similarity = 0.9  // High phonetic similarity
                    } else if (phoneticSim >= 0.6) {
                        similarity = 0.7  // Moderate phonetic similarity
                    }
                }

                // Apply position penalty - more lenient for stop words
                if (similarity > 0) {
                    val maxPenalty = if (isStopWord1) 0.2 else 0.4  // Less penalty for stop words
                    val positionPenalty = 1.0 - (kotlin.math.abs(j - expectedPos).toDouble() / windowSize * maxPenalty)
                    similarity *= positionPenalty
                }

                if (similarity > bestMatch) {
                    bestMatch = similarity
                    bestIdx = j
                }
            }

            if (bestIdx != -1 && bestMatch > 0.5) {
                used2[bestIdx] = true
                matches += bestMatch
            } else {
                // CRITICAL: Heavy penalty for missing content words, light penalty for missing stop words
                if (!isStopWord1) {
                    // Content word missing = major penalty
                    matches += 0.0  // No credit at all
                } else {
                    // Stop word missing = minor penalty
                    matches += 0.5  // Half credit for missing stop words
                }
            }
        }

        return matches / words1.size
    }

    private fun calculateSequenceAlignment(words1: List<String>, words2: List<String>): Double {
        val m = words1.size
        val n = words2.size

        if (m == 0 || n == 0) return 0.0

        // DP table where dp[i][j] represents the best alignment score up to words1[i-1] and words2[j-1]
        val dp = Array(m + 1) { DoubleArray(n + 1) { 0.0 } }

        for (i in 1..m) {
            for (j in 1..n) {
                val word1 = words1[i-1]
                val word2 = words2[j-1]

                // Calculate match score
                val matchScore = if (word1 == word2) {
                    1.0
                } else {
                    val phone1 = doubleMetaphone.doubleMetaphone(word1)
                    val phone2 = doubleMetaphone.doubleMetaphone(word2)
                    val phoneticSim = calculateMetaphoneSimilarity(phone1, phone2)
                    if (phoneticSim >= 0.7) phoneticSim * 0.8 else 0.0
                }

                // MODIFIED: Different skip penalties based on word type
                val skipWord1Penalty = if (isStopWord(word1)) 0.8 else 0.3  // Stop words can be skipped easier
                val skipWord2Penalty = if (isStopWord(word2)) 0.8 else 0.3  // Stop words can be skipped easier

                // DP recurrence: max of (match + previous diagonal, skip word1, skip word2)
                dp[i][j] = maxOf(
                    dp[i-1][j-1] + matchScore,           // Match/substitute
                    dp[i-1][j] * skipWord1Penalty,       // Skip word from words1 (penalty based on word type)
                    dp[i][j-1] * skipWord2Penalty        // Skip word from words2 (penalty based on word type)
                )
            }
        }

        // Normalize by the longer sequence length
        val maxLength = maxOf(m, n)
        return dp[m][n] / maxLength
    }

    private fun calculatePhoneticContentSimilarity(words1: List<String>, words2: List<String>): Double {
        // Separate content words and stop words
        val contentWords1 = words1.filter { !isStopWord(it) }
        val contentWords2 = words2.filter { !isStopWord(it) }
        val stopWords1 = words1.filter { isStopWord(it) }
        val stopWords2 = words2.filter { isStopWord(it) }

        // Convert to phonetic codes (excluding metaphone "0" codes)
        val contentPhones1 = contentWords1.map { doubleMetaphone.doubleMetaphone(it) }.filter { it != "0" }
        val contentPhones2 = contentWords2.map { doubleMetaphone.doubleMetaphone(it) }.filter { it != "0" }
        val stopPhones1 = stopWords1.map { doubleMetaphone.doubleMetaphone(it) }.filter { it != "0" }
        val stopPhones2 = stopWords2.map { doubleMetaphone.doubleMetaphone(it) }.filter { it != "0" }

        // Calculate content word similarity (80% weight)
        val contentSimilarity = if (contentPhones1.isEmpty() && contentPhones2.isEmpty()) {
            1.0
        } else if (contentPhones1.isEmpty() || contentPhones2.isEmpty()) {
            0.0  // Missing all content words = major penalty
        } else {
            calculatePhoneticMatching(contentPhones1, contentPhones2)
        }

        // Calculate stop word similarity (20% weight) - more lenient
        val stopSimilarity = if (stopPhones1.isEmpty() && stopPhones2.isEmpty()) {
            1.0
        } else if (stopPhones1.isEmpty() || stopPhones2.isEmpty()) {
            0.7  // Missing stop words = minor penalty
        } else {
            calculatePhoneticMatching(stopPhones1, stopPhones2)
        }

        // Weighted combination: content words matter much more
        return contentSimilarity * 0.8 + stopSimilarity * 0.2
    }

    private fun calculatePhoneticMatching(phones1: List<String>, phones2: List<String>): Double {
        val matched = mutableSetOf<Int>()
        var totalSimilarity = 0.0

        for (phone1 in phones1) {
            var bestSim = 0.0
            var bestIdx = -1

            for (i in phones2.indices) {
                if (i in matched) continue
                val sim = calculateMetaphoneSimilarity(phone1, phones2[i])
                if (sim > bestSim) {
                    bestSim = sim
                    bestIdx = i
                }
            }

            if (bestIdx != -1 && bestSim >= 0.6) {
                matched.add(bestIdx)
                totalSimilarity += bestSim
            }
        }

        val coverage1 = totalSimilarity / phones1.size  // How much of phones1 was matched
        val coverage2 = matched.size.toDouble() / phones2.size  // How much of phones2 was matched

        // Balance between coverage of both sequences
        return (coverage1 + coverage2) / 2.0
    }
}