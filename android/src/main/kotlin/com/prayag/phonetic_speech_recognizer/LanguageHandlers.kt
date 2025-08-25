package com.prayag.phonetic_speech_recognizer

import android.content.Context
import android.util.Log

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
     * Handles alphabet recognition using Vosk
     * @param timeoutMillis the timeout in milliseconds for the recognition
     */
    fun handleAlphabetRecognition(languageCode: String?, timeoutMillis: Int, sentence: String) {
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
     * Handles recognition for all languages using Vosk
     *
     * @param timeoutMillis the timeout in milliseconds for the recognition
     * @param languageCode the language code for the recognition
     */
    fun handleAllLanguages(languageCode: String?, timeoutMillis: Int, sentence: String) {
        Log.d("VoskSpeech", "SENTENCE FROM FLUTTER SIDE: \"$sentence\"")
        if (languageCode == null) {
            pluginInstance?.activeResult?.error("INVALID_LANG", "Language code required", null)
            pluginInstance?.activeResult = null
            return
        }

        pluginInstance?.startVoskRecognition(timeoutMillis, sentence)
    }

    /**
     * Handles Korean alphabet recognition using Vosk
     *
     * @param timeoutMillis the timeout in milliseconds for the recognition
     */
    fun handleKoreanAlphabetRecognition(languageCode: String?, timeoutMillis: Int, sentence: String) {
        Log.d("VoskSpeech", "SENTENCE FROM FLUTTER SIDE: \"$sentence\"")
        if (languageCode == null) {
            pluginInstance?.activeResult?.error("INVALID_LANG", "Language code required", null)
            pluginInstance?.activeResult = null
            return
        }

        pluginInstance?.startVoskRecognition(timeoutMillis, sentence)
    }

    /**
     * Handles number recognition using Vosk
     *
     * @param timeoutMillis the timeout in milliseconds for the recognition
     * @param sentence the sentence context for better recognition
     */
    fun handleNumberRecognition(timeoutMillis: Int, sentence: String) {
        val lang = if (listOf("0","1", "2", "3", "4", "5", "6", "7", "8", "9", "10").any { sentence.contains(it) }) {
            "ne-NP"
        } else {
            "hi-IN"
        }

        pluginInstance?.startRecognition(
            paragraph = "",
            lang = lang,
            mapper = { text -> Mapper().mapNumbersIncludingSpellings(text.keys.first(), PhoneticMapping.phoneticNumbersMapping) },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    /**
     * Handles word recognition using Vosk
     *
     * @param languageCode the language code for the recognition
     * @param timeoutMillis the timeout in milliseconds for the recognition
     * @param sentence the sentence to recognize
     */
    fun handleWordsRecognition(languageCode: String?, timeoutMillis: Int, sentence: String) {
        Log.d("VoskSpeech", "SENTENCE FROM FLUTTER SIDE: \"$sentence\"")

        if (languageCode == null) {
            pluginInstance?.activeResult?.error("INVALID_LANG", "Language code required", null)
            pluginInstance?.activeResult = null
            return
        }

        val trimmed = sentence.trim()
        val wordCount = trimmed.split("\\s+".toRegex()).size
        val isSingleWord = wordCount == 1
        var useGrammar  = false

        Log.d("VoskSpeech", "Word count: $wordCount, Single word? $isSingleWord")

        if (isSingleWord) {
            // Handle differently if needed
            pluginInstance?.startRecognition(
                paragraph = "", // Keep empty for word recognition
                lang = languageCode,
                mapper = { text ->
                    if (languageCode == "en-US") {
                        val context = ContextBasedDetection().detectContext(sentence)
                        Log.d("SpeechRecognition", "Original recognition: ${text.keys.first()}")

                        try {
                            // Get detailed analysis and return it directly
                            val detailedAnalysis = getDetailedPhraseAnalysis(
                                listOf(text.keys.first()),
                                sentence,
                                context
                            )

                            Log.d("SpeechRecognition", "Returning detailed analysis directly: $detailedAnalysis")

                            // Return the detailed analysis directly instead of storing it
                            detailedAnalysis
                        } catch (e: Exception) {
                            Log.e("SpeechRecognition", "Error getting detailed analysis", e)
                            // Fallback to simple correction
                            val simpleResult = correctRecognizedPhrase(listOf(text.keys.first()), sentence, context)
                            mapOf(
                                "correctedPhrase" to simpleResult.keys.first(),
                                "confidence" to simpleResult.values.first(),
                                "detailedAnalysis" to false
                            )
                        }
                    } else {
                        // For non-English, wrap in expected format
                        mapOf(
                            "correctedPhrase" to text.keys.first(),
                            "confidence" to text.values.first(),
                            "detailedAnalysis" to false
                        )
                    }
                },
                timeoutMillis = timeoutMillis,
                keepListening = false
            )
        } else {
            pluginInstance?.startVoskRecognition(timeoutMillis = timeoutMillis, sentence = sentence, useGrammar = true)
        }

    }


    /**
     * Handles paragraph mapping using Vosk
     *
     * @param languageCode the language code for the recognition
     * @param timeoutMillis the timeout in milliseconds for the recognition
     * @param paragraph the paragraph to recognize
     */
    fun handleParagraphMapping(languageCode: String?, timeoutMillis: Int, paragraph: String) {
        val getPartialTexts = true
        Log.d("VoskSpeech", "Paragraph FROM FLUTTER SIDE: \"$paragraph\"")
        if (languageCode == null) {
            pluginInstance?.activeResult?.error("INVALID_LANG", "Language code required", null)
            pluginInstance?.activeResult = null
            return
        }

//        pluginInstance?.startRecognition(
//            paragraph = paragraph,
//            lang = languageCode,
//            mapper = { text ->
//                if (languageCode == "en-US") {
//                    pluginInstance?.updateHighlightedText(text.keys.first(), words, paragraph)
//                }
//                text
//            },
//            timeoutMillis = timeoutMillis,
//            keepListening = true
//        )

//        pluginInstance?.startVoskRecognition(timeoutMillis, paragraph, getPartialTexts)
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
     * Handles Japanese recognition using Vosk
     *
     * @param timeoutMillis the timeout in milliseconds for the recognition
     * @param type the type of recognition
     */
    fun handleJapaneseRecognition(languageCode: String?, timeoutMillis: Int, sentence: String) {
        Log.d("VoskSpeech", "SENTENCE FROM FLUTTER SIDE: \"$sentence\"")
        if (languageCode == null) {
            pluginInstance?.activeResult?.error("INVALID_LANG", "Language code required", null)
            pluginInstance?.activeResult = null
            return
        }

        pluginInstance?.startVoskRecognition(timeoutMillis, sentence)
    }

    /**
     * Handles Korean number recognition using Vosk
     *
     * @param timeoutMillis the timeout in milliseconds for the recognition
     * @param type the type of recognition
     */
    fun handleKoreanNumberRecognition(languageCode: String?, timeoutMillis: Int, sentence: String) {
        Log.d("VoskSpeech", "SENTENCE FROM FLUTTER SIDE: \"$sentence\"")
        if (languageCode == null) {
            pluginInstance?.activeResult?.error("INVALID_LANG", "Language code required", null)
            pluginInstance?.activeResult = null
            return
        }

        pluginInstance?.startVoskRecognition(timeoutMillis, sentence)
    }

    /**
     * Simple phrase correction - now just returns the original phrase since Vosk provides direct results
     */
    fun correctRecognizedPhrase(
        recognizedPhrases: List<String>,
        expectedPhrase: String,
        context: String = ""
    ): Map<String, Double> {
        return if (recognizedPhrases.isNotEmpty()) {
            mapOf(recognizedPhrases.first() to 1.0)
        } else {
            mapOf("" to 0.0)
        }
    }

    fun getDetailedPhraseAnalysis(
        recognizedPhrases: List<String>,
        expectedPhrase: String,
        context: String = ""
    ): Map<String, Any> {
        val simpleResult = correctRecognizedPhrase(recognizedPhrases, expectedPhrase, context)
        return mapOf(
            "correctedPhrase" to simpleResult.keys.first(),
            "confidence" to simpleResult.values.first(),
            "detailedAnalysis" to false
        )
    }
}