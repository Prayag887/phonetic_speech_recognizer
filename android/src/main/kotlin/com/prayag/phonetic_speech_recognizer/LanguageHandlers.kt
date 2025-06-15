package com.prayag.phonetic_speech_recognizer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import io.flutter.plugin.common.MethodChannel
import org.apache.commons.lang3.StringUtils
import java.net.HttpURLConnection
import java.net.URL
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader

class LanguageHandlers(private val context: Context) {
    private var pluginInstance: PhoneticSpeechRecognizerPlugin? = null

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
            mapper = { text -> Mapper().mapText(text.keys.first(), PhoneticMapping.phoneticNepaliToEnglishMapping) },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    fun handleAllLanguages(timeoutMillis: Int, languageCode: String) {
        val isConnected = isNetworkAvailable(context)
        if (!isConnected) {
            pluginInstance?.activeResult?.error("NETWORK_ERROR", "Network not available", null)
            pluginInstance?.activeResult = null
            return
        }

        pluginInstance?.startRecognition(
            paragraph = "",
            lang = languageCode,
            mapper = { text -> Mapper().mapNumber(text.keys.first(), PhoneticMapping.phoneticNepaliToEnglishMapping) },
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
            mapper = { text -> Mapper().mapText(text.keys.first(), PhoneticMapping.phoneticKoreanMapping) },
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
            mapper = { text -> Mapper().mapNumber(text.keys.first(), PhoneticMapping.phoneticNumbersMapping) },
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
                if (languageCode == "en-US") correctRecognizedPhrase(listOf(text.keys.first()), sentence) else text
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
                    pluginInstance?.updateHighlightedText(text.keys.first(), words, paragraph)
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
                    text.keys.first(),
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
                    text.keys.first(),
                    PhoneticMapping.phoneticKoreanNumberMapping
                )
            },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: run {
                Log.e("NetworkCheck", "ConnectivityManager not available")
                return false
            }

        val isConnected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: run {
                Log.e("NetworkCheck", "No active network")
                return false
            }
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: run {
                Log.e("NetworkCheck", "No network capabilities")
                return false
            }
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }

//        if (isConnected) {
//            Log.i("NetworkCheck", "Network connection detected, testing speed...")
//
//            // Perform ping test in background thread
//            Thread {
//                try {
//                    Log.i("NetworkCheck", "Starting ping test to google.com...")
//                    val startTime = System.currentTimeMillis()
//
//                    val process = Runtime.getRuntime().exec("ping -c 1 google.com")
//                    val exitCode = process.waitFor()
//
//                    if (exitCode == 0) {
//                        val endTime = System.currentTimeMillis()
//                        var pingTime = endTime - startTime
//
//                        // Parse ping output for more accurate timing
//                        val reader = BufferedReader(InputStreamReader(process.inputStream))
//                        var line: String?
//
//                        while (reader.readLine().also { line = it } != null) {
//                            Log.d("NetworkCheck", "Ping output: $line")
//
//                            // Extract time from ping output (format: time=XX.X ms)
//                            line?.let { output ->
//                                val timeRegex = "time=([0-9.]+)".toRegex()
//                                val matchResult = timeRegex.find(output)
//                                matchResult?.let {
//                                    pingTime = it.groupValues[1].toDouble().toLong()
//                                }
//                            }
//                        }
//
//                        val speedCategory = when {
//                            pingTime <= 50 -> "FAST"
//                            pingTime <= 150 -> "AVERAGE"
//                            else -> "SLOW"
//                        }
//
//                        Log.i("NetworkCheck", "Ping successful! Time: ${pingTime}ms - Internet Speed: $speedCategory")
//
//                        // Print to console as well
//                        println("Internet Speed: $speedCategory (${pingTime}ms)")
//
//                    } else {
//                        Log.e("NetworkCheck", "Ping failed with exit code: $exitCode")
//
//                        // Read error stream
//                        val errorReader = BufferedReader(InputStreamReader(process.errorStream))
//                        var errorLine: String?
//                        while (errorReader.readLine().also { errorLine = it } != null) {
//                            Log.e("NetworkCheck", "Ping error: $errorLine")
//                        }
//
//                        Log.w("NetworkCheck", "Network available but ping failed - connection may be limited")
//                    }
//
//                } catch (e: Exception) {
//                    Log.e("NetworkCheck", "Exception during ping test: ${e.message}", e)
//                }
//            }.start()
//
//        }
//        else {
//            Log.e("NetworkCheck", "No internet connection available")
//        }

        return isConnected
    }

    // Helper method for correcting recognized phrases based on phonetic similarity
    fun correctRecognizedPhrase(
        recognizedPhrases: List<String>,
        expectedPhrase: String
    ): Map<String, Double> {
        if (recognizedPhrases.isEmpty()) return mapOf("" to 0.0)

        var bestMatch = recognizedPhrases[0]
        var bestSimilarity = 0.0

        for (recognizedPhrase in recognizedPhrases) {
            val phoneticSimilarity = PhoneticSimilarity().calculatePhoneticSimilarity(recognizedPhrase, expectedPhrase)

            val stringSimilarity = 1.0 - (
                    StringUtils.getLevenshteinDistance(recognizedPhrase, expectedPhrase).toDouble() /
                            kotlin.math.max(recognizedPhrase.length, expectedPhrase.length)
                    )

            val similarity = (phoneticSimilarity * 0.5) + (stringSimilarity * 0.5)

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestMatch = recognizedPhrase
            }
        }

        // Count words in expected phrase
        val wordCount = expectedPhrase.trim().split("\\s+".toRegex()).size

        // Set threshold based on word count
        val threshold = if (wordCount <= 3) 0.90 else 0.85

        return if (bestSimilarity >= threshold) {
            mapOf(expectedPhrase to bestSimilarity)
        } else {
            mapOf(bestMatch to bestSimilarity)
        }
    }
}