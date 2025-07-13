package com.prayag.phonetic_speech_recognizer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
//import android.widget.Toast
import org.apache.commons.lang3.StringUtils
import java.io.BufferedReader
import java.io.InputStreamReader

class LanguageHandlers(private val context: Context) {
    private var pluginInstance: PhoneticSpeechRecognizerPlugin? = null

    /**
     * Sets the plugin instance that will receive the recognition results.
     *
     * @param plugin the plugin instance to receive the recognition results
     */
    fun setPluginInstance(plugin: PhoneticSpeechRecognizerPlugin) {
        pluginInstance = plugin
    }

    /**
     * Handles alphabet recognition, first gets nepali language and maps it back to english..
     * Its done because english cant detect alphabets.
     * @param timeoutMillis the timeout in milliseconds for the recognition
     */
    fun handleAlphabetRecognition(timeoutMillis: Int) {

        val lang = "ne-NP"
        pluginInstance?.startRecognition(
            paragraph = "",
            lang = lang,
            mapper = { text -> Mapper().mapText(text.keys.first(), PhoneticMapping.phoneticNepaliToEnglishMapping) },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    /**
     * Handles recognition for all languages.
     *
     * @param timeoutMillis the timeout in milliseconds for the recognition
     * @param languageCode the language code for the recognition
     */
    fun handleAllLanguages(timeoutMillis: Int, languageCode: String) {

        pluginInstance?.startRecognition(
            paragraph = "",
            lang = languageCode,
            mapper = { text -> Mapper().mapNumber(text.keys.first(), PhoneticMapping.phoneticNepaliToEnglishMapping) },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    /**
     * Handles Korean alphabet recognition, works similar to english alohabets.
     *
     * @param timeoutMillis the timeout in milliseconds for the recognition
     */
    fun handleKoreanAlphabetRecognition(timeoutMillis: Int) {

        pluginInstance?.startRecognition(
            paragraph = "",
            lang = "ne-NP",
            mapper = { text -> Mapper().mapText(text.keys.first(), PhoneticMapping.phoneticKoreanMapping) },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    /**
     * Handles number recognition based on hindi, similar to english alphabet detection.
     *
     * @param timeoutMillis the timeout in milliseconds for the recognition
     */
    fun handleNumberRecognition(timeoutMillis: Int, sentence: String) {
        val lang = if (listOf("0","1", "2", "3", "4", "5", "6", "7", "8", "9", "10").any { sentence.contains(it) }) {
            "ne-NP"
        } else {
            "hi-IN"
        }
        println("this is number $sentence, lang $lang")

        pluginInstance?.startRecognition(
            paragraph = "",
            lang = lang,
            mapper = { text -> Mapper().mapNumbersIncludingSpellings(text.keys.first(), PhoneticMapping.phoneticNumbersMapping) },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    /**
     * Handles word recognition like objects.
     *
     * @param languageCode the language code for the recognition
     * @param timeoutMillis the timeout in milliseconds for the recognition
     * @param sentence the sentence to recognize
     */
    fun handleWordsRecognition(languageCode: String?, timeoutMillis: Int, sentence: String) {
        Log.d("SpeechRecognition", "SENTENCE FROM FLUTTER SIDE: \"$sentence\"")
        if (languageCode == null) {
            println("this is sentence $sentence")
            pluginInstance?.activeResult?.error("INVALID_LANG", "Language code required", null)
            pluginInstance?.activeResult = null
            return
        }


        pluginInstance?.startRecognition(
            paragraph = "",
            lang = languageCode,
            mapper = { text ->
                if (languageCode == "en-US") {
                    val context = ContextBasedDetection().detectContext(sentence)
                    Log.d("SpeechRecognition", "Detected context: $context")
                    Log.d("SpeechRecognition", "Original recognition: ${text.keys.first()}")
                    correctRecognizedPhrase(listOf(text.keys.first()), sentence, context)
                } else {
                    text
                }
            },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    /**
     * Handles paragraph mapping or partial texts.
     *
     * @param languageCode the language code for the recognition
     * @param timeoutMillis the timeout in milliseconds for the recognition
     * @param paragraph the paragraph to recognize
     */
    fun handleParagraphMapping(languageCode: String?, timeoutMillis: Int, paragraph: String) {
        Log.d("SpeechRecognition", "PARAGRAPH FROM FLUTTER SIDE: \"$paragraph\"")
        if (languageCode == null) {
            println("this is paragraph $paragraph")
            pluginInstance?.activeResult?.error("INVALID_LANG", "Language code required", null)
            pluginInstance?.activeResult = null
            return
        }


        val words = paragraph.split(" ").map { it.trim() }.filter { it.isNotEmpty() }

        pluginInstance?.startRecognition(
            paragraph = paragraph,
            lang = languageCode,
            mapper = { text ->
                if (languageCode == "en-US") {
                    pluginInstance?.updateHighlightedText(text.keys.first(), words, paragraph)
                }
                text
            },
            timeoutMillis = timeoutMillis,
            keepListening = true
        )
    }

    /**
     * Handles Japanese recognition based on nepali language.
     *
     * @param timeoutMillis the timeout in milliseconds for the recognition
     * @param type the type of recognition
     */
    fun handleJapaneseRecognition(timeoutMillis: Int, type: String) {

        val lang = "ne-NP"
        pluginInstance?.startRecognition(
            paragraph = "",
            lang = lang,
            mapper = { text ->
                Mapper().mapNumber(
                    text.keys.first(),
                    PhoneticMapping.phoneticJapaneseAlphabetMapping
                )
            },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    /**
     * Handles Korean number recognition based on nepali language.
     *
     * @param timeoutMillis the timeout in milliseconds for the recognition
     * @param type the type of recognition
     */
    fun handleKoreanNumberRecognition(timeoutMillis: Int, type: String) {

        val lang = "ne-NP"
        pluginInstance?.startRecognition(
            paragraph = "",
            lang = lang,
            mapper = { text ->
                Mapper().mapNumber(
                    text.keys.first(),
                    PhoneticMapping.phoneticKoreanNumberMapping
                )
            },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }


    // Helper method for correcting recognized phrases based on phonetic similarity
    fun correctRecognizedPhrase(
        recognizedPhrases: List<String>,
        expectedPhrase: String,
        context: String = "" // Add context parameter
    ): Map<String, Double> {
        if (recognizedPhrases.isEmpty()) return mapOf("" to 0.0)

        var bestMatch = recognizedPhrases[0]
        var bestSimilarity = 0.0

        for (recognizedPhrase in recognizedPhrases) {
            // Apply context-aware preprocessing
            val preprocessedPhrase = ContextBasedDetection().preprocessWithContext(recognizedPhrase, context)

            val phoneticSimilarity = PhoneticSimilarity().calculatePhoneticSimilarity(
                preprocessedPhrase, expectedPhrase
            )

//            val stringSimilarity = 1.0 - (
//                    StringUtils.getLevenshteinDistance(recognizedPhrase, expectedPhrase).toDouble() /
//                            kotlin.math.max(recognizedPhrase.length, expectedPhrase.length)
//                    )
            // Adjust weights based on context
            val contextWeight = if (context.isNotEmpty()) 0.7 else 0.5
//            val similarity = (phoneticSimilarity * contextWeight) + (stringSimilarity * (1.0 - contextWeight))
            val similarity = phoneticSimilarity

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestMatch = preprocessedPhrase
            }
        }

        val threshold = calculateDynamicThreshold(expectedPhrase)

        return if (bestSimilarity >= threshold) {
            mapOf(expectedPhrase to bestSimilarity)
        } else {
            mapOf(bestMatch to bestSimilarity)
        }
    }

    /**
     * Calculates a dynamic threshold based on the length and complexity of the expected phrase.
     * This is used to adjust the similarity threshold for the phonetic similarity correction.
     * The goal is to be more lenient for shorter phrases and more strict for longer phrases.
     *
     * @param expectedPhrase the expected phrase
     * @return the dynamic threshold
     */
    private fun calculateDynamicThreshold(expectedPhrase: String): Double {
        val words = expectedPhrase.trim().split("\\s+".toRegex())
        val wordCount = words.size

        // Check if phrase contains commonly misrecognized words
        val problematicWords = listOf("we", "ate", "the", "a", "I", "you", "to", "too", "two", "for", "four")
        val hasProblematicWords = words.any { it.lowercase() in problematicWords }

        // Check for short words that are often confused
        val shortWords = words.filter { it.length <= 2 }
        val hasShortWords = shortWords.isNotEmpty()

        // Check if any word has 2 or more syllables (simple heuristic: contains vowel groups)
        val hasMultiSyllableWords = words.any { word ->
            val vowelGroups = word.lowercase().split(Regex("[bcdfghjklmnpqrstvwxyz]+")).filter { it.isNotEmpty() }
            vowelGroups.size >= 2
        }

        return when {
            wordCount == 1 -> 0.40
            wordCount <= 2 && hasProblematicWords -> 0.50
            wordCount <= 2 && hasShortWords -> 0.50
            wordCount <= 3 && hasMultiSyllableWords -> 0.60  // New condition for multi-syllable words
            wordCount <= 3 && hasProblematicWords -> 0.50
            wordCount <= 3 -> 0.50
            wordCount >= 4 -> 0.60
            hasProblematicWords -> 0.50
            else -> 0.60
        }
    }
}