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
    fun handleAlphabetRecognition(timeoutMillis: Int) {
        pluginInstance?.startVoskRecognition(timeoutMillis, "")
    }

    /**
     * Handles recognition for all languages using Vosk
     *
     * @param timeoutMillis the timeout in milliseconds for the recognition
     * @param languageCode the language code for the recognition
     */
    fun handleAllLanguages(timeoutMillis: Int, languageCode: String) {
        pluginInstance?.startVoskRecognition(timeoutMillis, "")
    }

    /**
     * Handles Korean alphabet recognition using Vosk
     *
     * @param timeoutMillis the timeout in milliseconds for the recognition
     */
    fun handleKoreanAlphabetRecognition(timeoutMillis: Int) {
        pluginInstance?.startVoskRecognition(timeoutMillis, "")
    }

    /**
     * Handles number recognition using Vosk
     *
     * @param timeoutMillis the timeout in milliseconds for the recognition
     * @param sentence the sentence context for better recognition
     */
    fun handleNumberRecognition(timeoutMillis: Int, sentence: String) {
        pluginInstance?.startVoskRecognition(timeoutMillis, sentence)
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

        pluginInstance?.startVoskRecognition(timeoutMillis, sentence)
    }

    /**
     * Handles paragraph mapping using Vosk
     *
     * @param languageCode the language code for the recognition
     * @param timeoutMillis the timeout in milliseconds for the recognition
     * @param paragraph the paragraph to recognize
     */
    fun handleParagraphMapping(languageCode: String?, timeoutMillis: Int, paragraph: String) {
        Log.d("VoskSpeech", "PARAGRAPH FROM FLUTTER SIDE: \"$paragraph\"")
        if (languageCode == null) {
            pluginInstance?.activeResult?.error("INVALID_LANG", "Language code required", null)
            pluginInstance?.activeResult = null
            return
        }

        pluginInstance?.startVoskRecognition(timeoutMillis, paragraph)
    }

    /**
     * Handles Japanese recognition using Vosk
     *
     * @param timeoutMillis the timeout in milliseconds for the recognition
     * @param type the type of recognition
     */
    fun handleJapaneseRecognition(timeoutMillis: Int, type: String) {
        pluginInstance?.startVoskRecognition(timeoutMillis, "")
    }

    /**
     * Handles Korean number recognition using Vosk
     *
     * @param timeoutMillis the timeout in milliseconds for the recognition
     * @param type the type of recognition
     */
    fun handleKoreanNumberRecognition(timeoutMillis: Int, type: String) {
        pluginInstance?.startVoskRecognition(timeoutMillis, "")
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
}