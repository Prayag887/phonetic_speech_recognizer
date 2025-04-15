package com.example.phonetic_speech_recognizer

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.codec.language.DoubleMetaphone
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.apache.commons.lang3.StringUtils
import java.util.*

class PhoneticSpeechRecognizerPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {
  private lateinit var context: Context
  private lateinit var channel: MethodChannel
  private lateinit var eventChannel: EventChannel
  private var eventSink: EventChannel.EventSink? = null
  private var speechRecognizer: SpeechRecognizer? = null
  private var activeResult: MethodChannel.Result? = null
  private var timeoutHandler: Handler? = null
  private var timeoutRunnable: Runnable? = null
  private var isListening = false
  private val speakLoud : String = "Please speak clearly and loudly in a silent environment."
  private var isProcessing: Boolean = false

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    context = binding.applicationContext
    channel = MethodChannel(binding.binaryMessenger, "phonetic_speech_recognizer")
    channel.setMethodCallHandler(this)

    // Initialize the event channel for streaming partial results
    eventChannel = EventChannel(binding.binaryMessenger, "phonetic_speech_recognizer/partial_results")
    eventChannel.setStreamHandler(this)
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    eventChannel.setStreamHandler(null)
  }

  // EventChannel.StreamHandler implementation
  override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
    eventSink = events
  }

  override fun onCancel(arguments: Any?) {
    eventSink = null
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    Log.d("SpeechRecognition", "onMethodCall: ${call.method}")
    when (call.method) {
      "recognize" -> {
        if (activeResult != null) {
//          result.error("ALREADY_ACTIVE", "Recognition already active", null)
          isProcessing = true
          result.error("ALREADY_ACTIVE", "Analyzing...", null)
          return
        } else {
          isProcessing = false

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
          "koreanNumber" -> handleKoreanNumberRecognition(timeoutMillis, "katakana")
          "allLanguageSupport" -> handleAllLanguages(timeoutMillis, languageCode)
          "paragraphsMapping" -> handleParagraphMapping(timeoutMillis = timeoutMillis, languageCode = languageCode, paragraph = sentence)
          else -> result.error("INVALID_TYPE", "Unsupported type", null)
        }
      }

      "stopRecognition" -> {
        if (isProcessing) {
          stopRecognition(result)
        } else {
          Log.d("TAG", "Still analyzing")
          Handler(Looper.getMainLooper()).postDelayed({
            stopRecognition(result)
          }, 100)
        }
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
      Log.d("TAG", "stopRecognition: stopped successfully")
      isListening = false
    } catch (e: Exception) {
      result.error("STOP_ERROR", "Failed to stop recognition", e.message)
    }
  }

  private fun handleJapaneseRecognition(timeoutMillis: Int, type: String) {
    isNetworkAvailable(context)
    val lang = "ne-NP"
    startRecognition(
      paragraph = "",
      lang = lang,
      mapper = { text ->
        //mapText returns only the mapped value. If it picks up the noise on top of users voice then response wont be provided
        mapNumber(
          text,
          PhoneticMapping.phoneticJapaneseAlphabetMapping
        )
      },
      timeoutMillis = timeoutMillis,
      keepListening = false
    )
  }

  private fun handleKoreanNumberRecognition(timeoutMillis: Int, type: String) {
    isNetworkAvailable(context)
    val lang = "ne-NP"
    startRecognition(
      paragraph = "",
      lang = lang,
      mapper = { text ->
        //mapText returns only the mapped value. If it picks up the noise on top of users voice then response wont be provided
        mapNumber(
          text,
          PhoneticMapping.phoneticKoreanNumberMapping
        )
      },
      timeoutMillis = timeoutMillis,
      keepListening = false
    )
  }

  private fun handleAlphabetRecognition(timeoutMillis: Int) {
    val isConnected = isNetworkAvailable(context)
    val lang = "ne-NP"
//    val lang = if (isConnected) "ne-NP" else "hi-IN"
    startRecognition(
      paragraph = "",
      lang = lang,
      //mapText returns only the mapped value (english alphabets in this case). If it picks up the noise on top of users voice then response wont be provided
      mapper = { text -> mapText(text, PhoneticMapping.phoneticNepaliToEnglishMapping) },
      timeoutMillis = timeoutMillis,
      keepListening = false
    )
  }
  private fun handleAllLanguages(timeoutMillis: Int, languageCode: String) {
    Log.d("TAG", "handleAllLanguages: ------------------- $languageCode")
    startRecognition(
      paragraph = "",
      lang = languageCode,
      //mapText returns only the mapped value (english alphabets in this case). If it picks up the noise on top of users voice then response wont be provided
      mapper = { text -> mapNumber(text, PhoneticMapping.phoneticNepaliToEnglishMapping) },
      timeoutMillis = timeoutMillis,
      keepListening =  false
    )
  }

  private fun handleKoreanAlphabetRecognition(timeoutMillis: Int) {
    startRecognition(
      paragraph = "",
      lang = "ne-NP",
      //mapText returns only the mapped value (korean alphabets in this case). If it picks up the noise on top of users voice then response wont be provided
      mapper = { text -> mapText(text, PhoneticMapping.phoneticKoreanMapping) },
      timeoutMillis = timeoutMillis,
      keepListening =  false
    )
  }

  private fun handleNumberRecognition(timeoutMillis: Int) {
    startRecognition(
      paragraph = "",
      lang = "hi-IN",
      //mapNumber has to only the mapped value (number in this case). If it picks up the noise on top of users voice then response wont be provided
      mapper = { text -> mapNumber(text, PhoneticMapping.phoneticNumbersMapping) },
      timeoutMillis = timeoutMillis,
      keepListening =  false
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
      paragraph = "",
      lang = languageCode,
      mapper = { text ->
        if (languageCode == "en-US") correctRecognizedPhrase(listOf(text), sentence) else text
      },
      timeoutMillis = timeoutMillis,
      keepListening = false
    )
  }

  private fun handleParagraphMapping(languageCode: String?, timeoutMillis: Int, paragraph: String) {
    Log.d("SpeechRecognition", "SENTENCE FROM FLUTTER SIDE: \"$paragraph\"")
    if (languageCode == null) {
      println("this is sentence $paragraph")
      activeResult?.error("INVALID_LANG", "Language code required", null)
      activeResult = null
      return
    }

    val words = paragraph.split(" ").map { it.trim() }.filter { it.isNotEmpty() }

    startRecognition(
      paragraph = paragraph,
      lang = languageCode,
      mapper = { text ->
        if (languageCode == "en-US") {
          updateHighlightedText(text, words, paragraph)
        }
        text
      },
      timeoutMillis = timeoutMillis,
      keepListening = true
    )
  }

  private fun updateHighlightedText(spokenText: String, words: List<String>, paragraph: String): Map<String, Any> {
    val highlightedIndices = mutableListOf<Map<String, Int>>()
    val spokenWords = spokenText.lowercase(Locale.ENGLISH).split(" ").filter { it.isNotEmpty() }
    val lowerWords = words.map { it.lowercase(Locale.ENGLISH) }

    // Create a map of word -> list of positions in paragraph
    val wordPositions = mutableMapOf<String, MutableList<Int>>()
    for (i in lowerWords.indices) {
      val word = lowerWords[i]
      if (!wordPositions.containsKey(word)) {
        wordPositions[word] = mutableListOf()
      }
      wordPositions[word]?.add(i)
    }

    // For each spoken word, find all occurrences in the paragraph
    for (spokenWord in spokenWords) {
      val positions = wordPositions[spokenWord.lowercase(Locale.ENGLISH)] ?: continue

      for (position in positions) {
        val originalWord = words[position]
        val start = paragraph.indexOf(originalWord,
          // Start searching from after the last highlight if possible
          if (highlightedIndices.isNotEmpty())
            highlightedIndices.last()["end"] ?: 0
          else 0
        )

        if (start >= 0) {
          val end = start + originalWord.length
          highlightedIndices.add(mapOf("start" to start, "end" to end))
          break // Take the first occurrence after the last highlight
        }
      }
    }

    // Sort highlights by start position to ensure they're in order
    return mapOf("highlights" to highlightedIndices.sortedBy { it["start"] })
  }

  private fun startRecognition(lang: String, mapper: (String) -> Any, timeoutMillis: Int, paragraph: String?, keepListening: Boolean) {
    isListening = true
    if (speechRecognizer != null) {
      speechRecognizer?.cancel()
      cleanup()
    }

    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val intent = if(keepListening) {
       Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra("android.speech.extra.GET_AUDIO_FORMAT", "audio/AMR_WB")
      }
    } else {
      Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 7)
      }
    }
    timeoutHandler = Handler(context.mainLooper)
    val recognizedResults = mutableListOf<String>()
    val isKeepListening = keepListening // Capture for timeout handling

    timeoutRunnable = Runnable {
      val finalResult = if (isKeepListening) {
        recognizedResults.joinToString(" ") // Join all accumulated results
      } else {
        recognizedResults.firstOrNull() ?: ""
      }
      activeResult?.success(mapper(finalResult))
      speechRecognizer?.cancel()
      cleanup()
    }
    timeoutHandler?.postDelayed(timeoutRunnable!!, timeoutMillis.toLong())

    speechRecognizer?.setRecognitionListener(object : RecognitionListener {
      override fun onResults(results: Bundle) {
        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
          if (keepListening) {
            // Append the first match to the accumulated results
            val firstMatch = matches.first()
            recognizedResults.add(firstMatch)
            // this is to append the data after recognizer is restarted during the short pause
            val accumulatedText = recognizedResults.joinToString(" ")
            // this is to correct the grammar according to the paragraph received from the flutter side
            // TODO() faster grammar check is required for real time updates in paragraph. Somewhat achieved by the intent above
//            val correctedText = correctRecognizedParagraph(accumulatedText, paragraph!!)

            eventSink?.success(mapper(accumulatedText))
            // Restart listening for continuous input
            speechRecognizer?.startListening(intent)
          } else {
            // Normal behavior: replace with current matches
            recognizedResults.clear()
            recognizedResults.addAll(matches)
            isListening = false
            val finalResult = mapper(matches.first())
            activeResult?.success(finalResult)
            speechRecognizer?.cancel()
            cleanup()
          }
        } else {
          if (keepListening) {
            // No matches but keep listening: restart
            speechRecognizer?.startListening(intent)
          } else {
            // No matches and not keeping listening: error
            isListening = false
            activeResult?.error("NO_MATCH", "No speech recognized", null)
            speechRecognizer?.cancel()
            cleanup()
          }
        }
      }

      override fun onPartialResults(partialResults: Bundle?) {
        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { partialList ->
          if (partialList.isNotEmpty() && paragraph != null) {
            if (keepListening) {
              val currentPartial = partialList.firstOrNull { paragraph.contains(it) } ?: partialList.first()

              // val correctedPartial = correctRecognizedPhrase(listOf(currentPartial), paragraph)
              // Combine accumulated recognized results with current partial
              val accumulatedText = recognizedResults.joinToString(" ")
              val fullText = if (accumulatedText.isNotEmpty()) {
                "$accumulatedText $currentPartial"
              } else {
                currentPartial
              }
              eventSink?.success(mapper(fullText))
            } else {
              // Normal behavior: update recognizedResults with partials
              recognizedResults.clear()
              recognizedResults.addAll(partialList)
              val correctedText = correctRecognizedPhrase(partialList, paragraph)
              eventSink?.success(mapper(correctedText))
            }
          }
        }
      }

      override fun onError(error: Int) {
        if (keepListening && (error == SpeechRecognizer.ERROR_NO_MATCH ||
                  error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                  error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY)) {
          Log.d("SpeechRecognition", "Error occurred but continuing: ${getErrorText(error)}")
          speechRecognizer?.startListening(intent)
        } else {
          isListening = false
          activeResult?.error("SPEECH_ERROR", getErrorText(error), null)
          speechRecognizer?.cancel()
          cleanup()
        }
      }

      // Other overrides remain unchanged
      override fun onEndOfSpeech() {}
      override fun onReadyForSpeech(params: Bundle?) {}
      override fun onBeginningOfSpeech() {}
      override fun onRmsChanged(rmsdB: Float) {}
      override fun onBufferReceived(buffer: ByteArray?) {}
      override fun onEvent(eventType: Int, params: Bundle?) {}
    })

    speechRecognizer?.startListening(intent)
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
    }

    if (bestSimilarity >= 0.7) {
      return expectedPhrase
    }
    return bestMatch
  }
}