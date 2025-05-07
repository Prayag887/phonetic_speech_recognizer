package com.example.phonetic_speech_recognizer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class RecognitionManager {
    private var context: Context? = null
    private var eventSink: EventChannel.EventSink? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var activeResult: MethodChannel.Result? = null
    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null
    private var isListening = false

    fun initialize(context: Context) {
        this.context = context
    }

    fun setEventSink(eventSink: EventChannel.EventSink?) {
        this.eventSink = eventSink
    }

    fun setActiveResult(result: MethodChannel.Result) {
        this.activeResult = result
    }

    fun isActive(): Boolean {
        return activeResult != null
    }

    fun isListening(): Boolean {
        return isListening
    }

    fun stopRecognition(result: MethodChannel.Result) {
        try {
            speechRecognizer?.cancel()
            cleanup()
            result.success(true)
            isListening = false
        } catch (e: Exception) {
            result.error("STOP_ERROR", "Failed to stop recognition", e.message)
        }
    }

    fun handleJapaneseRecognition(timeoutMillis: Int, type: String) {
        val lang = "ne-NP"
        startRecognition(
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

    fun handleKoreanNumberRecognition(timeoutMillis: Int) {
        val lang = "ne-NP"
        startRecognition(
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

    fun handleKoreanWordsRecognition(timeoutMillis: Int, languageCode: String) {
        if (languageCode == "ne-NP") {
            startRecognition(
                paragraph = "",
                lang = "ne-NP",
                mapper = { text ->
                    Mapper().mapNumber(
                        text,
                        PhoneticMapping.phoneticKoreanObjectsMapping
                    )
                },
                timeoutMillis = timeoutMillis,
                keepListening = false
            )
        } else {
            startRecognition(
                paragraph = "",
                lang = languageCode,
                mapper = { text -> Mapper().mapNumber(text, PhoneticMapping.phoneticNepaliToEnglishMapping) },
                timeoutMillis = timeoutMillis,
                keepListening = false
            )
        }
    }

    fun handleAlphabetRecognition(timeoutMillis: Int) {
        val lang = "ne-NP"
        startRecognition(
            paragraph = "",
            lang = lang,
            mapper = { text -> Mapper().mapText(text, PhoneticMapping.phoneticNepaliToEnglishMapping) },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    fun handleKoreanAlphabetRecognition(timeoutMillis: Int) {
        startRecognition(
            paragraph = "",
            lang = "ne-NP",
            mapper = { text -> Mapper().mapText(text, PhoneticMapping.phoneticKoreanMapping) },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    fun handleNumberRecognition(timeoutMillis: Int) {
        startRecognition(
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
            activeResult?.error("INVALID_LANG", "Language code required", null)
            activeResult = null
            return
        }
        startRecognition(
            paragraph = "",
            lang = languageCode,
            mapper = { text ->
                if (languageCode == "en-US") Util().correctRecognizedPhrase(listOf(text), sentence) else text
            },
            timeoutMillis = timeoutMillis,
            keepListening = false
        )
    }

    fun handleParagraphMapping(timeoutMillis: Int, languageCode: String?, paragraph: String) {
        Log.d("SpeechRecognition", "SENTENCE FROM FLUTTER SIDE: \"$paragraph\"")
        if (languageCode == null) {
            activeResult?.error("INVALID_LANG", "Language code required", null)
            activeResult = null
            return
        }

        startRecognition(
            paragraph = paragraph,
            lang = languageCode,
            mapper = { text -> text },
            timeoutMillis = timeoutMillis,
            keepListening = true
        )
    }

    private fun startRecognition(lang: String, mapper: (String) -> Any, timeoutMillis: Int, paragraph: String?, keepListening: Boolean) {
        isListening = true
        if (speechRecognizer != null) {
            speechRecognizer?.cancel()
            cleanup()
        }

        val ctx = context ?: return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx)
        val intent = if(keepListening) {
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
        } else {
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 7)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000)
            }
        }

        timeoutHandler = Handler(ctx.mainLooper)
        val recognizedResults = mutableListOf<String>()
        val isKeepListening = keepListening // Capture for timeout handling

        timeoutRunnable = Runnable {
            val finalResult = if (isKeepListening) {
                recognizedResults.joinToString(" ")
            } else {
                recognizedResults.firstOrNull() ?: ""
            }
            activeResult?.success(mapper(finalResult))
            speechRecognizer?.cancel()
            cleanup()
        }
        timeoutHandler?.postDelayed(timeoutRunnable!!, timeoutMillis.toLong())

        speechRecognizer?.setRecognitionListener(createRecognitionListener(intent, recognizedResults, mapper, keepListening, paragraph))
        speechRecognizer?.startListening(intent)
    }

    private fun createRecognitionListener(
        intent: Intent,
        recognizedResults: MutableList<String>,
        mapper: (String) -> Any,
        keepListening: Boolean,
        paragraph: String?
    ): RecognitionListener {
        return object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    if (keepListening) {
                        // Append the first match to the accumulated results
                        val firstMatch = matches.first()
                        recognizedResults.add(firstMatch)
                        // this is to append the data after recognizer is restarted during the short pause
                        val accumulatedText = recognizedResults.joinToString(" ")

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
                        val currentPartial = partialList.firstOrNull { paragraph.contains(it) } ?: partialList.first()

                        val accumulatedText = if (recognizedResults.isNotEmpty()) {
                            "${recognizedResults.joinToString(" ")} $currentPartial"
                        } else {
                            currentPartial
                        }

                        // Send immediate response without waiting for grammar correction
                        eventSink?.success(mapper(accumulatedText))

                        // Run grammar correction in the background (optional)
                        CoroutineScope(Dispatchers.Default).launch {
                            val correctedText = Util().correctRecognizedPhrase(listOf(accumulatedText), paragraph)
                            withContext(Dispatchers.Main) {
                                eventSink?.success(mapper(correctedText))
                            }
                        }
                    }
                }
            }

            override fun onError(error: Int) {
                if (keepListening && (error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY)) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        speechRecognizer?.startListening(intent)
                    }, 200) // Reduce delay before restarting
                } else {
                    isListening = false
                    activeResult?.error("SPEECH_ERROR", getErrorText(error), null)
                    speechRecognizer?.cancel()
                    cleanup()
                }
            }

            override fun onEndOfSpeech() {}
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
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

    fun cleanup() {
        timeoutHandler?.removeCallbacks(timeoutRunnable!!)
        timeoutHandler = null
        timeoutRunnable = null
        speechRecognizer?.destroy()
        speechRecognizer = null
        activeResult = null
        isListening = false
    }
}