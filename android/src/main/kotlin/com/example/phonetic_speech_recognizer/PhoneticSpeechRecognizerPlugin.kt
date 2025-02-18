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
  private var isListening = false
  private val speakLoud : String = "Please speak clearly and loudly in a silent environment."

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
        val languageCode = call.argument<String>("languageCode") ?: "ja-JP"
        val timeoutMillis = call.argument<Int>("timeout")!!
        val sentence = call.argument<String>("sentence") ?: ""

        Log.d("TAG", "THIS IS LANGUAGE CODE: $languageCode")

        Log.d("TAG", "onMethodCall: ----------------- $timeoutMillis ")
        when (type) {
          "alphabet" -> handleAlphabetRecognition(timeoutMillis)
          "koreanAlphabet" -> handleKoreanAlphabetRecognition(timeoutMillis)
          "number" -> handleNumberRecognition(timeoutMillis)
          "englishWordsOrSentence" -> handleWordsRecognition(languageCode, timeoutMillis, sentence)
          "japaneseAlphabet" -> handleJapaneseRecognition(timeoutMillis, "hiragana")
          "japaneseNumber" -> handleJapaneseNumberRecognition(timeoutMillis, "katakana")
          "allLanguageSupport" -> handleAllLanguages(timeoutMillis, languageCode)
          else -> result.error("INVALID_TYPE", "Unsupported type", null)
        }
      }

      "stopRecognition" -> {
        stopRecognition(result)
      }

      "isListening" -> {
        result.success(isListening)  // Return whether the mic is active
      }

      else -> result.notImplemented()
    }
  }

  private fun stopRecognition(result: MethodChannel.Result) {
    try {
      speechRecognizer?.cancel()
      cleanup()
      result.success(true)
      isListening = false
    } catch (e: Exception) {
      result.error("STOP_ERROR", "Failed to stop recognition", e.message)
    }
  }

  private fun handleJapaneseRecognition(timeoutMillis: Int, type: String) {
    isNetworkAvailable(context)
    val lang = "ne-NP"
    val jpLang = "ja-JP"
      startRecognition(
        nativeLang = jpLang,
        lang = lang,
        mapper = { text ->
          //mapText returns only the mapped value. If it picks up the noise on top of users voice then response wont be provided
          mapNumber(
            text,
            PhoneticMapping.phoneticJapaneseAlphabetMapping
          )
        },
        timeoutMillis = timeoutMillis
      )
  }

  private fun handleJapaneseNumberRecognition(timeoutMillis: Int, type: String) {
    isNetworkAvailable(context)
    val lang = "ne-NP"
    val jpLang = "ja-JP"
      startRecognition(
        nativeLang = jpLang,
        lang = lang,
        mapper = { text ->
          //mapText returns only the mapped value. If it picks up the noise on top of users voice then response wont be provided
          mapNumber(
            text,
            PhoneticMapping.phoneticJapaneseNumberMapping
          )
        },
        timeoutMillis = timeoutMillis
      )
  }

  private fun handleAlphabetRecognition(timeoutMillis: Int) {
    val isConnected = isNetworkAvailable(context)
    val lang = "ne-NP"
//    val lang = if (isConnected) "ne-NP" else "hi-IN"
    startRecognition(
      nativeLang = "",
      lang = lang,
      //mapText returns only the mapped value (english alphabets in this case). If it picks up the noise on top of users voice then response wont be provided
      mapper = { text -> mapText(text, PhoneticMapping.phoneticNepaliToEnglishMapping) },
      timeoutMillis = timeoutMillis
    )
  }
  private fun handleAllLanguages(timeoutMillis: Int, languageCode: String) {
    Log.d("TAG", "handleAllLanguages: ------------------- $languageCode")
    startRecognition(
      nativeLang = "",
      lang = languageCode,
      //mapText returns only the mapped value (english alphabets in this case). If it picks up the noise on top of users voice then response wont be provided
      mapper = { text -> mapNumber(text, PhoneticMapping.phoneticNepaliToEnglishMapping) },
      timeoutMillis = timeoutMillis
    )
  }

  private fun handleKoreanAlphabetRecognition(timeoutMillis: Int) {
    startRecognition(
      nativeLang = "",
      lang = "ne-NP",
      //mapText returns only the mapped value (korean alphabets in this case). If it picks up the noise on top of users voice then response wont be provided
      mapper = { text -> mapText(text, PhoneticMapping.phoneticKoreanMapping) },
      timeoutMillis = timeoutMillis
    )
  }

  private fun handleNumberRecognition(timeoutMillis: Int) {
    startRecognition(
      nativeLang = "",
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
      nativeLang = "",
      lang = languageCode,
      mapper = { text ->
        if (languageCode == "en-US") correctRecognizedPhrase(listOf(text), sentence) else text
      },
      timeoutMillis = timeoutMillis
    )
  }

  private fun startRecognition(lang: String, mapper: (String) -> String, timeoutMillis: Int, nativeLang: String) {
    isListening = true
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
    val recognizedResults = mutableListOf<String>()

    // Always set a timeout, using the provided value or 7000ms by default
    timeoutRunnable = Runnable {
      val finalResult = recognizedResults.firstOrNull() ?: ""
      activeResult?.success(mapper(finalResult))
      speechRecognizer?.cancel()
      cleanup()
    }
    timeoutHandler?.postDelayed(timeoutRunnable!!, timeoutMillis.toLong())

    speechRecognizer?.setRecognitionListener(object : RecognitionListener {
      override fun onResults(results: Bundle) {
        isListening = false
        for (key in results.keySet()) {
          Log.d("TAG", "Key: $key => Value: ${results.get(key)}")
        }
        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
          val finalResult = mapper(matches.first())
          activeResult?.success(finalResult)
        } else {
          activeResult?.error("NO_MATCH", "No speech recognized", null)
        }
        speechRecognizer?.cancel()
        cleanup()
      }

      override fun onError(error: Int) {
        isListening = false
        activeResult?.error("SPEECH_ERROR", getErrorText(error), null)
        speechRecognizer?.cancel()
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
    if (text.isBlank()) {
      return speakLoud
    }
    Log.d("TAG", "numbers: ------------- $text")
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

    Log.d("TAG", "texts: ------------- $text")
//    if (text.isBlank()) {
//      return speakLoud
//    }
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
      speakLoud // if null
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

//  private fun cleanup() {
//    cancelTimeout()
//    speechRecognizer?.destroy()
//    speechRecognizer = null
//    activeResult = null
//  }

  private fun cleanup() {
    timeoutHandler?.removeCallbacks(timeoutRunnable!!)
    timeoutHandler = null
    timeoutRunnable = null
    speechRecognizer?.destroy()
    speechRecognizer = null
    activeResult = null
    isListening = false
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


    fun calculateCodeSimilarity(code1: String, code2: String): Double {
      // Group similar sounds together
      val soundGroups = mapOf(
        setOf('R', 'W') to 0.8,
        setOf('N', "NG") to 0.8,
        setOf("EY", "EH", "AE") to 0.7,
        setOf("CH", "JH", "GE") to 0.7
      )

      // If codes are identical, return 1.0
      if (code1 == code2) return 1.0

      // Check if codes belong to the same sound group
      for ((group, similarity) in soundGroups) {
        if (code1 in group && code2 in group) {
          return similarity
        }
      }

      // Handle partial matches
      val minLength = kotlin.math.min(code1.length, code2.length)
      val commonPrefix = code1.commonPrefixWith(code2)
      if (commonPrefix.length > 0) {
        return commonPrefix.length.toDouble() / minLength * 0.5
      }

      return 0.0
    }


    fun calculatePhoneticSimilarity(phrase1: String, phrase2: String): Double {
      val phonetics1 = getPhoneticCodes(phrase1)
      val phonetics2 = getPhoneticCodes(phrase2)

      if (kotlin.math.abs(phonetics1.size - phonetics2.size) > 1) return 0.0

      var totalSimilarity = 0.0
      val maxLength = kotlin.math.max(phonetics1.size, phonetics2.size)

      phonetics1.forEachIndexed { index, code1 ->
        if (index < phonetics2.size) {
          val code2 = phonetics2[index]
          // Calculate similarity between individual phonetic codes
          totalSimilarity += calculateCodeSimilarity(code1, code2)
        }
      }

      return totalSimilarity / maxLength
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