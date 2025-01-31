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
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.*

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
        val timeoutMillis = call.argument<Int>("timeout") ?: 0 // Extract timeout

        when (type) {
          "alphabet" -> handleAlphabetRecognition(languageCode, timeoutMillis)
          "number" -> handleNumberRecognition(languageCode, timeoutMillis)
          "wordsOrSentence" -> handleWordsRecognition(languageCode, timeoutMillis)
          else -> result.error("INVALID_TYPE", "Unsupported type", null)
        }
      }
      else -> result.notImplemented()
    }
  }

  private fun handleAlphabetRecognition(languageCode: String?, timeoutMillis: Int) {
    val isConnected = isNetworkAvailable(context)
    val lang = if (isConnected) "ne-NP" else "hi-IN"
    startRecognition(
      lang = lang,
      mapper = { text -> mapText(text, PhoneticMapping.phoneticNepaliToEnglishMapping) },
      timeoutMillis = timeoutMillis
    )
  }

  private fun handleNumberRecognition(languageCode: String?, timeoutMillis: Int) {
    startRecognition(
      lang = "hi-IN",
      mapper = { text -> mapText(text, PhoneticMapping.phoneticNumbersMapping) },
      timeoutMillis = timeoutMillis
    )
  }

  private fun handleWordsRecognition(languageCode: String?, timeoutMillis: Int) {
    if (languageCode == null) {
      activeResult?.error("INVALID_LANG", "Language code required", null)
      activeResult = null
      return
    }
    startRecognition(
      lang = languageCode,
      mapper = { it },
      timeoutMillis = timeoutMillis
    )
  }

  private fun startRecognition(lang: String, mapper: (String) -> String, timeoutMillis: Int) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      activeResult?.error("PERMISSION_DENIED", "Audio permission required", null)
      activeResult = null
      return
    }

    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
      putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
      putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    // Setup timeout
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
        cancelTimeout() // Cancel timeout on results
        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val recognizedText = matches?.firstOrNull() ?: ""
        activeResult?.success(mapper(recognizedText))
        cleanup()
      }

      override fun onError(error: Int) {
        cancelTimeout() // Cancel timeout on error
        activeResult?.error("SPEECH_ERROR", getErrorText(error), null)
        cleanup()
      }

      // Other overridden methods
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

  private fun mapText(text: String, mapping: Map<String, List<String>>): String {
    val normalizedText = text.lowercase(Locale.ROOT)
    return mapping.flatMap { entry ->
      entry.value.map { it.lowercase(Locale.ROOT) to entry.key.toString() }
    }.toMap()[normalizedText] ?: text.uppercase(Locale.ROOT)
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
}