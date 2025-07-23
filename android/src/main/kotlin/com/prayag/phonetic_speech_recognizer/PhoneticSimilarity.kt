package com.prayag.phonetic_speech_recognizer

import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.codec.language.DoubleMetaphone
import org.apache.commons.text.similarity.JaroWinklerDistance
import org.apache.commons.text.similarity.LevenshteinDistance


class PhoneticSimilarity {
    private val doubleMetaphone = DoubleMetaphone()
    private val levenshtein = LevenshteinDistance()
//    private val jaro = JaroWinklerDistance()

    fun calculatePhoneticSimilarity(phrase1: String, phrase2: String): Double {
        val words1 = phrase1.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val words2 = phrase2.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }

        if (words1.isEmpty() && words2.isEmpty()) return 1.0
        if (words1.isEmpty() || words2.isEmpty()) return 0.0

        // Calculate different similarity metrics
        val metaphoneSim = calculateDynamicPhoneticSimilarity(words1, words2)
        val editSim = calculateEditDistanceSimilarity(phrase1, phrase2)
        val wordOrderSim = calculateWordOrderSimilarity(words1, words2)

        println("🔍 Metric Breakdown:")
        println("   Metaphone (Dynamic): ${metaphoneSim * 100}%")
        println("   Edit Distance: ${editSim * 100}%")
        println("   Word Order: ${wordOrderSim * 100}%")

        // Show phonetic breakdown
        showPhoneticBreakdown(words1, words2)

        // Weighted combination - adjust weights based on your needs
        return (metaphoneSim * 0.75 + editSim * 0.25)
    }

    private fun calculateDynamicPhoneticSimilarity(words1: List<String>, words2: List<String>): Double {
        if (words1.isEmpty() && words2.isEmpty()) return 1.0
        if (words1.isEmpty() || words2.isEmpty()) return 0.0

        val metaphone1 = words1.map { doubleMetaphone.doubleMetaphone(it) }
        val metaphone2 = words2.map { doubleMetaphone.doubleMetaphone(it) }

        val matched2 = mutableSetOf<Int>()
        var totalMatches = 0.0

        // Find best matches for each word in phrase1
        for (i in metaphone1.indices) {
            var bestScore = 0.0
            var bestMatch = -1
            var matchedWords = 1

            // Try single word match first
            for (j in metaphone2.indices) {
                if (j in matched2) continue // Already matched

                val similarity = calculateMetaphoneSimilarity(metaphone1[i], metaphone2[j])
                if (similarity > bestScore) {
                    bestScore = similarity
                    bestMatch = j
                    matchedWords = 1
                }
            }

            // Try multi-word combinations (for cases like "twizer" = "toys are")
            for (j in metaphone2.indices) {
                if (j in matched2) continue

                // Try combining 2 consecutive words
                if (j + 1 < metaphone2.size && (j + 1) !in matched2) {
                    val combinedPhonetic = metaphone2[j] + metaphone2[j + 1]
                    val similarity = calculateMetaphoneSimilarity(metaphone1[i], combinedPhonetic)
                    if (similarity > bestScore) {
                        bestScore = similarity
                        bestMatch = j
                        matchedWords = 2
                    }
                }

                // Try combining 3 consecutive words
                if (j + 2 < metaphone2.size && (j + 1) !in matched2 && (j + 2) !in matched2) {
                    val combinedPhonetic = metaphone2[j] + metaphone2[j + 1] + metaphone2[j + 2]
                    val similarity = calculateMetaphoneSimilarity(metaphone1[i], combinedPhonetic)
                    if (similarity > bestScore) {
                        bestScore = similarity
                        bestMatch = j
                        matchedWords = 3
                    }
                }
            }

            if (bestMatch != -1 && bestScore > 0.7) { // Threshold for acceptable match
                // Mark all matched words as used
                for (k in bestMatch until (bestMatch + matchedWords)) {
                    matched2.add(k)
                }
                totalMatches += bestScore
            }
        }

        val maxPossibleMatches = maxOf(words1.size, words2.size)
        return totalMatches / maxPossibleMatches
    }

    private fun calculateMetaphoneSimilarity(code1: String, code2: String): Double {
        return when {
            code1 == code2 -> 1.0  // Perfect match
            code1.isEmpty() || code2.isEmpty() -> 0.0
            else -> {
                // Calculate similarity based on character overlap
                val maxLen = maxOf(code1.length, code2.length)
                val distance = levenshtein.apply(code1, code2)
                maxOf(0.0, 1.0 - (distance.toDouble() / maxLen))
            }
        }
    }

    private fun showPhoneticBreakdown(words1: List<String>, words2: List<String>) {
        println("🔍 Phonetic Analysis (Dynamic Matching):")

        val metaphone1 = words1.map { doubleMetaphone.doubleMetaphone(it) }
        val metaphone2 = words2.map { doubleMetaphone.doubleMetaphone(it) }

        val matched2 = mutableSetOf<Int>()

        for (i in words1.indices) {
            val word1 = words1[i]
            val phone1 = metaphone1[i]

            var bestMatch = -1
            var bestScore = 0.0
            var matchedWords = 1
            var matchedPhrase = ""

            // Try single word match first
            for (j in words2.indices) {
                if (j in matched2) continue // Already matched

                val phone2 = metaphone2[j]
                val similarity = calculateMetaphoneSimilarity(phone1, phone2)

                if (similarity > bestScore) {
                    bestScore = similarity
                    bestMatch = j
                    matchedWords = 1
                    matchedPhrase = words2[j]
                }
            }

            // Try multi-word combinations
            for (j in metaphone2.indices) {
                if (j in matched2) continue

                // Try combining 2 consecutive words
                if (j + 1 < metaphone2.size && (j + 1) !in matched2) {
                    val combinedPhonetic = metaphone2[j] + metaphone2[j + 1]
                    val similarity = calculateMetaphoneSimilarity(phone1, combinedPhonetic)
                    if (similarity > bestScore) {
                        bestScore = similarity
                        bestMatch = j
                        matchedWords = 2
                        matchedPhrase = "${words2[j]} ${words2[j + 1]}"
                    }
                }

                // Try combining 3 consecutive words
                if (j + 2 < metaphone2.size && (j + 1) !in matched2 && (j + 2) !in matched2) {
                    val combinedPhonetic = metaphone2[j] + metaphone2[j + 1] + metaphone2[j + 2]
                    val similarity = calculateMetaphoneSimilarity(phone1, combinedPhonetic)
                    if (similarity > bestScore) {
                        bestScore = similarity
                        bestMatch = j
                        matchedWords = 3
                        matchedPhrase = "${words2[j]} ${words2[j + 1]} ${words2[j + 2]}"
                    }
                }
            }

            if (bestMatch != -1 && bestScore > 0.7) {
                // Mark all matched words as used
                for (k in bestMatch until (bestMatch + matchedWords)) {
                    matched2.add(k)
                }
                val combinedPhonetic = if (matchedWords == 1) {
                    metaphone2[bestMatch]
                } else {
                    (bestMatch until (bestMatch + matchedWords)).map { metaphone2[it] }.joinToString("")
                }
                val status = if (bestScore >= 0.95) "✅ EXACT" else "🟡 SIMILAR"
                println("   • '$word1' [$phone1] ↔ '$matchedPhrase' [$combinedPhonetic] → $status (${(bestScore * 100).toInt()}%)")
            } else {
                println("   • '$word1' [$phone1] → ❌ NO MATCH")
            }
        }

        // Show unmatched words from phrase2
        for (j in words2.indices) {
            if (j !in matched2) {
                val word2 = words2[j]
                val phone2 = metaphone2[j]
                println("   • MISSING: '$word2' [$phone2] → ❌ NOT FOUND")
            }
        }
    }

    private fun calculateStringSequenceSimilarity(seq1: List<String>, seq2: List<String>): Double {
        val m = seq1.size
        val n = seq2.size

        val dp = Array(m + 1) { DoubleArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                val match = calculateStringCodeSimilarity(seq1[i-1], seq2[j-1])

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

    private fun calculateStringCodeSimilarity(code1: String, code2: String): Double {
        return when {
            code1 == code2 -> 1.0
            code1.isEmpty() || code2.isEmpty() -> 0.0
            else -> {
                val maxLen = maxOf(code1.length, code2.length)
                val distance = levenshtein.apply(code1, code2)
                1.0 - (distance.toDouble() / maxLen)
            }
        }
    }

    private fun calculateSequenceSimilarity(seq1: List<DoubleMetaphoneResult>, seq2: List<DoubleMetaphoneResult>): Double {
        val m = seq1.size
        val n = seq2.size

        val dp = Array(m + 1) { DoubleArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                val match = calculateMetaphoneResultSimilarity(seq1[i-1], seq2[j-1])

                dp[i][j] = maxOf(
                    dp[i-1][j-1] + match,  // Match
                    dp[i-1][j] * 0.8,      // Skip from seq1 (penalty)
                    dp[i][j-1] * 0.8       // Skip from seq2 (penalty)
                )
            }
        }

        val maxLength = maxOf(m, n)
        return if (maxLength > 0) dp[m][n] / maxLength else 0.0
    }

    private fun calculateMetaphoneResultSimilarity(result1: DoubleMetaphoneResult, result2: DoubleMetaphoneResult): Double {
        // Double Metaphone gives primary and alternate encodings
        val matches = listOf(
            result1.primary == result2.primary,
            result1.primary == result2.alternate,
            result1.alternate == result2.primary,
            result1.alternate == result2.alternate && result1.alternate.isNotEmpty()
        )

        return when {
            matches[0] -> 1.0  // Perfect primary match
            matches[1] || matches[2] -> 0.9  // Cross match
            matches[3] -> 0.8  // Alternate match
            else -> calculateCodeEditSimilarity(result1.primary, result2.primary)
        }
    }

    private fun calculateCodeEditSimilarity(code1: String, code2: String): Double {
        if (code1.isEmpty() && code2.isEmpty()) return 1.0
        if (code1.isEmpty() || code2.isEmpty()) return 0.0

        val maxLen = maxOf(code1.length, code2.length)
        val distance = levenshtein.apply(code1, code2)
        return maxOf(0.0, 1.0 - (distance.toDouble() / maxLen))
    }

    private fun calculateEditDistanceSimilarity(phrase1: String, phrase2: String): Double {
        val maxLen = maxOf(phrase1.length, phrase2.length)
        if (maxLen == 0) return 1.0

        val distance = levenshtein.apply(phrase1.lowercase(), phrase2.lowercase())
        return 1.0 - (distance.toDouble() / maxLen)
    }

    private fun calculateWordOrderSimilarity(words1: List<String>, words2: List<String>): Double {
        if (words1.isEmpty() || words2.isEmpty()) return 0.0

        // Clean words by removing punctuation and converting to lowercase
        val cleanWords1 = words1.map { it.lowercase().replace(Regex("[^a-zA-Z]"), "") }.filter { it.isNotEmpty() }
        val cleanWords2 = words2.map { it.lowercase().replace(Regex("[^a-zA-Z]"), "") }.filter { it.isNotEmpty() }

        val set1 = cleanWords1.toSet()
        val set2 = cleanWords2.toSet()

        val intersection = set1.intersect(set2).size
        val union = set1.union(set2).size

        println("Debug words1: $cleanWords1")
        println("Debug words2: $cleanWords2")
        println("Debug set1: $set1")
        println("Debug set2: $set2")
        println("Debug intersection: ${set1.intersect(set2)}")
        println("Debug union: ${set1.union(set2)}")

        return if (union > 0) intersection.toDouble() / union else 0.0
    }
}

// Double Metaphone implementation - much more accurate than Soundex
class DoubleMetaphone {

    fun encode(word: String): DoubleMetaphoneResult {
        if (word.isEmpty()) return DoubleMetaphoneResult("", "")

        val normalized = normalizeForSpeechRecognition(word.uppercase())
        val primary = StringBuilder()
        val alternate = StringBuilder()

        var current = 0
        val length = normalized.length

        // Handle initial silent letters
        current = skipInitialSilent(normalized, current)

        while (current < length && primary.length < 4) {
            when (normalized[current]) {
                'A', 'E', 'I', 'O', 'U', 'Y' -> {
                    if (current == 0) {
                        primary.append('A')
                        alternate.append('A')
                    }
                    current++
                }
                'B' -> {
                    primary.append('P')
                    alternate.append('P')
                    current = if (current + 1 < length && normalized[current + 1] == 'B') current + 2 else current + 1
                }
                'C' -> {
                    val result = handleC(normalized, current, primary, alternate)
                    current = result
                }
                'D' -> {
                    val result = handleD(normalized, current, primary, alternate)
                    current = result
                }
                'F' -> {
                    primary.append('F')
                    alternate.append('F')
                    current = if (current + 1 < length && normalized[current + 1] == 'F') current + 2 else current + 1
                }
                'G' -> {
                    val result = handleG(normalized, current, primary, alternate)
                    current = result
                }
                'H' -> {
                    val result = handleH(normalized, current, primary, alternate)
                    current = result
                }
                'J' -> {
                    primary.append('J')
                    alternate.append('J')
                    current = if (current + 1 < length && normalized[current + 1] == 'J') current + 2 else current + 1
                }
                'K' -> {
                    primary.append('K')
                    alternate.append('K')
                    current = if (current + 1 < length && normalized[current + 1] == 'K') current + 2 else current + 1
                }
                'L' -> {
                    primary.append('L')
                    alternate.append('L')
                    current = if (current + 1 < length && normalized[current + 1] == 'L') current + 2 else current + 1
                }
                'M' -> {
                    primary.append('M')
                    alternate.append('M')
                    current = if (current + 1 < length && normalized[current + 1] == 'M') current + 2 else current + 1
                }
                'N' -> {
                    primary.append('N')
                    alternate.append('N')
                    current = if (current + 1 < length && normalized[current + 1] == 'N') current + 2 else current + 1
                }
                'P' -> {
                    val result = handleP(normalized, current, primary, alternate)
                    current = result
                }
                'Q' -> {
                    primary.append('K')
                    alternate.append('K')
                    current = if (current + 1 < length && normalized[current + 1] == 'U') current + 2 else current + 1
                }
                'R' -> {
                    primary.append('R')
                    alternate.append('R')
                    current = if (current + 1 < length && normalized[current + 1] == 'R') current + 2 else current + 1
                }
                'S' -> {
                    val result = handleS(normalized, current, primary, alternate)
                    current = result
                }
                'T' -> {
                    val result = handleT(normalized, current, primary, alternate)
                    current = result
                }
                'V' -> {
                    primary.append('F')
                    alternate.append('F')
                    current = if (current + 1 < length && normalized[current + 1] == 'V') current + 2 else current + 1
                }
                'W' -> {
                    val result = handleW(normalized, current, primary, alternate)
                    current = result
                }
                'X' -> {
                    val result = handleX(normalized, current, primary, alternate)
                    current = result
                }
                'Z' -> {
                    primary.append('S')
                    alternate.append('S')
                    current = if (current + 1 < length && normalized[current + 1] == 'Z') current + 2 else current + 1
                }
                else -> current++
            }
        }

        return DoubleMetaphoneResult(
            primary.toString().padEnd(4, '0').take(4),
            alternate.toString().padEnd(4, '0').take(4)
        )
    }

    private fun normalizeForSpeechRecognition(word: String): String {
        return word.uppercase()
            // Common speech recognition confusions
            .replace("PH", "F")
            .replace("GH", "F")
            .replace("CK", "K")
            .replace("QU", "KW")
            // Handle treasure -> toys are confusion
            .replace("TREASURE", "TOYSARE")
            .replace("TREASUR", "TOYSAR")
            // Other common confusions
            .replace("TION", "SHON")
            .replace("SION", "SHON")
            .filter { it.isLetter() }
    }

    private fun skipInitialSilent(word: String, start: Int): Int {
        var current = start
        // Skip initial silent letters
        if (current < word.length - 1) {
            when {
                word.startsWith("GN") || word.startsWith("KN") ||
                        word.startsWith("PN") || word.startsWith("WR") ||
                        word.startsWith("PS") -> current = 1
            }
        }
        return current
    }

    // Simplified handlers - you can expand these based on your specific needs
    private fun handleC(word: String, pos: Int, primary: StringBuilder, alternate: StringBuilder): Int {
        return when {
            pos + 1 < word.length && word[pos + 1] == 'H' -> {
                primary.append("K")
                alternate.append("K")
                pos + 2
            }
            pos + 1 < word.length && (word[pos + 1] == 'E' || word[pos + 1] == 'I' || word[pos + 1] == 'Y') -> {
                primary.append("S")
                alternate.append("S")
                pos + 1
            }
            else -> {
                primary.append("K")
                alternate.append("K")
                pos + 1
            }
        }
    }

    private fun handleD(word: String, pos: Int, primary: StringBuilder, alternate: StringBuilder): Int {
        return when {
            pos + 2 < word.length && word.substring(pos, pos + 2) == "DG" -> {
                primary.append("J")
                alternate.append("J")
                pos + 2
            }
            else -> {
                primary.append("T")
                alternate.append("T")
                pos + 1
            }
        }
    }

    private fun handleG(word: String, pos: Int, primary: StringBuilder, alternate: StringBuilder): Int {
        return when {
            pos + 1 < word.length && word[pos + 1] == 'H' -> {
                primary.append("K")
                alternate.append("K")
                pos + 2
            }
            pos + 1 < word.length && (word[pos + 1] == 'E' || word[pos + 1] == 'I' || word[pos + 1] == 'Y') -> {
                primary.append("J")
                alternate.append("J")
                pos + 1
            }
            else -> {
                primary.append("K")
                alternate.append("K")
                pos + 1
            }
        }
    }

    private fun handleH(word: String, pos: Int, primary: StringBuilder, alternate: StringBuilder): Int {
        return if (pos == 0 || isVowel(word[pos - 1]) && pos + 1 < word.length && isVowel(word[pos + 1])) {
            primary.append("H")
            alternate.append("H")
            pos + 1
        } else {
            pos + 1
        }
    }

    private fun handleP(word: String, pos: Int, primary: StringBuilder, alternate: StringBuilder): Int {
        return if (pos + 1 < word.length && word[pos + 1] == 'H') {
            primary.append("F")
            alternate.append("F")
            pos + 2
        } else {
            primary.append("P")
            alternate.append("P")
            pos + 1
        }
    }

    private fun handleS(word: String, pos: Int, primary: StringBuilder, alternate: StringBuilder): Int {
        return when {
            pos + 1 < word.length && word[pos + 1] == 'H' -> {
                primary.append("S")
                alternate.append("S")
                pos + 2
            }
            else -> {
                primary.append("S")
                alternate.append("S")
                pos + 1
            }
        }
    }

    private fun handleT(word: String, pos: Int, primary: StringBuilder, alternate: StringBuilder): Int {
        return when {
            pos + 2 < word.length && word.substring(pos, pos + 3) == "TH" -> {
                primary.append("0") // Theta sound
                alternate.append("T")
                pos + 2
            }
            pos + 3 < word.length && (word.substring(pos, pos + 4) == "TION" || word.substring(pos, pos + 4) == "TIAL") -> {
                primary.append("S")
                alternate.append("S")
                pos + 3
            }
            else -> {
                primary.append("T")
                alternate.append("T")
                pos + 1
            }
        }
    }

    private fun handleW(word: String, pos: Int, primary: StringBuilder, alternate: StringBuilder): Int {
        return if (pos + 1 < word.length && isVowel(word[pos + 1])) {
            primary.append("W")
            alternate.append("W")
            pos + 1
        } else {
            pos + 1
        }
    }

    private fun handleX(word: String, pos: Int, primary: StringBuilder, alternate: StringBuilder): Int {
        return if (pos == 0) {
            primary.append("S")
            alternate.append("S")
            pos + 1
        } else {
            primary.append("KS")
            alternate.append("KS")
            pos + 1
        }
    }

    private fun isVowel(char: Char): Boolean {
        return char in "AEIOUY"
    }
}

data class DoubleMetaphoneResult(
    val primary: String,
    val alternate: String
)

// Jaro-Winkler Distance implementation
class JaroWinklerDistance {
    fun calculate(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0

        val len1 = s1.length
        val len2 = s2.length

        if (len1 == 0 || len2 == 0) return 0.0

        val matchWindow = maxOf(len1, len2) / 2 - 1
        if (matchWindow < 0) return 0.0

        val s1Matches = BooleanArray(len1)
        val s2Matches = BooleanArray(len2)

        var matches = 0
        var transpositions = 0

        // Find matches
        for (i in 0 until len1) {
            val start = maxOf(0, i - matchWindow)
            val end = minOf(i + matchWindow + 1, len2)

            for (j in start until end) {
                if (s2Matches[j] || s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        // Find transpositions
        var k = 0
        for (i in 0 until len1) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val jaro = (matches.toDouble() / len1 +
                matches.toDouble() / len2 +
                (matches - transpositions / 2.0) / matches) / 3.0

        // Winkler modification
        var prefix = 0
        for (i in 0 until minOf(len1, len2, 4)) {
            if (s1[i] == s2[i]) prefix++ else break
        }

        return jaro + (0.1 * prefix * (1.0 - jaro))
    }
}