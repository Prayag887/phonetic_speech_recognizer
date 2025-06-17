package com.prayag.phonetic_speech_recognizer

class ContextBasedDetection {
    /**
     * Determines the context of a given sentence based on specific keywords.
     *
     * This function analyzes the input sentence by converting it to lowercase
     * and checking for the presence of certain keywords that indicate specific contexts:
     * - "eating": Identified by words related to food or meals such as "ate", "eat", "food",
     *   "banana", "apple", "lunch", "dinner", or "breakfast".
     * - "movement": Identified by actions related to movement such as "went", "go", "walk", or "run".
     * - "group": Identified by words indicative of group references such as "we", "us", or "our".
     * - If none of these keywords are found, the context is considered "general".
     *
     * @param sentence The input sentence whose context is to be determined.
     * @return A string representing the detected context, which could be "eating", "movement", "group", or "general".
     */
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

    fun preprocessWithContext(phrase: String, context: String): String {
        var corrected = phrase

        // Common speech recognition errors in food context
        val foodContextCorrections = mapOf(
            "V8" to "we ate",
            "V 8" to "we ate",
            "we 8" to "we ate",
            "we eight" to "we ate",
            "VI" to "we",
            "V" to "we"
        )

        if (context.contains("food") || context.contains("eating")) {
            foodContextCorrections.forEach { (wrong, correct) ->
                corrected = corrected.replace(wrong, correct, ignoreCase = true)
            }
        }

        return corrected
    }
}