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
//                    Log.d("SpeechRecognition", "Detected context: $context")
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
        context: String = ""
    ): Map<String, Double> {
        if (recognizedPhrases.isEmpty()) return emptyMap()

        // Use the enhanced phonetic similarity
        val enhancedSimilarity = PhoneticSimilarity()
        var bestMatch = ""
        var bestSimilarity = 0.0

        println("=" * 60)
        println("🎯 ENHANCED PHONETIC SPEECH RECOGNITION CORRECTION")
        println("=" * 60)
        println("📝 Expected Phrase: '$expectedPhrase'")
        println("🗣️  Context: '${context.ifEmpty { "No context provided" }}'")
        println("📊 Total Recognized Phrases: ${recognizedPhrases.size}")
        println()

        for ((index, recognizedPhrase) in recognizedPhrases.withIndex()) {
            println("--- Processing Phrase ${index + 1} ---")
            println("🔤 Original Recognized: '$recognizedPhrase'")

            // Apply context-aware preprocessing
            val preprocessedPhrase = ContextBasedDetection().preprocessWithContext(recognizedPhrase, context)
            println("⚙️  After Processing: '$preprocessedPhrase'")

            // Calculate enhanced similarity
            val similarity = enhancedSimilarity.calculatePhoneticSimilarity(
                preprocessedPhrase, expectedPhrase
            )

            println("📈 Enhanced Accuracy Score: ${String.format("%.2f", similarity * 100)}%")
            println("✅ Meets Threshold (90%): ${if (similarity >= 0.90) "YES" else "NO"}")

            // Show detailed phonetic analysis
            val cleanedExpected = expectedPhrase.trim()
                .lowercase()
                .replace(Regex("[^a-zA-Z\\s]"), "") // Remove punctuation/symbols
                .replace(Regex("\\s+"), " ") // Normalize multiple spaces to single space
                .trim() // Final trim after cleanup

            showPhoneticBreakdown(preprocessedPhrase, cleanedExpected)

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestMatch = preprocessedPhrase
                println("🏆 NEW BEST MATCH!")
            }
            println()
        }

        // Final results
        println("=" * 60)
        println("📋 ENHANCED FINAL RESULTS")
        println("=" * 60)
        println("🔧 Best Processed: '$bestMatch'")
        println("🎯 Enhanced Accuracy: ${String.format("%.2f", bestSimilarity * 100)}%")
        println("✅ Accepted: ${if (bestSimilarity >= 0.90) "YES" else "NO"}")
        println("=" * 60)

        // Return only if similarity is 90% or higher
        return if (bestSimilarity >= 0.90) {
            mapOf(expectedPhrase to bestSimilarity)
        } else {
            emptyMap()
        }
    }

    private fun showPhoneticBreakdown(phrase1: String, phrase2: String) {
        val doubleMetaphone = DoubleMetaphone()
        val words1 = phrase1.trim().split("\\s+".toRegex())
        val words2 = phrase2.trim().split("\\s+".toRegex())

        println("   🔍 Phonetic Analysis:")

        val maxWords = maxOf(words1.size, words2.size)
        for (i in 0 until maxWords) {
            val word1 = if (i < words1.size) words1[i] else ""
            val word2 = if (i < words2.size) words2[i] else ""

            if (word1.isNotEmpty() && word2.isNotEmpty()) {
                val meta1 = doubleMetaphone.encode(word1)
                val meta2 = doubleMetaphone.encode(word2)

                val matches = when {
                    meta1.primary == meta2.primary -> "✅ EXACT"
                    meta1.primary == meta2.alternate || meta1.alternate == meta2.primary -> "🟡 CLOSE"
                    meta1.alternate == meta2.alternate && meta1.alternate.isNotEmpty() -> "🟠 ALT"
                    else -> "❌ DIFF"
                }

                println("   • '$word1' [${meta1.primary}] vs '$word2' [${meta2.primary}] → $matches")
            }
        }
    }

    // Helper extension for string repetition
    private operator fun String.times(n: Int): String = this.repeat(n)

}