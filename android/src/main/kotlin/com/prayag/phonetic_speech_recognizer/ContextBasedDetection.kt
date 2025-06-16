package com.prayag.phonetic_speech_recognizer

class ContextBasedDetection {
    fun detectContext(sentence: String): String {
        val lowerSentence = sentence.lowercase()

        return when {
            lowerSentence.contains("ate") || lowerSentence.contains("eat") ||
                    lowerSentence.contains("food") || lowerSentence.contains("banana") ||
                    lowerSentence.contains("apple") || lowerSentence.contains("lunch") ||
                    lowerSentence.contains("dinner") || lowerSentence.contains("breakfast") -> "eating"

            lowerSentence.contains("went") || lowerSentence.contains("go") ||
                    lowerSentence.contains("walk") || lowerSentence.contains("run") -> "movement"

            lowerSentence.contains("we") || lowerSentence.contains("us") ||
                    lowerSentence.contains("our") -> "group"

            else -> "general"
        }
    }
}