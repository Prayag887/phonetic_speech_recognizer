package com.prayag.phonetic_speech_recognizer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.media.audiofx.AcousticEchoCanceler
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.*


class PhoneticSpeechRecognizerPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler, ActivityAware {
  private lateinit var context: Context
  private lateinit var channel: MethodChannel
  private lateinit var eventChannel: EventChannel
  private var eventSink: EventChannel.EventSink? = null
  private var speechRecognizer: SpeechRecognizer? = null
  var activeResult: MethodChannel.Result? = null
  private var timeoutHandler: Handler? = null
  private var timeoutRunnable: Runnable? = null
  private var isListening = false
  private val speakLoud : String = "Please speak clearly and loudly in a silent environment."
  private var isProcessing: Boolean = false
  private var activity: Activity? = null
  private var activityBinding: ActivityPluginBinding? = null
  private var audioManager: AudioManager? = null
  private var originalStreamVolume: Int = 0
  private var originalMicMute: Boolean = false

  // Create a single instance of LanguageHandlers that will be reused
  private lateinit var languageHandlers: LanguageHandlers

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    context = binding.applicationContext
    channel = MethodChannel(binding.binaryMessenger, "phonetic_speech_recognizer")
    channel.setMethodCallHandler(this)

    // Initialize the event channel for streaming partial results
    eventChannel = EventChannel(binding.binaryMessenger, "phonetic_speech_recognizer/partial_results")
    eventChannel.setStreamHandler(this)

    // Initialize LanguageHandlers with plugin instance reference
    languageHandlers = LanguageHandlers(context)
    languageHandlers.setPluginInstance(this)

    // Initialize AudioManager
    audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  }

  // Implement the missing ActivityAware methods
  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    activityBinding = binding
  }

  override fun onDetachedFromActivity() {
    activity = null
    activityBinding = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    onAttachedToActivity(binding)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity()
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    eventChannel.setStreamHandler(null)

    activity = null
    activityBinding = null
  }

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
        checkAndRequestPermission()
        // Store the result reference
        activeResult = result

        val type = call.argument<String>("type")
        val languageCode = call.argument<String>("languageCode") ?: "ja-JP"
        val timeoutMillis = call.argument<Int>("timeout")!!
        val sentence = call.argument<String>("sentence") ?: ""

        try {
          when (type) {
            "alphabet" -> languageHandlers.handleAlphabetRecognition(timeoutMillis)
            "koreanAlphabet" -> languageHandlers.handleKoreanAlphabetRecognition(timeoutMillis)
            "number" -> languageHandlers.handleNumberRecognition(timeoutMillis, sentence)
            "englishWordsOrSentence" -> languageHandlers.handleWordsRecognition(languageCode, timeoutMillis, sentence)
            "japaneseAlphabet" -> languageHandlers.handleJapaneseRecognition(timeoutMillis, "hiragana")
            "koreanNumber" -> languageHandlers.handleKoreanNumberRecognition(timeoutMillis, "katakana")
            "allLanguageSupport" -> languageHandlers.handleAllLanguages(timeoutMillis, languageCode)
            "paragraphsMapping" -> languageHandlers.handleParagraphMapping(languageCode, timeoutMillis, sentence)
            else -> {
              activeResult?.error("INVALID_TYPE", "Unsupported type", null)
              activeResult = null
            }
          }
        } catch (e: Exception) {
          Log.e("SpeechRecognition", "Error in recognize method", e)
          activeResult?.error("RECOGNITION_ERROR", "Error starting recognition", e.message)
          activeResult = null
        }
      }

      "stopRecognition" -> { stopRecognition(result) }

      "isListening" -> {
        result.success(isListening)  // Return whether the mic is active
      }

      else -> result.notImplemented()
    }
  }

  private fun checkAndRequestPermission() {
    val permission = Manifest.permission.RECORD_AUDIO
    if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(activity!!, arrayOf(permission), 1001)
    } else {
      Log.d("PhoneticPlugin", "Permission already granted.")
    }
  }

  private fun stopRecognition(result: MethodChannel.Result) {
    try {
      speechRecognizer?.cancel()
      restoreAudioSettings()
      cleanup()
      result.success(true)
      Log.d("TAG", "stopRecognition: stopped successfully")
      isListening = false
    } catch (e: Exception) {
      result.error("STOP_ERROR", "Failed to stop recognition", e.message)
    }
  }

  fun updateHighlightedText(spokenText: String, words: List<String>, paragraph: String): Map<String, Any> {
    val highlightedIndices = mutableListOf<Map<String, Int>>()
    val spokenWords = spokenText.lowercase(Locale.ENGLISH).split(" ").filter { it.isNotEmpty() }
    val lowerWords = words.map { it.lowercase(Locale.ENGLISH) }

    val wordPositions = mutableMapOf<String, MutableList<Int>>()
    for (i in lowerWords.indices) {
      val word = lowerWords[i]
      if (!wordPositions.containsKey(word)) {
        wordPositions[word] = mutableListOf()
      }
      wordPositions[word]?.add(i)
    }

    for (spokenWord in spokenWords) {
      val positions = wordPositions[spokenWord.lowercase(Locale.ENGLISH)] ?: continue

      for (position in positions) {
        val originalWord = words[position]
        val start = paragraph.indexOf(originalWord,
          if (highlightedIndices.isNotEmpty())
            highlightedIndices.last()["end"] ?: 0
          else 0
        )

        if (start >= 0) {
          val end = start + originalWord.length
          highlightedIndices.add(mapOf("start" to start, "end" to end))
          break
        }
      }
    }
    return mapOf("highlights" to highlightedIndices.sortedBy { it["start"] })
  }

  private fun setupAudioEnhancements(): Int {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    var audioSessionId = 0

    try {
      // Store original settings
      originalStreamVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        originalMicMute = audioManager.isMicrophoneMute
      }

      // Generate audio session ID for effects
      audioSessionId = audioManager.generateAudioSessionId()

      // Set microphone to unmuted and boost system volume
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        audioManager.setMicrophoneMute(false)
      }

      // Boost voice call stream volume to maximum
      val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
      audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, 0)

      // Enable noise suppression if available
      if (NoiseSuppressor.isAvailable()) {
        val noiseSuppressor = NoiseSuppressor.create(audioSessionId)
        noiseSuppressor?.enabled = true
        Log.d("SpeechRecognition", "Noise suppressor enabled")
      }

      // Enable Automatic Gain Control for sensitivity boost
      if (AutomaticGainControl.isAvailable()) {
        val agc = AutomaticGainControl.create(audioSessionId)
        agc?.enabled = true
        Log.d("SpeechRecognition", "AGC enabled for sensitivity boost")
      }

      // Enable Acoustic Echo Canceler if available
      if (AcousticEchoCanceler.isAvailable()) {
        val aec = AcousticEchoCanceler.create(audioSessionId)
        aec?.enabled = true
        Log.d("SpeechRecognition", "AEC enabled")
      }

      // Set mode to improve voice recognition
      audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

      Log.d("SpeechRecognition", "Audio enhancements applied successfully")

    } catch (e: Exception) {
      Log.w("SpeechRecognition", "Could not apply audio enhancements: ${e.message}")
    }

    return audioSessionId
  }

  private fun restoreAudioSettings() {
    try {
      audioManager?.let { am ->
        // Restore original volume
        am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, originalStreamVolume, 0)

        // Restore original mic mute state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          am.setMicrophoneMute(originalMicMute)
        }

        // Restore normal audio mode
        am.mode = AudioManager.MODE_NORMAL
      }
      Log.d("SpeechRecognition", "Audio settings restored")
    } catch (e: Exception) {
      Log.w("SpeechRecognition", "Could not restore audio settings: ${e.message}")
    }
  }

  fun startRecognition(lang: String, mapper: (Map<String, Double>) -> Any, timeoutMillis: Int, paragraph: String = "", keepListening: Boolean) {
    // Set processing flags
    isProcessing = true
    isListening = true

    if (speechRecognizer != null) {
      speechRecognizer?.cancel()
      cleanup()
    }

    // Setup audio enhancements for improved sensitivity
    val audioSessionId = setupAudioEnhancements()

    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      // Use web search model for better recognition
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
      putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
      putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

      // Enhanced audio settings for better sensitivity
      putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false) // Online often better for sensitivity
      putExtra("android.speech.extra.GET_AUDIO_FORMAT", "audio/AMR_WB")
      putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(lang))

      // Try to set timeout parameters (may not work on all devices)
      putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
      putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
      putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)

      // Request more results for better matching
      if (!keepListening) {
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10)
      }

      // Audio source hint for better microphone sensitivity
      putExtra("android.speech.extra.AUDIO_SOURCE", android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION)
    }

    timeoutHandler = Handler(context.mainLooper)
    val recognizedResults = mutableListOf<String>()
    val isKeepListening = keepListening // Capture for timeout handling

    timeoutRunnable = Runnable {
      try {
        val finalResult = if (isKeepListening) {
          mapOf(recognizedResults.joinToString(" ") to 0.0 ) // Join all accumulated results
        } else {
          mapOf((recognizedResults.firstOrNull() ?: "") to 0.0)
        }
        activeResult?.success(mapper(finalResult))
      } catch (e: Exception) {
        activeResult?.error("TIMEOUT_ERROR", "Error processing timeout result", e.message)
      }
      speechRecognizer?.cancel()
      restoreAudioSettings()
      cleanup()
    }
    timeoutHandler?.postDelayed(timeoutRunnable!!, timeoutMillis.toLong())

    speechRecognizer?.setRecognitionListener(object : RecognitionListener {
      override fun onResults(results: Bundle) {
        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val confidenceScores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

        if (!matches.isNullOrEmpty()) {
          try {
            if (keepListening) {
              val firstMatch = matches.first()
              recognizedResults.add(firstMatch)
              val accumulatedText = mapOf(recognizedResults.joinToString(" ") to 0.0)

              eventSink?.success(mapper(accumulatedText))

              // Restart listening with reduced delay for better continuous recognition
              Handler().postDelayed({
                if (isListening) {
                  speechRecognizer?.startListening(intent)
                }
              }, 100)
            } else {
              recognizedResults.clear()
              recognizedResults.addAll(matches)
              isListening = false

              // Use confidence scores if available for better results
              val bestMatch = if (confidenceScores != null && confidenceScores.isNotEmpty()) {
                val bestIndex = confidenceScores.indices.maxByOrNull { confidenceScores[it] } ?: 0
                matches[bestIndex]
              } else {
                matches.first()
              }

              val mappedMatches = mapOf(bestMatch to (confidenceScores?.get(0)?.toDouble() ?: 0.0))
              val finalResult = mapper(mappedMatches)
              Log.d("SpeechRecognition", "Sending final result: $finalResult")
              activeResult?.success(finalResult)
              speechRecognizer?.cancel()
              restoreAudioSettings()
              cleanup()
            }
          } catch (e: Exception) {
            Log.e("SpeechRecognition", "Error processing results", e)
            activeResult?.error("PROCESSING_ERROR", "Error processing speech results", e.message)
            restoreAudioSettings()
            cleanup()
          }
        } else {
          if (keepListening) {
            // Restart listening even if no match, with shorter delay
            Handler().postDelayed({
              if (isListening) {
                speechRecognizer?.startListening(intent)
              }
            }, 100)
          } else {
            isListening = false
            activeResult?.error("NO_MATCH", "No speech recognized", null)
            speechRecognizer?.cancel()
            restoreAudioSettings()
            cleanup()
          }
        }
      }

      override fun onPartialResults(partialResults: Bundle?) {
        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { partialList ->
          if (partialList.isNotEmpty()) {
            try {
              if (keepListening) {
                val currentPartial = if (paragraph.isNotEmpty()) {
                  partialList.firstOrNull { paragraph.contains(it, ignoreCase = true) } ?: partialList.first()
                } else {
                  partialList.first()
                }
                val accumulatedText = recognizedResults.joinToString(" ")
                val fullText = if (accumulatedText.isNotEmpty()) {
                  mapOf("$accumulatedText $currentPartial" to 0.0)
                } else {
                  mapOf(currentPartial to 0.0)
                }
                eventSink?.success(mapper(fullText))
              } else {
                recognizedResults.clear()
                recognizedResults.addAll(partialList)
                val correctedText = if (paragraph.isNotEmpty()) {
                  languageHandlers.correctRecognizedPhrase(partialList, paragraph)
                } else {
                  mapOf(partialList.first() to 0.0)
                }
                eventSink?.success(mapper(correctedText))
              }
            } catch (e: Exception) {
              Log.e("SpeechRecognition", "Error processing partial results", e)
            }
          }
        }
      }

      override fun onError(error: Int) {
        val errorText = getErrorText(error)
        Log.e("SpeechRecognition", "Recognition error: $errorText")

        if (keepListening && (error == SpeechRecognizer.ERROR_NO_MATCH ||
                  error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                  error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY)) {
          Log.d("SpeechRecognition", "Recoverable error, restarting: $errorText")
          // Restart with slight delay for better stability
          Handler().postDelayed({
            if (isListening) {
              speechRecognizer?.startListening(intent)
            }
          }, 200)
        } else {
          isListening = false
          Log.e("SpeechRecognition", "Fatal error occurred: $errorText")
          activeResult?.error("SPEECH_ERROR", errorText, null)
          speechRecognizer?.cancel()
          speechRecognizer?.destroy()
          restoreAudioSettings()
          cleanup()
        }
      }

      override fun onRmsChanged(rmsdB: Float) {
        // Monitor audio levels for debugging - more sensitive threshold
        if (rmsdB > -30.0f) { // Lower threshold for better sensitivity detection
          Log.d("SpeechRecognition", "Good audio level detected: $rmsdB dB")
        }
      }

      override fun onReadyForSpeech(params: Bundle?) {
        Log.d("SpeechRecognition", "Ready for speech - enhanced sensitivity active")
      }

      override fun onBeginningOfSpeech() {
        Log.d("SpeechRecognition", "Speech detected")
      }

      override fun onEndOfSpeech() {
        Log.d("SpeechRecognition", "End of speech detected")
      }

      override fun onBufferReceived(buffer: ByteArray?) {
        // Could potentially process buffer here for custom sensitivity adjustments
      }

      override fun onEvent(eventType: Int, params: Bundle?) {}
    })

    speechRecognizer?.startListening(intent)
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
    SpeechRecognizer.ERROR_NO_MATCH -> "No match"
    else -> "Unknown error"
  }

  private fun cleanup() {
    isProcessing = false
    isListening = false
    timeoutHandler?.removeCallbacks(timeoutRunnable!!)
    timeoutHandler = null
    timeoutRunnable = null
    speechRecognizer?.destroy()
    speechRecognizer = null
    activeResult = null
  }
}