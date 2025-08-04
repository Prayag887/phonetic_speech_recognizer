package com.prayag.phonetic_speech_recognizer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.apache.commons.lang3.StringUtils
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.codec.language.DoubleMetaphone
import java.io.BufferedReader
import java.io.InputStreamReader

class LanguageHandlers(private val context: Context) {
    private var pluginInstance: PhoneticSpeechRecognizerPlugin? = null

    // Initialize enhanced phonetic system with NLP capabilities
    private val enhancedPhoneticSimilarity = PhoneticSimilarity()
    private val nlpEnhancedMatcher = NlpEnhancedPhoneticMatcher()

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
     * Enhanced word recognition with full NLP integration for severe misrecognitions.
     *
     * @param languageCode the language code for the recognition
     * @param timeoutMillis the timeout in milliseconds for the recognition
     * @param sentence the sentence to recognize
     */
    fun handleWordsRecognition(languageCode: String?, timeoutMillis: Int, sentence: String) {
        Log.d("SpeechRecognition", "🎯 ENHANCED WORD RECOGNITION STARTED")
        Log.d("SpeechRecognition", "Expected sentence: \"$sentence\"")

        if (languageCode == null) {
            println("Missing language code for sentence: $sentence")
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
                    Log.d("SpeechRecognition", "🧠 Detected context: $context")
                    Log.d("SpeechRecognition", "🗣️ Original recognition: ${text.keys.first()}")

                    // Use enhanced correction with full NLP integration
                    correctRecognizedPhraseEnhanced(listOf(text.keys.first()), sentence, context)
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

    /**
     * ENHANCED: Full NLP-powered phrase correction with semantic understanding
     */
    fun correctRecognizedPhraseEnhanced(
        recognizedPhrases: List<String>,
        expectedPhrase: String,
        context: String = ""
    ): Map<String, Double> {
        if (recognizedPhrases.isEmpty()) return emptyMap()

        var bestMatch = ""
        var bestSimilarity = 0.0
        var bestResult: EnhancedPhoneticResult? = null

        val auxiliaryVerbs = setOf("am", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would",
            "shall", "should", "can", "could", "may", "might", "must")

        println("=" * 80)
        println("🧠 ENHANCED NLP-POWERED SPEECH RECOGNITION CORRECTION")
        println("=" * 80)
        println("📝 Expected Phrase: '$expectedPhrase'")
        println("🏷️ Context: '${context.ifEmpty { "general" }}'")
        println("📊 Recognition Candidates: ${recognizedPhrases.size}")
        println("🎯 Using: Traditional Phonetic + NLP Semantic Analysis")
        println()

        val expectedWords = expectedPhrase
            .lowercase()
            .replace(Regex("[^a-zA-Z\\s]"), "")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in auxiliaryVerbs }

        for ((index, recognizedPhrase) in recognizedPhrases.withIndex()) {
            println("--- 🔍 Analyzing Candidate ${index + 1} ---")
            println("🗣️ Original: '$recognizedPhrase'")

            val preprocessedPhrase = ContextBasedDetection().preprocessWithContext(recognizedPhrase, context)
            println("⚙️ Preprocessed: '$preprocessedPhrase'")

            val recognizedWords = preprocessedPhrase
                .lowercase()
                .replace(Regex("[^a-zA-Z\\s]"), "")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

            // Quick exact match check
            val containsAllExpected = expectedWords.all { expectedWord ->
                recognizedWords.any { recognizedWord ->
                    recognizedWord == expectedWord
                }
            }

            if (containsAllExpected) {
                println("✅ PERFECT MATCH: All content words found!")
                println("🎉 Returning expected phrase with 100% confidence")
                return mapOf(expectedPhrase to 1.0)
            }

            // ENHANCED: Use new integrated phonetic analysis with NLP
            println("🧠 Running Enhanced Analysis...")
            val enhancedResult = enhancedPhoneticSimilarity.getEnhancedPhoneticResult(
                expectedPhrase,
                preprocessedPhrase
            )

            println("📊 ENHANCED ANALYSIS RESULTS:")
            println("   Traditional Score: ${String.format("%.2f", enhancedResult.traditionalScore * 100)}%")
            println("   NLP Score: ${String.format("%.2f", enhancedResult.nlpScore * 100)}%")
            println("   Final Fused Score: ${String.format("%.2f", enhancedResult.finalScore * 100)}%")
            println("   Strategy Used: ${enhancedResult.strategy}")
            println("   Confidence Level: ${enhancedResult.confidence}")
            println("   Should Accept: ${enhancedResult.shouldAccept}")

            if (enhancedResult.correctedText != preprocessedPhrase) {
                println("   🔧 NLP Correction: '${enhancedResult.correctedText}'")
            }

            // Show detailed breakdown
            showEnhancedPhoneticBreakdown(preprocessedPhrase, expectedPhrase, enhancedResult)

            if (enhancedResult.finalScore > bestSimilarity) {
                bestSimilarity = enhancedResult.finalScore
                bestMatch = if (enhancedResult.shouldAccept) {
                    enhancedResult.correctedText
                } else {
                    preprocessedPhrase
                }
                bestResult = enhancedResult
                println("🏆 NEW BEST MATCH!")
            }
            println()
        }

        println("=" * 80)
        println("📋 ENHANCED FINAL DECISION")
        println("=" * 80)
        println("🏆 Best Match: '$bestMatch'")
        println("🎯 Final Score: ${String.format("%.2f", bestSimilarity * 100)}%")
        println("🤖 Strategy: ${bestResult?.strategy ?: "Unknown"}")
        println("📊 Confidence: ${bestResult?.confidence ?: "Unknown"}")

        val accepted = bestSimilarity >= 0.75 // Lower threshold with NLP confidence
        println("✅ Decision: ${if (accepted) "ACCEPTED" else "REJECTED"}")

        if (accepted && bestResult?.correctedText != null && bestResult.correctedText != bestMatch) {
            println("🔧 Applied NLP Correction")
            bestMatch = bestResult.correctedText
        }

        println("=" * 80)

        return if (accepted) {
            if (bestSimilarity >= 0.90) {
                // Very high confidence - return expected phrase
                mapOf(expectedPhrase to bestSimilarity)
            } else {
                // Good confidence - return corrected phrase
                mapOf(bestMatch to bestSimilarity)
            }
        } else {
            // Low confidence - return original with low score
            mapOf(bestMatch to bestSimilarity)
        }
    }

    /**
     * LEGACY: Original method kept for backward compatibility
     */
    fun correctRecognizedPhrase(
        recognizedPhrases: List<String>,
        expectedPhrase: String,
        context: String = ""
    ): Map<String, Double> {
        // Redirect to enhanced version
        return correctRecognizedPhraseEnhanced(recognizedPhrases, expectedPhrase, context)
    }

    private fun showEnhancedPhoneticBreakdown(
        recognized: String,
        expected: String,
        result: EnhancedPhoneticResult
    ) {
        val doubleMetaphone = DoubleMetaphone()
        val recognizedWords = recognized.trim().split("\\s+".toRegex())
        val expectedWords = expected.trim().split("\\s+".toRegex())

        println("   🔬 DETAILED PHONETIC BREAKDOWN:")
        println("   Strategy: ${result.strategy}")

        when (result.strategy) {
            "TRADITIONAL_PHONETIC" -> {
                println("   📞 Traditional phonetic matching was more reliable")
                showTraditionalBreakdown(recognizedWords, expectedWords, doubleMetaphone)
            }
            "SEMANTIC_MATCH" -> {
                println("   🧠 NLP semantic analysis provided better match")
                println("   💡 Detected semantic similarity despite phonetic differences")
            }
            "CONTEXTUAL_MATCH" -> {
                println("   🎯 Context-aware matching was decisive")
                println("   📍 Context clues helped resolve ambiguity")
            }
            "NLP_FUSION" -> {
                println("   ⚖️ Combined NLP analysis provided best results")
                println("   🔗 Multiple NLP components contributed to decision")
            }
            else -> {
                showTraditionalBreakdown(recognizedWords, expectedWords, doubleMetaphone)
            }
        }

        if (result.correctedText != recognized) {
            println("   🔧 NLP Corrections Applied:")
            println("      Before: '$recognized'")
            println("      After:  '${result.correctedText}'")
        }
    }

    private fun showTraditionalBreakdown(
        recognizedWords: List<String>,
        expectedWords: List<String>,
        doubleMetaphone: DoubleMetaphone
    ) {
        val maxWords = maxOf(recognizedWords.size, expectedWords.size)
        for (i in 0 until maxWords) {
            val recognizedWord = if (i < recognizedWords.size) recognizedWords[i] else ""
            val expectedWord = if (i < expectedWords.size) expectedWords[i] else ""

            if (recognizedWord.isNotEmpty() && expectedWord.isNotEmpty()) {
                val metaRecognized = doubleMetaphone.doubleMetaphone(recognizedWord)
                val metaExpected = doubleMetaphone.doubleMetaphone(expectedWord)

                val matches = when {
                    metaRecognized == metaExpected -> "✅ EXACT"
                    recognizedWord.lowercase() == expectedWord.lowercase() -> "✅ EXACT TEXT"
                    metaRecognized.isNotEmpty() && metaExpected.isNotEmpty() &&
                            (metaRecognized == metaExpected) -> "🟡 CLOSE"
                    else -> "❌ DIFFERENT"
                }

                println("   • '$recognizedWord' [$metaRecognized] vs '$expectedWord' [$metaExpected] → $matches")
            } else if (recognizedWord.isNotEmpty()) {
                println("   • '$recognizedWord' → ❌ EXTRA WORD")
            } else if (expectedWord.isNotEmpty()) {
                println("   • '$expectedWord' → ❌ MISSING WORD")
            }
        }
    }

    // Helper extension for string repetition
    private operator fun String.times(n: Int): String = this.repeat(n)
}