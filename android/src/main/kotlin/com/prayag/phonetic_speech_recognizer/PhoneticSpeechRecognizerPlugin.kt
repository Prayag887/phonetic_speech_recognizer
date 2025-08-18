package com.prayag.phonetic_speech_recognizer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread
import kotlin.math.min
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.ActivityCompat
import java.util.*
import com.prayag.phonetic_speech_recognizer.Utils

class PhoneticSpeechRecognizerPlugin : FlutterPlugin, MethodChannel.MethodCallHandler,
  EventChannel.StreamHandler, ActivityAware {

  private lateinit var context: Context
  private lateinit var channel: MethodChannel
  private lateinit var eventChannel: EventChannel
  private lateinit var eventChannelDownload: EventChannel
  private var eventSink: EventChannel.EventSink? = null
  private var eventSinkDownload: EventChannel.EventSink? = null
  var activeResult: MethodChannel.Result? = null

  private var model: Model? = null
  private var recognizer: Recognizer? = null
  private var audioRecord: AudioRecord? = null
  private var isRecording = false
  private var recordingThread: Thread? = null
  private var timeoutHandler: Handler? = null
  private var timeoutRunnable: Runnable? = null
  private var activity: Activity? = null
  private var executorService: ExecutorService = Executors.newCachedThreadPool()
  private var downloadExecutorService: ExecutorService = Executors.newFixedThreadPool(4) // For parallel downloads
  private var isModelDownloading = false
  private var isModelReady = false
  private var isInitialized = false

  private val utils = Utils()
  private val RECORD_AUDIO_PERMISSION_REQUEST = 1001

  private var isProcessing: Boolean = false
  private var isListening = false

  // Model configuration
  private val modelUrl = "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22-lgraph.zip"
  private val modelFileName = "vosk-model-en-us-0.22-lgraph.zip"
  private val modelDirName = "vosk-model-en-us-0.22-lgraph"

  // Enhanced download configuration
  private val maxRetries = 3
  private val chunkSize = 1024 * 1024 // 1MB chunks for parallel download
  private val connectionTimeout = 15000 // 15 seconds
  private val readTimeout = 30000 // 30 seconds
  private val bufferSize = 64  * 1024 // 64KB buffer for better I/O performance

  // Create a single instance of LanguageHandlers that will be reused
  private lateinit var languageHandlers: LanguageHandlers


  private var speechRecognizer: SpeechRecognizer? = null

  // Audio configuration
  private val sampleRate = 16000
  private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
  private val channelConfig = AudioFormat.CHANNEL_IN_MONO
  private val audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    context = binding.applicationContext
    channel = MethodChannel(binding.binaryMessenger, "phonetic_speech_recognizer")
    channel.setMethodCallHandler(this)

    eventChannel = EventChannel(binding.binaryMessenger, "phonetic_speech_recognizer/partial_results")
    eventChannel.setStreamHandler(this)

    eventChannelDownload = EventChannel(binding.binaryMessenger, "download_model_progress")
    eventChannelDownload.setStreamHandler(this)


    // Initialize LanguageHandlers with plugin instance reference
    languageHandlers = LanguageHandlers(context)
    languageHandlers.setPluginInstance(this)

    // Initialize Vosk in background
    initializeVosk()

//    android built in mode for numbers and paragraph mappings:
    initializeSpeechRecognizer()
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    eventChannel.setStreamHandler(null)
    eventChannel.setStreamHandler(null)
    cleanup()
    executorService.shutdown()
    downloadExecutorService.shutdown()
    try {
      if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
        executorService.shutdownNow()
      }
      if (!downloadExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
        downloadExecutorService.shutdownNow()
      }
    } catch (e: InterruptedException) {
      executorService.shutdownNow()
      downloadExecutorService.shutdownNow()
      Thread.currentThread().interrupt()
    }
  }

  override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
    // Handle different event channels based on arguments
    val channelName = arguments as? String
    when (channelName) {
      "download_progress" -> eventSinkDownload = events
      else -> eventSink = events
    }
  }

  override fun onCancel(arguments: Any?) {
    val channelName = arguments as? String
    when (channelName) {
      "download_progress" -> eventSinkDownload = null
      else -> eventSink = null
    }
  }

  private fun sendDownloadProgress(progress: Int, status: String, downloaded: Long = 0, total: Long = 0) {
    Handler(Looper.getMainLooper()).post {
      val progressData = mapOf(
        "progress" to progress,
        "status" to status,
        "downloadedBytes" to downloaded,
        "totalBytes" to total,
        "downloadedMB" to (downloaded / 1024 / 1024).toInt(),
        "totalMB" to (total / 1024 / 1024).toInt()
      )
      eventSinkDownload?.success(progressData)
      Log.d("VoskSpeech", "Progress sent: $progress% - $status")
    }
  }

  private fun initializeVosk() {
    if (isInitialized) return

    executorService.execute {
      try {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        Log.d("VoskSpeech", "Vosk library initialized")
        isInitialized = true

        // Start model download/initialization
        initializeModel()

      } catch (e: Exception) {
        Log.e("VoskSpeech", "Error initializing Vosk library", e)
        Handler(Looper.getMainLooper()).post {
          // Notify about initialization failure if needed
        }
      }
    }
  }

  private fun initializeModel() {
    executorService.execute {
      try {
        val modelPath = getOrDownloadModel()
        if (modelPath != null) {
          model = Model(modelPath)
          isModelReady = true
          Log.d("VoskSpeech", "Vosk model initialized successfully")
        } else {
          Log.e("VoskSpeech", "Failed to initialize model")
        }
      } catch (e: Exception) {
        Log.e("VoskSpeech", "Error initializing model", e)
      }
    }
  }

  private fun getOrDownloadModel(): String? {
    val modelDir = File(context.filesDir, modelDirName)
    val modelFile = File(context.filesDir, modelFileName)

    // Check if model already exists and is valid
    if (modelDir.exists() && isValidModel(modelDir)) {
      Log.d("VoskSpeech", "Model already exists at: ${modelDir.absolutePath}")
      return modelDir.absolutePath
    }

    // Download model if it doesn't exist or is invalid
    return try {
      downloadModelEnhanced(modelFile, modelDir)
    } catch (e: Exception) {
      Log.e("VoskSpeech", "Failed to download model", e)
      null
    }
  }

  private fun isValidModel(modelDir: File): Boolean {
    // Basic validation - check if essential model files exist
    val requiredFiles = listOf("am", "conf", "graph", "ivector")
    return requiredFiles.all {
      File(modelDir, it).exists()
    }
  }

  private fun downloadModelEnhanced(modelFile: File, modelDir: File): String? {
    if (isModelDownloading) {
      Log.d("VoskSpeech", "Model download already in progress")
      return null
    }
    isModelDownloading = true

    try {
      sendDownloadProgress(0, "Initializing download...")
      Log.d("VoskSpeech", "Starting enhanced download from: $modelUrl")

      modelFile.parentFile?.mkdirs()
      if (modelDir.exists()) deleteDirectory(modelDir)
      modelDir.mkdirs()

      sendDownloadProgress(5, "Connecting to server...")

      val url = URL(modelUrl)
      val connection = url.openConnection() as HttpURLConnection
      connection.connectTimeout = connectionTimeout
      connection.readTimeout = readTimeout
      connection.instanceFollowRedirects = true
      connection.setRequestProperty("Accept-Encoding", "identity")
      connection.setRequestProperty("User-Agent", "Vosk-Android-Plugin-Enhanced")

      if (connection.responseCode != HttpURLConnection.HTTP_OK) {
        sendDownloadProgress(0, "Server error: HTTP ${connection.responseCode}")
        Log.e("VoskSpeech", "Server returned HTTP ${connection.responseCode}")
        return null
      }

      val totalSize = connection.contentLengthLong
      sendDownloadProgress(10, "Download started...", 0, totalSize)
      Log.d("VoskSpeech", "File size: ${totalSize / 1024 / 1024}MB")

      // Try parallel download first, fallback to sequential
      val downloadSuccess = if (totalSize > 0) {
//        tryParallelDownloadWithProgress(modelFile, totalSize) ||
        tryOptimizedSequentialDownloadWithProgress(modelFile, totalSize)
      } else {
        tryOptimizedSequentialDownloadWithProgress(modelFile, totalSize)
      }

      if (!downloadSuccess) {
        sendDownloadProgress(0, "Download failed")
        return null
      }

      sendDownloadProgress(80, "Download completed, extracting...")
      Log.d("VoskSpeech", "Download completed, starting extraction...")

      // Extract with progress updates
      extractZipFileWithProgress(modelFile, modelDir.parentFile!!)

      if (!isValidModel(modelDir)) {
        sendDownloadProgress(0, "Model validation failed")
        Log.e("VoskSpeech", "Model extraction failed or incomplete")
        deleteDirectory(modelDir)
        modelFile.delete()
        return null
      } else {
        modelFile.delete()
        sendDownloadProgress(100, "Model ready!")
        Log.d("VoskSpeech", "Model ready at: ${modelDir.absolutePath}")
        return modelDir.absolutePath
      }

    } catch (e: Exception) {
      sendDownloadProgress(0, "Download error: ${e.message}")
      Log.e("VoskSpeech", "Error downloading model", e)
      modelFile.delete()
      deleteDirectory(modelDir)
      return null
    } finally {
      isModelDownloading = false
    }
  }


//  private fun tryParallelDownload(modelFile: File): Boolean {
//    try {
//      // First, get the file size and check if server supports range requests
//      val url = URL(modelUrl)
//      val headConnection = url.openConnection() as HttpURLConnection
//      headConnection.requestMethod = "HEAD"
//      headConnection.connectTimeout = connectionTimeout
//      headConnection.readTimeout = readTimeout
//      headConnection.setRequestProperty("User-Agent", "Vosk-Android-Plugin-Enhanced")
//
//      val totalSize = headConnection.contentLengthLong
//      val acceptRanges = headConnection.getHeaderField("Accept-Ranges")
//      headConnection.disconnect()
//
//      if (totalSize <= 0 || acceptRanges?.lowercase() != "bytes") {
//        Log.d("VoskSpeech", "Server doesn't support range requests or unknown file size, falling back to sequential")
//        return false
//      }
//
//      Log.d("VoskSpeech", "Starting parallel download. Total size: ${totalSize / 1024 / 1024}MB")
//
//      // Calculate chunk size and number of parts
//      val numParts = min(4, (totalSize / chunkSize).toInt().coerceAtLeast(1))
//      val partSize = totalSize / numParts
//      val downloadedSize = AtomicLong(0)
//
//      // Create temporary files for each part
//      val partFiles = mutableListOf<File>()
//      val futures = mutableListOf<CompletableFuture<Boolean>>()
//
//      for (i in 0 until numParts) {
//        val start = i * partSize
//        val end = if (i == numParts - 1) totalSize - 1 else (i + 1) * partSize - 1
//        val partFile = File(modelFile.parent, "${modelFile.name}.part$i")
//        partFiles.add(partFile)
//
//        val future = CompletableFuture.supplyAsync({
//          downloadPart(start, end, partFile, downloadedSize, totalSize)
//        }, downloadExecutorService)
//
//        futures.add(future)
//      }
//
//      // Wait for all parts to complete
//      val results = futures.map { it.get(10, TimeUnit.MINUTES) }
//
//      if (results.all { it }) {
//        // Combine all parts
//        combineParts(partFiles, modelFile)
//
//        // Clean up part files
//        partFiles.forEach { it.delete() }
//
//        Log.d("VoskSpeech", "Parallel download completed successfully")
//        return true
//      } else {
//        // Clean up on failure
//        partFiles.forEach { it.delete() }
//        return false
//      }
//
//    } catch (e: Exception) {
//      Log.e("VoskSpeech", "Parallel download failed", e)
//      return false
//    }
//  }

  private fun downloadPart(start: Long, end: Long, partFile: File, downloadedSize: AtomicLong, totalSize: Long): Boolean {
    try {
      val url = URL(modelUrl)
      val connection = url.openConnection() as HttpURLConnection
      connection.connectTimeout = connectionTimeout
      connection.readTimeout = readTimeout
      connection.requestMethod = "GET"
      connection.setRequestProperty("User-Agent", "Vosk-Android-Plugin-Enhanced")
      connection.setRequestProperty("Range", "bytes=$start-$end")
      connection.setRequestProperty("Connection", "close")

      if (connection.responseCode !in 200..299) {
        Log.e("VoskSpeech", "Part download failed with response code: ${connection.responseCode}")
        connection.disconnect()
        return false
      }

      connection.inputStream.use { input ->
        FileOutputStream(partFile).use { output ->
          val buffer = ByteArray(bufferSize)
          var bytesRead: Int
          var lastLogTime = System.currentTimeMillis()

          while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            val currentDownloaded = downloadedSize.addAndGet(bytesRead.toLong())

            // Log progress every 2 seconds
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastLogTime > 2000) {
              val progress = ((currentDownloaded * 100) / totalSize).toInt()
              Log.d("VoskSpeech", "Download progress: $progress% (${currentDownloaded / 1024 / 1024}MB / ${totalSize / 1024 / 1024}MB)")
              lastLogTime = currentTime
            }
          }
        }
      }

      connection.disconnect()
      return true

    } catch (e: Exception) {
      Log.e("VoskSpeech", "Error downloading part $start-$end", e)
      return false
    }
  }

  private fun combineParts(partFiles: List<File>, outputFile: File) {
    FileOutputStream(outputFile).use { output ->
      partFiles.forEach { partFile ->
        FileInputStream(partFile).use { input ->
          input.copyTo(output, bufferSize)
        }
      }
    }
  }

  private fun tryOptimizedSequentialDownloadWithProgress(modelFile: File, totalSize: Long): Boolean {
    try {
      sendDownloadProgress(20, "Starting sequential download...")

      val url = URL(modelUrl)
      val connection = url.openConnection() as HttpURLConnection
      connection.connectTimeout = connectionTimeout
      connection.readTimeout = readTimeout
      connection.requestMethod = "GET"
      connection.setRequestProperty("User-Agent", "Vosk-Android-Plugin-Enhanced")
      connection.setRequestProperty("Accept-Encoding", "identity")

      var downloadedSize = 0L
      var lastProgressUpdate = System.currentTimeMillis()
      val actualTotalSize = if (totalSize > 0) totalSize else connection.contentLengthLong

      Log.d("VoskSpeech", "Sequential download. Total size: ${if (actualTotalSize > 0) "${actualTotalSize / 1024 / 1024}MB" else "Unknown"}")

      connection.inputStream.buffered(bufferSize).use { input ->
        FileOutputStream(modelFile).buffered(bufferSize).use { output ->
          val buffer = ByteArray(bufferSize)
          var bytesRead: Int

          while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            downloadedSize += bytesRead

            // Update progress every 500ms
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProgressUpdate > 500) {
              if (actualTotalSize > 0) {
                val progress = 20 + ((downloadedSize * 50) / actualTotalSize).toInt()
                val speed = (downloadedSize / 1024) / ((currentTime - (lastProgressUpdate - 500)) / 1000.0)
                sendDownloadProgress(
                  progress.coerceAtMost(70),
                  "Downloading... ${speed.toInt()} KB/s",
                  downloadedSize,
                  actualTotalSize
                )
              } else {
                sendDownloadProgress(
                  50,
                  "Downloading... ${downloadedSize / 1024 / 1024}MB",
                  downloadedSize,
                  0
                )
              }
              lastProgressUpdate = currentTime
            }
          }
        }
      }

      connection.disconnect()
      sendDownloadProgress(70, "Download completed")
      Log.d("VoskSpeech", "Sequential download completed. Total downloaded: ${downloadedSize / 1024 / 1024}MB")
      return true

    } catch (e: Exception) {
      sendDownloadProgress(20, "Sequential download failed: ${e.message}")
      Log.e("VoskSpeech", "Sequential download failed", e)
      return false
    }
  }

  private fun extractZipFileWithProgress(zipFile: File, destinationDir: File) {
    Log.d("VoskSpeech", "Starting extraction...")
    var extractedFiles = 0
    val startTime = System.currentTimeMillis()

    ZipInputStream(BufferedInputStream(FileInputStream(zipFile), bufferSize)).use { zipInput ->
      var entry: ZipEntry? = zipInput.nextEntry

      while (entry != null) {
        val file = File(destinationDir, entry.name)

        // Security check to prevent zip slip
        if (!file.canonicalPath.startsWith(destinationDir.canonicalPath)) {
          throw SecurityException("Zip entry is outside target directory: ${entry.name}")
        }

        if (entry.isDirectory) {
          file.mkdirs()
        } else {
          // Create parent directories if they don't exist
          file.parentFile?.mkdirs()

          BufferedOutputStream(FileOutputStream(file), bufferSize).use { output ->
            val buffer = ByteArray(bufferSize)
            var bytesRead: Int
            while (zipInput.read(buffer).also { bytesRead = it } != -1) {
              output.write(buffer, 0, bytesRead)
            }
          }

          extractedFiles++
          if (extractedFiles % 100 == 0) {
            Log.d("VoskSpeech", "Extracted $extractedFiles files...")
          }
        }

        zipInput.closeEntry()
        entry = zipInput.nextEntry
      }
    }

    val extractionTime = (System.currentTimeMillis() - startTime) / 1000.0
    Log.d("VoskSpeech", "Extraction completed. $extractedFiles files extracted in ${extractionTime}s")
  }

  private fun deleteDirectory(directory: File): Boolean {
    if (directory.exists()) {
      directory.listFiles()?.forEach { file ->
        if (file.isDirectory) {
          deleteDirectory(file)
        } else {
          file.delete()
        }
      }
    }
    return directory.delete()
  }

  private fun hasRecordAudioPermission(): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
  }

  private fun requestRecordAudioPermission() {
//    ContextCompat.requestPermissions(
//      this,
//      arrayOf(android.Manifest.permission.RECORD_AUDIO),
//      RECORD_AUDIO_PERMISSION_REQUEST
//    )
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "recognize" -> {
        if (!hasRecordAudioPermission()) {
          requestRecordAudioPermission()
          result.error(
            "PERMISSION_REQUESTED",
            "Microphone permission requested from user",
            null
          )
          return
        }

        if (!isInitialized) {
          result.error("NOT_INITIALIZED", "Vosk library is not initialized", null)
          return
        }

        if (!isModelReady) {
          result.error("MODEL_NOT_READY", "Vosk model is not ready. Please wait for download to complete.", null)
          return
        }

        if (isModelDownloading) {
          result.error("MODEL_DOWNLOADING", "Model is currently downloading. Please wait.", null)
          return
        }

        activeResult = result
        val type = call.argument<String>("type")
        val languageCode = call.argument<String>("languageCode") ?: "en-US"
        val timeoutMillis = call.argument<Int>("timeout") ?: 5000
        val sentence = call.argument<String>("sentence") ?: ""

        try {
          when (type) {
            "alphabet" -> languageHandlers.handleAlphabetRecognition(languageCode, timeoutMillis, sentence)
            "koreanAlphabet" -> languageHandlers.handleKoreanAlphabetRecognition(languageCode, timeoutMillis, sentence)
            "number" -> languageHandlers.handleNumberRecognition(timeoutMillis, sentence)
            "englishWordsOrSentence" -> languageHandlers.handleWordsRecognition(languageCode, timeoutMillis, sentence)
            "japaneseAlphabet" -> languageHandlers.handleJapaneseRecognition(languageCode, timeoutMillis, sentence)
            "koreanNumber" -> languageHandlers.handleKoreanNumberRecognition(languageCode, timeoutMillis, sentence)
            "allLanguageSupport" -> languageHandlers.handleAllLanguages(languageCode, timeoutMillis, sentence)
            "paragraphsMapping" -> languageHandlers.handleParagraphMapping(languageCode, timeoutMillis, sentence)
            else -> {
              activeResult?.error("INVALID_TYPE", "Unsupported type", null)
              activeResult = null
            }
          }
        } catch (e: Exception) {
          Log.e("VoskSpeech", "Error in recognize method", e)
          activeResult?.error("RECOGNITION_ERROR", "Error starting recognition", e.message)
          activeResult = null
        }
      }

      "stopRecognition" -> {
        stopRecognition()
        result.success(true)
      }

      "isListening" -> {
        result.success(isRecording)
      }

      "isModelReady" -> {
        result.success(isModelReady)
      }

      "getRecordedAudio" ->{}

      "downloadModel" -> {
        if (isModelReady) {
          result.success(true)
        } else {
          executorService.execute {
            Handler(Looper.getMainLooper()).post {
              result.success(isModelReady)
            }
          }
        }
      }

      else -> result.notImplemented()
    }
  }

  fun updateHighlightedText(spokenText: String, words: List<String>, paragraph: String): Map<String, Any> {
    val highlightedIndices = mutableListOf<Map<String, Int>>()
    val spokenWords = spokenText.lowercase().split(" ").filter { it.isNotEmpty() }
    val lowerWords = words.map { it.lowercase() }

    val wordPositions = mutableMapOf<String, MutableList<Int>>()
    for (i in lowerWords.indices) {
      val word = lowerWords[i]
      if (!wordPositions.containsKey(word)) {
        wordPositions[word] = mutableListOf()
      }
      wordPositions[word]?.add(i)
    }

    for (spokenWord in spokenWords) {
      val positions = wordPositions[spokenWord.lowercase()] ?: continue

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

  fun startVoskRecognition(timeoutMillis: Int, sentence: String) {
    if (!isModelValid()) {
      activeResult?.error("MODEL_ERROR", "Vosk model not ready", null)
      activeResult = null
      return
    }

    try {
      expectedSentence = sentence

      recognizer = if (sentence.isNotEmpty()) {
        val grammar = createGrammarFromSentence(sentence)
        Recognizer(model, sampleRate.toFloat(), grammar)
      } else {
        Recognizer(model, sampleRate.toFloat())
      }

      val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
      if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
        activeResult?.error("AUDIO_ERROR", "Invalid audio configuration", null)
        activeResult = null
        return
      }

      audioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize * 2)

      if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
        activeResult?.error("AUDIO_ERROR", "AudioRecord initialization failed", null)
        activeResult = null
        return
      }

      isRecording = true
      audioRecord?.startRecording()

      // Prepare WAV file
      val outputFile = File(context.cacheDir, "vosk_recording.wav")
      val outputStream = FileOutputStream(outputFile)
      val dataOutputStream = DataOutputStream(BufferedOutputStream(outputStream))

      // Write placeholder WAV header (will update later when we know file size)
      utils.writeWavHeader(dataOutputStream, sampleRate, 1, 16)

      // Set timeout
      timeoutHandler = Handler(Looper.getMainLooper())
      timeoutRunnable = Runnable {
        stopRecognition()
        activeResult?.error("TIMEOUT", "Recognition timeout", null)
        activeResult = null
      }
      timeoutHandler?.postDelayed(timeoutRunnable!!, timeoutMillis.toLong())

      // Start recording thread
      recordingThread = thread {
        val buffer = ByteArray(bufferSize)

        try {
          while (isRecording && audioRecord != null &&
            audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
          ) {
            val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0

            if (bytesRead > 0) {
              // Store raw PCM in WAV
              dataOutputStream.write(buffer, 0, bytesRead)

              // Feed Vosk
              if (recognizer?.acceptWaveForm(buffer, bytesRead) == true) {
                val result = recognizer?.result
                if (!result.isNullOrEmpty()) {
                  processVoskResult(result, true)
                }
              } else {
                val partialResult = recognizer?.partialResult
                if (!partialResult.isNullOrEmpty()) {
                  processPartialResult(partialResult)
                }
              }
            }
          }
        } finally {
          // Close WAV properly
          dataOutputStream.flush()
          dataOutputStream.close()

          // Fix WAV header (update file sizes)
          utils.updateWavHeader(outputFile)
          Log.d("VoskSpeech", "Audio stored at: ${outputFile.absolutePath}")
        }
      }

    } catch (e: Exception) {
      Log.e("VoskSpeech", "Error starting recognition", e)
      activeResult?.error("RECOGNITION_ERROR", "Failed to start recognition", e.message)
      activeResult = null
    }
  }


  private fun createGrammarFromSentence(sentence: String): String {
    // Clean sentence
    val cleanSentence = sentence
      .lowercase()
      .replace(Regex("[^a-zA-Z0-9\\s]"), "")
      .trim()

    // Optional: include variations with/without small words
    val withoutShortWords = cleanSentence.replace("\\b(is|a|the|of|and|for|up)\\b".toRegex(), "").trim()

    // JSON array of full sentences
    val grammar = if (withoutShortWords != cleanSentence) {
      "[\"$cleanSentence\", \"$withoutShortWords\"]"
    } else {
      "[\"$cleanSentence\"]"
    }

    Log.d("VoskSpeech", "Grammar JSON: $grammar")
    return grammar
  }


  private fun isModelValid(): Boolean {
    return model != null && isModelReady && !isModelDownloading
  }

  private fun calculateConfidence(recognizedText: String, expectedSentence: String): Double {
    if (expectedSentence.isEmpty()) {
      return 1.0 // If no expected sentence, return full confidence
    }

    // Clean both sentences for comparison
    val cleanRecognized = recognizedText.lowercase()
      .replace(Regex("[^a-zA-Z0-9\\s]"), "")
      .trim()
      .split("\\s+".toRegex())
      .filter { it.isNotBlank() }

    val cleanExpected = expectedSentence.lowercase()
      .replace(Regex("[^a-zA-Z0-9\\s]"), "")
      .trim()
      .split("\\s+".toRegex())
      .filter { it.isNotBlank() }

    if (cleanExpected.isEmpty()) {
      return 1.0
    }

    Log.d("VoskSpeech", "Recognized words: $cleanRecognized")
    Log.d("VoskSpeech", "Expected words: $cleanExpected")

    // Calculate word-level accuracy
    var matchingWords = 0
    var totalWords = cleanExpected.size

    // Check each expected word against recognized words
    for (expectedWord in cleanExpected) {
      if (cleanRecognized.contains(expectedWord)) {
        matchingWords++
      }
    }

    // Also consider word order and sequence
    var sequenceScore = 0.0
    val minLength = minOf(cleanRecognized.size, cleanExpected.size)

    for (i in 0 until minLength) {
      if (i < cleanRecognized.size && i < cleanExpected.size) {
        if (cleanRecognized[i] == cleanExpected[i]) {
          sequenceScore += 1.0
        }
      }
    }

    // Normalize sequence score
    if (cleanExpected.isNotEmpty()) {
      sequenceScore /= cleanExpected.size
    }

    // Calculate final confidence
    val wordAccuracy = matchingWords.toDouble() / totalWords
    val finalConfidence = (wordAccuracy * 0.7 + sequenceScore * 0.3) // 70% word matching, 30% sequence

    Log.d("VoskSpeech", "Word accuracy: $wordAccuracy, Sequence score: $sequenceScore, Final confidence: $finalConfidence")

    return minOf(1.0, maxOf(0.0, finalConfidence))
  }

  private fun shouldReturnExpectedSentence(confidence: Double): Boolean {
    return confidence >= 0.9
  }

  private fun processVoskResult(result: String, isFinal: Boolean) {
    try {
      val jsonResult = JSONObject(result)
      val recognizedText = jsonResult.optString("text", "")

      if (isFinal && recognizedText.isNotEmpty()) {
        Handler(Looper.getMainLooper()).post {
          // Calculate actual confidence based on expected sentence
          val actualConfidence = calculateConfidence(recognizedText, expectedSentence)

          // Determine what text to return
          val finalText = if (shouldReturnExpectedSentence(actualConfidence) && expectedSentence.isNotEmpty()) {
            Log.d("VoskSpeech", "High confidence ($actualConfidence), returning expected sentence")
            expectedSentence
          } else {
            Log.d("VoskSpeech", "Lower confidence ($actualConfidence), returning recognized text")
            recognizedText
          }

          val resultMap = mapOf(
            "correctedPhrase" to finalText,
            "confidence" to actualConfidence,
            "detailedAnalysis" to mapOf(
              "recognizedText" to recognizedText,
              "expectedText" to expectedSentence,
              "actualConfidence" to actualConfidence
            )
          )

          Log.d("VoskSpeech", "Final result - Text: $finalText, Confidence: $actualConfidence")
          activeResult?.success(resultMap)
          stopRecognition()
        }
      }
    } catch (e: Exception) {
      Log.e("VoskSpeech", "Error processing Vosk result", e)
    }
  }

  private fun processPartialResult(partialResult: String) {
    try {
      val jsonResult = JSONObject(partialResult)
      val partial = jsonResult.optString("partial", "")

      if (partial.isNotEmpty()) {
        Handler(Looper.getMainLooper()).post {
          // Calculate confidence for partial result too
          val partialConfidence = calculateConfidence(partial, expectedSentence) * 0.8 // Reduce confidence for partial results

          val resultMap = mapOf(
            "correctedPhrase" to partial,
            "confidence" to partialConfidence,
            "detailedAnalysis" to mapOf(
              "recognizedText" to partial,
              "expectedText" to expectedSentence,
              "actualConfidence" to partialConfidence,
              "isPartial" to true
            )
          )
          eventSink?.success(resultMap)
        }
      }
    } catch (e: Exception) {
      Log.e("VoskSpeech", "Error processing partial result", e)
    }
  }

  // Add this property at class level to store expected sentence
  private var expectedSentence: String = ""


  private fun stopRecognition() {
    if (!isRecording) return
    isRecording = false

    try {
      // 1. Stop the recording thread first
      recordingThread?.let { thread ->
        thread.interrupt()
        thread.join(200) // wait briefly to avoid race
      }
      recordingThread = null

      // 2. Stop and release AudioRecord safely
      audioRecord?.apply {
        try {
          stop()
        } catch (e: Exception) {
          Log.w("VoskSpeech", "AudioRecord stop failed", e)
        }
        try {
          release()
        } catch (e: Exception) {
          Log.w("VoskSpeech", "AudioRecord release failed", e)
        }
      }
      audioRecord = null

      // 3. Get final result before closing recognizer
      recognizer?.let { rec ->
        val finalResult = rec.finalResult
        if (!finalResult.isNullOrEmpty() && activeResult != null) {
          processVoskResult(finalResult, true)
        }
        rec.close()
      }
      recognizer = null

      // 4. Clean up handler
      timeoutRunnable?.let { r ->
        timeoutHandler?.removeCallbacks(r)
      }
      timeoutRunnable = null
      timeoutHandler = null

    } catch (e: Exception) {
      Log.e("VoskSpeech", "Error stopping recognition", e)
    }
  }

  //  android built in mode for numbers and paragraph mappings:
  private fun initializeSpeechRecognizer() {
    // Only initialize if we have permission and don't already have an instance
    if (speechRecognizer == null && hasRecordAudioPermission()) {
      try {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        Log.d("SpeechRecognition", "SpeechRecognizer pre-initialized")
      } catch (e: Exception) {
        Log.e("SpeechRecognition", "Failed to pre-initialize SpeechRecognizer", e)
      }
    }
  }

  fun startRecognition(lang: String, mapper: (Map<String, Double>) -> Any, timeoutMillis: Int, paragraph: String = "", keepListening: Boolean) {
    // Set processing flags
    isProcessing = true
    isListening = true

    // Ensure we have a SpeechRecognizer instance
    if (speechRecognizer == null) {
      initializeSpeechRecognizer()
    }

    // If still null, create one (fallback)
    if (speechRecognizer == null) {
      speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    }

    val intent = if(keepListening) {
      Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra("android.speech.extra.GET_AUDIO_FORMAT", "audio/AMR_WB")
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(lang))
      }
    } else {
      Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(lang))
      }
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
      cleanup()
    }
    timeoutHandler?.postDelayed(timeoutRunnable!!, timeoutMillis.toLong())

    speechRecognizer?.setRecognitionListener(object : RecognitionListener {
      override fun onResults(results: Bundle) {
        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

        if (!matches.isNullOrEmpty()) {
          try {
            if (keepListening) {
              val firstMatch = matches.first()
              recognizedResults.add(firstMatch)
              val accumulatedText = mapOf(recognizedResults.joinToString(" ") to 0.0)

              eventSink?.success(mapper(accumulatedText))
              speechRecognizer?.startListening(intent)
            } else {
              recognizedResults.clear()
              recognizedResults.addAll(matches)
              isListening = false

              // Apply the mapper to process the results
              val mappedMatches = mapOf(matches.first() to 0.0)
              val finalResult = mapper(mappedMatches)

              Log.d("SpeechRecognition", "Final result from mapper: $finalResult")

              // The mapper now returns the result directly, so just use it
              val resultToReturn = when (finalResult) {
                is Map<*, *> -> {
                  try {
                    @Suppress("UNCHECKED_CAST")
                    finalResult as Map<String, Any>
                  } catch (e: ClassCastException) {
                    Log.e("SpeechRecognition", "Error casting final result", e)
                    mapOf(
                      "correctedPhrase" to finalResult.toString(),
                      "confidence" to 0.0,
                      "detailedAnalysis" to false
                    )
                  }
                }
                else -> {
                  mapOf(
                    "correctedPhrase" to finalResult.toString(),
                    "confidence" to 0.0,
                    "detailedAnalysis" to false
                  )
                }
              }

              Log.d("SpeechRecognition", "Sending final result: $resultToReturn")
              activeResult?.success(resultToReturn)
              speechRecognizer?.cancel()
              cleanup()
            }
          } catch (e: Exception) {
            Log.e("SpeechRecognition", "Error processing results", e)
            activeResult?.error("PROCESSING_ERROR", "Error processing speech results", e.message)
            cleanup()
          }
        } else {
          if (keepListening) {
            speechRecognizer?.startListening(intent)
          } else {
            isListening = false
            activeResult?.error("NO_MATCH", "No speech recognized", null)
            speechRecognizer?.cancel()
            cleanup()
          }
        }
      }

      override fun onPartialResults(partialResults: Bundle?) {
        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { partialList ->
          if (partialList.isNotEmpty() && paragraph.isNotEmpty()) {
            try {
              if (keepListening) {
                val currentPartial = partialList.firstOrNull { paragraph.contains(it) } ?: partialList.first()
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
                val correctedText = languageHandlers.correctRecognizedPhrase(partialList, paragraph)
                eventSink?.success(mapper(correctedText))
              }
            } catch (e: Exception) { Log.e("SpeechRecognition", "Error processing partial results", e) }
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
          Log.e("SpeechRecognition", "Fatal error occurred: ${getErrorText(error)}")
          activeResult?.error("SPEECH_ERROR", getErrorText(error), null)
          speechRecognizer?.cancel()
          speechRecognizer?.destroy()
          cleanup()
        }
      }

      override fun onRmsChanged(rmsdB: Float) {}
      override fun onEndOfSpeech() {}
      override fun onReadyForSpeech(params: Bundle?) {}
      override fun onBeginningOfSpeech() {}
      override fun onBufferReceived(buffer: ByteArray?) {}
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
    else -> "Unknown error"
  }

  private fun cleanup() {
    isProcessing = false
    isListening = false
    timeoutHandler?.removeCallbacks(timeoutRunnable!!)
    timeoutHandler = null
    timeoutRunnable = null
    activeResult = null
    speechRecognizer?.cancel()
  }
}