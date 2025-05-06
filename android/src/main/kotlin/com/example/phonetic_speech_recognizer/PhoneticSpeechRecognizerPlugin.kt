package com.example.phonetic_speech_recognizer

import android.content.Context
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class PhoneticSpeechRecognizerPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {
  private lateinit var context: Context
  private lateinit var channel: MethodChannel
  private lateinit var eventChannel: EventChannel
  private var eventSink: EventChannel.EventSink? = null

  private val recognitionManager = RecognitionManager()

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    context = binding.applicationContext
    channel = MethodChannel(binding.binaryMessenger, "phonetic_speech_recognizer")
    channel.setMethodCallHandler(this)

    // Initialize the event channel for streaming partial results
    eventChannel = EventChannel(binding.binaryMessenger, "phonetic_speech_recognizer/partial_results")
    eventChannel.setStreamHandler(this)

    recognitionManager.initialize(context)
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    eventChannel.setStreamHandler(null)
    recognitionManager.cleanup()
  }

  // EventChannel.StreamHandler implementation
  override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
    eventSink = events
    recognitionManager.setEventSink(eventSink)
  }

  override fun onCancel(arguments: Any?) {
    eventSink = null
    recognitionManager.setEventSink(null)
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    Log.d("SpeechRecognition", "onMethodCall: ${call.method}")
    when (call.method) {
      "recognize" -> {
        handleRecognitionRequest(call, result)
      }

      "stopRecognition" -> {
        recognitionManager.stopRecognition(result)
      }

      "isListening" -> {
        result.success(recognitionManager.isListening())
      }

      else -> result.notImplemented()
    }
  }

  private fun handleRecognitionRequest(call: MethodCall, result: MethodChannel.Result) {
    if (recognitionManager.isActive()) {
      result.error("ALREADY_ACTIVE", "Recognition already active", null)
      return
    }

    val type = call.argument<String>("type")
    val languageCode = call.argument<String>("languageCode") ?: "ja-JP"
    val timeoutMillis = call.argument<Int>("timeout")!!
    val sentence = call.argument<String>("sentence") ?: ""

    recognitionManager.setActiveResult(result)

    when (type) {
      "alphabet" -> recognitionManager.handleAlphabetRecognition(timeoutMillis)
      "koreanAlphabet" -> recognitionManager.handleKoreanAlphabetRecognition(timeoutMillis)
      "number" -> recognitionManager.handleNumberRecognition(timeoutMillis)
      "englishWordsOrSentence" -> recognitionManager.handleWordsRecognition(languageCode, timeoutMillis, sentence)
      "japaneseAlphabet" -> recognitionManager.handleJapaneseRecognition(timeoutMillis, "hiragana")
      "koreanNumber" -> recognitionManager.handleKoreanNumberRecognition(timeoutMillis)
      "allLanguageSupport" -> recognitionManager.handleKoreanWordsRecognition(timeoutMillis, languageCode)
      "paragraphsMapping" -> recognitionManager.handleParagraphMapping(timeoutMillis, languageCode, sentence)
      else -> result.error("INVALID_TYPE", "Unsupported type", null)
    }
  }
}