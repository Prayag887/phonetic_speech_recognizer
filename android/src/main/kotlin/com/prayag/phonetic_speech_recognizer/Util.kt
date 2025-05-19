//package com.prayag.phonetic_speech_recognizer
//
//import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.codec.language.DoubleMetaphone
//import org.apache.commons.lang3.StringUtils
//
//class Util {
//    fun correctRecognizedPhrase(recognizedPhrases: List<String>, expectedPhrase: String): String {
//        if (recognizedPhrases.isEmpty()) return ""
//
//        val doubleMetaphone = DoubleMetaphone()
//
//        // Function to get the phonetic codes from a phrase
//        fun getPhoneticCodes(text: String): List<String> {
//            return text.lowercase()
//                .replace(Regex("[^a-z\\s]"), "")  // Remove non-alphabet characters
//                .split("\\s+".toRegex()) // Split by spaces (fix for multi-word phrases)
//                .map { word ->
//                    // Ensure non-null phonetic codes, fallback to word if null
//                    doubleMetaphone.doubleMetaphone(word) ?: word
//                }
//        }
//
//
//        fun calculateCodeSimilarity(code1: String, code2: String): Double {
//            // Group similar sounds together
//            val soundGroups = mapOf(
//                setOf('R', 'W') to 0.8,
//                setOf('N', "NG") to 0.8,
//                setOf("EY", "EH", "AE") to 0.7,
//                setOf("CH", "JH", "GE") to 0.7
//            )
//
//            // If codes are identical, return 1.0
//            if (code1 == code2) return 1.0
//
//            // Check if codes belong to the same sound group
//            for ((group, similarity) in soundGroups) {
//                if (code1 in group && code2 in group) {
//                    return similarity
//                }
//            }
//
//            // Handle partial matches
//            val minLength = kotlin.math.min(code1.length, code2.length)
//            val commonPrefix = code1.commonPrefixWith(code2)
//            if (commonPrefix.length > 0) {
//                return commonPrefix.length.toDouble() / minLength * 0.5
//            }
//
//            return 0.0
//        }
//
//
//        fun calculatePhoneticSimilarity(phrase1: String, phrase2: String): Double {
//            val phonetics1 = getPhoneticCodes(phrase1)
//            val phonetics2 = getPhoneticCodes(phrase2)
//
//            if (kotlin.math.abs(phonetics1.size - phonetics2.size) > 1) return 0.0
//
//            var totalSimilarity = 0.0
//            val maxLength = kotlin.math.max(phonetics1.size, phonetics2.size)
//
//            phonetics1.forEachIndexed { index, code1 ->
//                if (index < phonetics2.size) {
//                    val code2 = phonetics2[index]
//                    // Calculate similarity between individual phonetic codes
//                    totalSimilarity += calculateCodeSimilarity(code1, code2)
//                }
//            }
//
//            return totalSimilarity / maxLength
//        }
//        var bestMatch = recognizedPhrases[0]
//        var bestSimilarity = 0.0
//
//        // Iterate through all recognized phrases and calculate the best match based on similarity
//        for (recognizedPhrase in recognizedPhrases) {
//            val phoneticSimilarity = calculatePhoneticSimilarity(recognizedPhrase, expectedPhrase)
//
////      this is to check the string similarity based on 0 to 1, 1 being best match.
//            val stringSimilarity = 1.0 - (StringUtils.getLevenshteinDistance(recognizedPhrase, expectedPhrase).toDouble() / kotlin.math.max(recognizedPhrase.length, expectedPhrase.length))
//            val similarity = (phoneticSimilarity + stringSimilarity) / 2.0  // Combine both phonetic and string similarity
//
//            // Keep track of the best match
//            if (similarity > bestSimilarity) {
//                bestSimilarity = similarity
//                bestMatch = recognizedPhrase
//            }
//        }
//
//        if (bestSimilarity >= 0.8) {
//            return expectedPhrase
//        }
//        return bestMatch
//    }
//}