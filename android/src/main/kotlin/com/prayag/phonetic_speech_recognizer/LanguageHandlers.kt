package com.prayag.phonetic_speech_recognizer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import io.flutter.plugin.common.MethodChannel
import org.apache.commons.lang3.StringUtils

class LanguageHandlers(private val context: Context) {

    // Reference to the main plugin instance to access activeResult
    private var pluginInstance: PhoneticSpeechRecognizerPlugin? = null

    // Method to set the plugin instance
    fun setPluginInstance(plugin: PhoneticSpeechRecognizerPlugin) {
        pluginInstance = plugin
    }

    fun handleAlphabetRecognition(timeoutMillis: Int) {
        val isConnected = isNetworkAvailable(context)
        if (!isConnected) {
            pluginInstance?.activeResult?.error("NETWORK_ERROR", "Network not available", null)
            pluginInstance?.activeResult = null
            return
        }

        val lang = "ne-NP"
        pluginInstance?.startRecognition(
            paragraph = "",
            lang = lang,
            mapper = { text -> Mapper().mapText(text, PhoneticMapping.phoneticNepaliToEnglishMapping) },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    fun handleAllLanguages(timeoutMillis: Int, languageCode: String) {
        Log.d("TAG", "handleAllLanguages: ------------------- $languageCode")
        val isConnected = isNetworkAvailable(context)
        if (!isConnected) {
            pluginInstance?.activeResult?.error("NETWORK_ERROR", "Network not available", null)
            pluginInstance?.activeResult = null
            return
        }

        pluginInstance?.startRecognition(
            paragraph = "",
            lang = languageCode,
            mapper = { text -> Mapper().mapNumber(text, PhoneticMapping.phoneticNepaliToEnglishMapping) },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    fun handleKoreanAlphabetRecognition(timeoutMillis: Int) {
        val isConnected = isNetworkAvailable(context)
        if (!isConnected) {
            pluginInstance?.activeResult?.error("NETWORK_ERROR", "Network not available", null)
            pluginInstance?.activeResult = null
            return
        }

        pluginInstance?.startRecognition(
            paragraph = "",
            lang = "ne-NP",
            mapper = { text -> Mapper().mapText(text, PhoneticMapping.phoneticKoreanMapping) },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    fun handleNumberRecognition(timeoutMillis: Int) {
        val isConnected = isNetworkAvailable(context)
        if (!isConnected) {
            pluginInstance?.activeResult?.error("NETWORK_ERROR", "Network not available", null)
            pluginInstance?.activeResult = null
            return
        }

        pluginInstance?.startRecognition(
            paragraph = "",
            lang = "hi-IN",
            mapper = { text -> Mapper().mapNumber(text, PhoneticMapping.phoneticNumbersMapping) },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    fun handleWordsRecognition(languageCode: String?, timeoutMillis: Int, sentence: String) {
        Log.d("SpeechRecognition", "SENTENCE FROM FLUTTER SIDE: \"$sentence\"")
        if (languageCode == null) {
            println("this is sentence $sentence")
            pluginInstance?.activeResult?.error("INVALID_LANG", "Language code required", null)
            pluginInstance?.activeResult = null
            return
        }

        val isConnected = isNetworkAvailable(context)
        if (!isConnected) {
            pluginInstance?.activeResult?.error("NETWORK_ERROR", "Network not available", null)
            pluginInstance?.activeResult = null
            return
        }

        pluginInstance?.startRecognition(
            paragraph = "",
            lang = languageCode,
            mapper = { text ->
                if (languageCode == "en-US") correctRecognizedPhrase(listOf(text), sentence) else text
            },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    fun handleParagraphMapping(languageCode: String?, timeoutMillis: Int, paragraph: String) {
        Log.d("SpeechRecognition", "PARAGRAPH FROM FLUTTER SIDE: \"$paragraph\"")
        if (languageCode == null) {
            println("this is paragraph $paragraph")
            pluginInstance?.activeResult?.error("INVALID_LANG", "Language code required", null)
            pluginInstance?.activeResult = null
            return
        }

        val isConnected = isNetworkAvailable(context)
        if (!isConnected) {
            pluginInstance?.activeResult?.error("NETWORK_ERROR", "Network not available", null)
            pluginInstance?.activeResult = null
            return
        }

        val words = paragraph.split(" ").map { it.trim() }.filter { it.isNotEmpty() }

        pluginInstance?.startRecognition(
            paragraph = paragraph,
            lang = languageCode,
            mapper = { text ->
                if (languageCode == "en-US") {
                    pluginInstance?.updateHighlightedText(text, words, paragraph)
                }
                text
            },
            timeoutMillis = timeoutMillis,
            keepListening = true
        )
    }

    fun handleJapaneseRecognition(timeoutMillis: Int, type: String) {
        val isConnected = isNetworkAvailable(context)
        if (!isConnected) {
            pluginInstance?.activeResult?.error("NETWORK_ERROR", "Network not available", null)
            pluginInstance?.activeResult = null
            return
        }

        val lang = "ne-NP"
        pluginInstance?.startRecognition(
            paragraph = "",
            lang = lang,
            mapper = { text ->
                Mapper().mapNumber(
                    text,
                    PhoneticMapping.phoneticJapaneseAlphabetMapping
                )
            },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    fun handleKoreanNumberRecognition(timeoutMillis: Int, type: String) {
        val isConnected = isNetworkAvailable(context)
        if (!isConnected) {
            pluginInstance?.activeResult?.error("NETWORK_ERROR", "Network not available", null)
            pluginInstance?.activeResult = null
            return
        }

        val lang = "ne-NP"
        pluginInstance?.startRecognition(
            paragraph = "",
            lang = lang,
            mapper = { text ->
                Mapper().mapNumber(
                    text,
                    PhoneticMapping.phoneticKoreanNumberMapping
                )
            },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }

    // Helper method for correcting recognized phrases (you'll need to implement this)
    fun correctRecognizedPhrase(recognizedPhrases: List<String>, expectedPhrase: String): String {
        if (recognizedPhrases.isEmpty()) return ""

        var bestMatch = recognizedPhrases[0]
        var bestSimilarity = 0.0

        // Iterate through all recognized phrases and calculate the best match based on similarity
        for (recognizedPhrase in recognizedPhrases) {
            val phoneticSimilarity = PhoneticSimilarity().calculatePhoneticSimilarity(recognizedPhrase, expectedPhrase)

//      this is to check the string similarity based on 0 to 1, 1 being best match.
            val stringSimilarity = 1.0 - (StringUtils.getLevenshteinDistance(recognizedPhrase, expectedPhrase).toDouble() / kotlin.math.max(recognizedPhrase.length, expectedPhrase.length))
            val similarity = (phoneticSimilarity + stringSimilarity) / 2.0  // Combine both phonetic and string similarity

            // Keep track of the best match
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestMatch = recognizedPhrase
            }

            Log.d("SpeechRecognition", "Recognized: \"$recognizedPhrase\" | Phonetic Similarity: $phoneticSimilarity | String Similarity: $stringSimilarity | Combined Similarity: $similarity | Best Similarity: $bestSimilarity")
        }

        if (bestSimilarity >= 0.7) {
            Log.d("SpeechRecognition", "Returning expectedPhrase match: $expectedPhrase")
            return expectedPhrase
        }

        Log.d("SpeechRecognition", "Returning best match: $bestMatch")
        return bestMatch
    }
}