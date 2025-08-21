//package com.prayag.phonetic_speech_recognizer
//// mostly used for spelling checks and corrections
//
//class JaroWinklerDistance {
//    fun calculate(s1: String, s2: String): Double {
//        if (s1 == s2) return 1.0
//
//        val len1 = s1.length
//        val len2 = s2.length
//
//        if (len1 == 0 || len2 == 0) return 0.0
//
//        val matchWindow = maxOf(len1, len2) / 2 - 1
//        if (matchWindow < 0) return 0.0
//
//        val s1Matches = BooleanArray(len1)
//        val s2Matches = BooleanArray(len2)
//
//        var matches = 0
//        var transpositions = 0
//
//        // Find matches
//        for (i in 0 until len1) {
//            val start = maxOf(0, i - matchWindow)
//            val end = minOf(i + matchWindow + 1, len2)
//
//            for (j in start until end) {
//                if (s2Matches[j] || s1[i] != s2[j]) continue
//                s1Matches[i] = true
//                s2Matches[j] = true
//                matches++
//                break
//            }
//        }
//
//        if (matches == 0) return 0.0
//
//        // Find transpositions
//        var k = 0
//        for (i in 0 until len1) {
//            if (!s1Matches[i]) continue
//            while (!s2Matches[k]) k++
//            if (s1[i] != s2[k]) transpositions++
//            k++
//        }
//
//        val jaro = (matches.toDouble() / len1 +
//                matches.toDouble() / len2 +
//                (matches - transpositions / 2.0) / matches) / 3.0
//
//        // Winkler modification
//        var prefix = 0
//        for (i in 0 until minOf(len1, len2, 4)) {
//            if (s1[i] == s2[i]) prefix++ else break
//        }
//
//        return jaro + (0.1 * prefix * (1.0 - jaro))
//    }
//}