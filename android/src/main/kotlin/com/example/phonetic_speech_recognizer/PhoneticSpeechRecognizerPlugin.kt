package com.example.phonetic_speech_recognizer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import org.apache.commons.lang3.StringUtils
import androidx.core.content.ContextCompat
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.codec.language.DoubleMetaphone
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.*
import kotlin.math.min

class PhoneticSpeechRecognizerPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
  private lateinit var context: Context
  private lateinit var channel: MethodChannel
  private var speechRecognizer: SpeechRecognizer? = null
  private var activeResult: MethodChannel.Result? = null
  private var timeoutHandler: Handler? = null
  private var timeoutRunnable: Runnable? = null

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    context = binding.applicationContext
    channel = MethodChannel(binding.binaryMessenger, "phonetic_speech_recognizer")
    channel.setMethodCallHandler(this)
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "recognize" -> {
        if (activeResult != null) {
          result.error("ALREADY_ACTIVE", "Recognition already active", null)
          return
        }
        activeResult = result

        val type = call.argument<String>("type")
        val languageCode = call.argument<String>("languageCode")
        val timeoutMillis = call.argument<Int>("timeout") ?: 0
        val sentence = call.argument<String>("sentence") ?: ""

        when (type) {
          "alphabet" -> handleAlphabetRecognition(timeoutMillis)
          "koreanAlphabet" -> handleKoreanAlphabetRecognition(timeoutMillis)
          "number" -> handleNumberRecognition(timeoutMillis)
          "englishWordsOrSentence" -> handleWordsRecognition(languageCode, timeoutMillis, sentence)
          "hiraganaJapanese" -> handleJapaneseRecognition(timeoutMillis, "hiragana")
          "katakanaJapanese" -> handleJapaneseRecognition(timeoutMillis, "katakana")
          else -> result.error("INVALID_TYPE", "Unsupported type", null)
        }
      }

      "stopRecognition" -> {
        stopRecognition(result)
      }

      else -> result.notImplemented()
    }
  }

  private fun stopRecognition(result: MethodChannel.Result) {
    try {
      speechRecognizer?.cancel()
      cleanup()
      result.success(true)
    } catch (e: Exception) {
      result.error("STOP_ERROR", "Failed to stop recognition", e.message)
    }
  }

  private fun handleJapaneseRecognition(timeoutMillis: Int, type: String) {
    isNetworkAvailable(context)
    val lang = "ne-NP"
    if (type == "hiragana") {
      startRecognition(
        returnOutOfMap = false,
        lang = lang,
        mapper = { text ->
          //mapText returns only the mapped value. If it picks up the noise on top of users voice then response wont be provided
          mapText(
            text,
            PhoneticMapping.phoneticHiraganaToNepaliAndEnglishMapping
          )
        },
        timeoutMillis = timeoutMillis
      )
    } else{
      startRecognition(
        returnOutOfMap = false,
        lang = lang,
        mapper = { text ->
          //mapText returns only the mapped value (). If it picks up the noise on top of users voice then response wont be provided
          mapText(
            text,
            PhoneticMapping.phoneticKatakanaToNepaliAndEnglishMapping
          )
        },
        timeoutMillis = timeoutMillis
      )
    }
  }

  private fun handleAlphabetRecognition(timeoutMillis: Int) {
    val isConnected = isNetworkAvailable(context)
    val lang = if (isConnected) "ne-NP" else "hi-IN"
    startRecognition(
      returnOutOfMap = false,
      lang = lang,
      //mapText returns only the mapped value (english alphabets in this case). If it picks up the noise on top of users voice then response wont be provided
      mapper = { text -> mapText(text, PhoneticMapping.phoneticNepaliToEnglishMapping) },
      timeoutMillis = timeoutMillis
    )
  }

  private fun handleKoreanAlphabetRecognition(timeoutMillis: Int) {
    startRecognition(
      returnOutOfMap = false,
      lang = "ne-NP",
      //mapText returns only the mapped value (korean alphabets in this case). If it picks up the noise on top of users voice then response wont be provided
      mapper = { text -> mapText(text, PhoneticMapping.phoneticKoreanMapping) },
      timeoutMillis = timeoutMillis
    )
  }

  private fun handleNumberRecognition(timeoutMillis: Int) {
    startRecognition(
      returnOutOfMap = true,
      lang = "hi-IN",
      //mapNumber has to only the mapped value (number in this case). If it picks up the noise on top of users voice then response wont be provided
      mapper = { text -> mapNumber(text, PhoneticMapping.phoneticNumbersMapping) },
      timeoutMillis = timeoutMillis
    )
  }

  private fun handleWordsRecognition(languageCode: String?, timeoutMillis: Int, sentence: String) {

    Log.d("SpeechRecognition", "SENTENCE FROM FLUTTER SIDE: \"$sentence\"")
    if (languageCode == null) {
      println("this is sentence $sentence")
      activeResult?.error("INVALID_LANG", "Language code required", null)
      activeResult = null
      return
    }
    startRecognition(
      returnOutOfMap = true,
      lang = languageCode,
      mapper = { text ->
        if (languageCode == "en-US") correctRecognizedPhrase(listOf(text), sentence) else text
      },
      timeoutMillis = timeoutMillis
    )
  }

  private fun startRecognition(lang: String, mapper: (String) -> String, timeoutMillis: Int, returnOutOfMap:Boolean) {
    if (speechRecognizer != null) {
      speechRecognizer?.cancel()
      cleanup()
    }

    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
      putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
      putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 7)
    }

    timeoutHandler = Handler(context.mainLooper)
    if (timeoutMillis > 0) {
      timeoutRunnable = Runnable {
        activeResult?.error("TIMEOUT", "Speech recognition timed out", null)
        speechRecognizer?.cancel()
        cleanup()
      }
      timeoutHandler?.postDelayed(timeoutRunnable!!, timeoutMillis.toLong())
    }

    speechRecognizer?.setRecognitionListener(object : RecognitionListener {
      override fun onResults(results: Bundle) {
        cancelTimeout()
        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val recognizedText = matches?.firstOrNull() ?: ""
        activeResult?.success(mapper(recognizedText))
        cleanup()
      }

      override fun onError(error: Int) {
        cancelTimeout()
        activeResult?.error("SPEECH_ERROR", getErrorText(error), null)
        cleanup()
      }

      override fun onReadyForSpeech(params: Bundle?) {}
      override fun onBeginningOfSpeech() {}
      override fun onRmsChanged(rmsdB: Float) {}
      override fun onBufferReceived(buffer: ByteArray?) {}
      override fun onEndOfSpeech() {}
      override fun onPartialResults(partialResults: Bundle?) {}
      override fun onEvent(eventType: Int, params: Bundle?) {}
    })

    speechRecognizer?.startListening(intent)
  }


  private fun cancelTimeout() {
    timeoutRunnable?.let { timeoutHandler?.removeCallbacks(it) }
    timeoutHandler = null
    timeoutRunnable = null
  }

  private fun mapNumber(text: String, mapping: Map<String, List<String>>): String {
    val normalizedText = text.lowercase(Locale.ROOT)
    val reversedMapping = mutableMapOf<String, MutableList<String>>()
    mapping.forEach { (key, values) ->
      values.forEach { pronunciation ->
        val normalizedPronunciation = pronunciation.lowercase(Locale.ROOT)
        reversedMapping.getOrPut(normalizedPronunciation) { mutableListOf() }.add(key)
      }
    }
    val matchedKeys = reversedMapping[normalizedText]?.distinct() ?: listOf(text.uppercase(Locale.ROOT))
    return matchedKeys.joinToString(", ")
  }

  private fun mapText(text: String, mapping: Map<String, List<String>>): String {
    val normalizedText = text.lowercase(Locale.ROOT)
    val matchedKeys = mapping.entries
      .filter { (_, pronunciations) ->
        pronunciations.any { it.lowercase(Locale.ROOT) == normalizedText }
      }
      .map { it.key }
      .distinct()

    return if (matchedKeys.isNotEmpty()) {
      matchedKeys.joinToString(", ")
    } else {
      ""  // Return empty string if no match found
    }
  }

  private fun getErrorText(errorCode: Int): String = when (errorCode) {
    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
    SpeechRecognizer.ERROR_CLIENT -> "Client error"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions needed"
    SpeechRecognizer.ERROR_NETWORK -> "Network error"
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Busy"
    SpeechRecognizer.ERROR_SERVER -> "Server error"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech"
    else -> "Unknown error"
  }

  private fun cleanup() {
    cancelTimeout()
    speechRecognizer?.destroy()
    speechRecognizer = null
    activeResult = null
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


  private fun correctRecognizedPhrase(recognizedPhrases: List<String>, expectedPhrase: String): String {
    if (recognizedPhrases.isEmpty()) return ""

    val doubleMetaphone = DoubleMetaphone()

    // Function to get the phonetic codes from a phrase
    fun getPhoneticCodes(text: String): List<String> {
      return text.lowercase()
        .replace(Regex("[^a-z\\s]"), "")  // Remove non-alphabet characters
        .split("\\s+".toRegex()) // Split by spaces (fix for multi-word phrases)
        .map { word ->
          // Ensure non-null phonetic codes, fallback to word if null
          doubleMetaphone.doubleMetaphone(word) ?: word
        }
    }

    // Function to calculate phonetic similarity between two phrases
    fun calculatePhoneticSimilarity(phrase1: String, phrase2: String): Double {
      val phonetics1 = getPhoneticCodes(phrase1)
      val phonetics2 = getPhoneticCodes(phrase2)

      // Allow for one word difference between phrases
      if (kotlin.math.abs(phonetics1.size - phonetics2.size) > 1) return 0.0

      var matchCount = 0
      val maxLength = kotlin.math.max(phonetics1.size, phonetics2.size)

      // Compare phonetic codes between two phrases
      phonetics1.forEachIndexed { index, code1 ->
        if (index < phonetics2.size) {
          val code2 = phonetics2[index]
          if (code1 == code2) matchCount++
        }
      }

      // Return the similarity as a fraction
      return matchCount.toDouble() / maxLength
    }

    var bestMatch = recognizedPhrases[0]
    var bestSimilarity = 0.0

    // Iterate through all recognized phrases and calculate the best match based on similarity
    for (recognizedPhrase in recognizedPhrases) {
      val phoneticSimilarity = calculatePhoneticSimilarity(recognizedPhrase, expectedPhrase)

//      this is to check the string similarity based on 0 to 1, 1 being best match.
      val stringSimilarity = 1.0 - (StringUtils.getLevenshteinDistance(recognizedPhrase, expectedPhrase).toDouble() / kotlin.math.max(recognizedPhrase.length, expectedPhrase.length))
      val similarity = (phoneticSimilarity + stringSimilarity) / 2.0  // Combine both phonetic and string similarity

      // Keep track of the best match
      if (similarity > bestSimilarity) {
        bestSimilarity = similarity
        bestMatch = recognizedPhrase
      }

      Log.d("SpeechRecognition", "Recognized: \"$recognizedPhrase\" | Phonetic Similarity: $phoneticSimilarity | String Similarity: $stringSimilarity | Combined Similarity: $similarity")
    }

    // Log the final decision
    Log.d("SpeechRecognition", "Best match: \"$bestMatch\" | Similarity: $bestSimilarity")

    if (bestSimilarity >= 0.5) {
      Log.d("SpeechRecognition", "Returning expected phrase: $expectedPhrase")
      return expectedPhrase
    }

    Log.d("SpeechRecognition", "Returning best match: $bestMatch")
    return bestMatch
  }
}