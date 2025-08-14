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

class PhoneticSpeechRecognizerPlugin : FlutterPlugin, MethodChannel.MethodCallHandler,
  EventChannel.StreamHandler, ActivityAware {

  private lateinit var context: Context
  private lateinit var channel: MethodChannel
  private lateinit var eventChannel: EventChannel
  private var eventSink: EventChannel.EventSink? = null
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

  private val RECORD_AUDIO_PERMISSION_REQUEST = 1001

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

  // Audio configuration
  private val sampleRate = 16000
  private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
  private val channelConfig = AudioFormat.CHANNEL_IN_MONO
  private val audioSource = MediaRecorder.AudioSource.MIC

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    context = binding.applicationContext
    channel = MethodChannel(binding.binaryMessenger, "phonetic_speech_recognizer")
    channel.setMethodCallHandler(this)

    eventChannel = EventChannel(binding.binaryMessenger, "phonetic_speech_recognizer/partial_results")
    eventChannel.setStreamHandler(this)

    // Initialize LanguageHandlers with plugin instance reference
    languageHandlers = LanguageHandlers(context)
    languageHandlers.setPluginInstance(this)

    // Initialize Vosk in background
    initializeVosk()
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
    eventSink = events
  }

  override fun onCancel(arguments: Any?) {
    eventSink = null
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
      Log.d("VoskSpeech", "Starting fast download from: $modelUrl")

      modelFile.parentFile?.mkdirs()
      if (modelDir.exists()) deleteDirectory(modelDir)
      modelDir.mkdirs()

      val url = URL(modelUrl)
      val connection = url.openConnection() as HttpURLConnection
      connection.connectTimeout = 10_000
      connection.readTimeout = 20_000
      connection.instanceFollowRedirects = true
      connection.setRequestProperty("Accept-Encoding", "identity") // avoid gzip overhead

      if (connection.responseCode != HttpURLConnection.HTTP_OK) {
        Log.e("VoskSpeech", "Server returned HTTP ${connection.responseCode}")
        return null
      }

      val totalSize = connection.contentLengthLong
      Log.d("VoskSpeech", "File size: $totalSize bytes")

      // Fast stream copy with large buffer
      connection.inputStream.use { input ->
        FileOutputStream(modelFile).use { output ->
          val buffer = ByteArray(512 * 1024) // 512 KB
          var bytesRead: Int
          var downloaded: Long = 0
          while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            downloaded += bytesRead
            if (totalSize > 0) {
              val progress = (downloaded * 100 / totalSize).toInt()
              Log.d("VoskSpeech", "Download progress: $progress%")
            }
          }
          output.flush()
        }
      }

      Log.d("VoskSpeech", "Download completed, starting extraction...")

      // Extract in background thread for speed
      val extractThread = Thread {
        extractZipFileWithProgress(modelFile, modelDir.parentFile!!)
        if (!isValidModel(modelDir)) {
          Log.e("VoskSpeech", "Model extraction failed or incomplete")
          deleteDirectory(modelDir)
          modelFile.delete()
        } else {
          modelFile.delete()
          Log.d("VoskSpeech", "Model ready at: ${modelDir.absolutePath}")
        }
      }
      extractThread.start()
      extractThread.join() // Wait if you must return synchronously

      return modelDir.absolutePath

    } catch (e: Exception) {
      Log.e("VoskSpeech", "Error downloading model", e)
      modelFile.delete()
      deleteDirectory(modelDir)
      return null
    } finally {
      isModelDownloading = false
    }
  }


  private fun tryParallelDownload(modelFile: File): Boolean {
    try {
      // First, get the file size and check if server supports range requests
      val url = URL(modelUrl)
      val headConnection = url.openConnection() as HttpURLConnection
      headConnection.requestMethod = "HEAD"
      headConnection.connectTimeout = connectionTimeout
      headConnection.readTimeout = readTimeout
      headConnection.setRequestProperty("User-Agent", "Vosk-Android-Plugin-Enhanced")

      val totalSize = headConnection.contentLengthLong
      val acceptRanges = headConnection.getHeaderField("Accept-Ranges")
      headConnection.disconnect()

      if (totalSize <= 0 || acceptRanges?.lowercase() != "bytes") {
        Log.d("VoskSpeech", "Server doesn't support range requests or unknown file size, falling back to sequential")
        return false
      }

      Log.d("VoskSpeech", "Starting parallel download. Total size: ${totalSize / 1024 / 1024}MB")

      // Calculate chunk size and number of parts
      val numParts = min(4, (totalSize / chunkSize).toInt().coerceAtLeast(1))
      val partSize = totalSize / numParts
      val downloadedSize = AtomicLong(0)

      // Create temporary files for each part
      val partFiles = mutableListOf<File>()
      val futures = mutableListOf<CompletableFuture<Boolean>>()

      for (i in 0 until numParts) {
        val start = i * partSize
        val end = if (i == numParts - 1) totalSize - 1 else (i + 1) * partSize - 1
        val partFile = File(modelFile.parent, "${modelFile.name}.part$i")
        partFiles.add(partFile)

        val future = CompletableFuture.supplyAsync({
          downloadPart(start, end, partFile, downloadedSize, totalSize)
        }, downloadExecutorService)

        futures.add(future)
      }

      // Wait for all parts to complete
      val results = futures.map { it.get(10, TimeUnit.MINUTES) }

      if (results.all { it }) {
        // Combine all parts
        combineParts(partFiles, modelFile)

        // Clean up part files
        partFiles.forEach { it.delete() }

        Log.d("VoskSpeech", "Parallel download completed successfully")
        return true
      } else {
        // Clean up on failure
        partFiles.forEach { it.delete() }
        return false
      }

    } catch (e: Exception) {
      Log.e("VoskSpeech", "Parallel download failed", e)
      return false
    }
  }

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

  private fun tryOptimizedSequentialDownload(modelFile: File): Boolean {
    try {
      val url = URL(modelUrl)
      val connection = url.openConnection() as HttpURLConnection
      connection.connectTimeout = connectionTimeout
      connection.readTimeout = readTimeout
      connection.requestMethod = "GET"
      connection.setRequestProperty("User-Agent", "Vosk-Android-Plugin-Enhanced")
      connection.setRequestProperty("Connection", "keep-alive")
      connection.setRequestProperty("Accept-Encoding", "identity") // Prevent compression issues

      val totalSize = connection.contentLengthLong
      var downloadedSize = 0L
      var lastLogTime = System.currentTimeMillis()

      Log.d("VoskSpeech", "Starting optimized sequential download. Total size: ${if (totalSize > 0) "${totalSize / 1024 / 1024}MB" else "Unknown"}")

      connection.inputStream.buffered(bufferSize).use { input ->
        FileOutputStream(modelFile).buffered(bufferSize).use { output ->
          val buffer = ByteArray(bufferSize)
          var bytesRead: Int

          while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            downloadedSize += bytesRead

            // Log progress every 2 seconds
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastLogTime > 2000) {
              if (totalSize > 0) {
                val progress = ((downloadedSize * 100) / totalSize).toInt()
                val speed = (downloadedSize / 1024) / ((currentTime - (lastLogTime - 2000)) / 1000.0)
                Log.d("VoskSpeech", "Download progress: $progress% (${downloadedSize / 1024 / 1024}MB / ${totalSize / 1024 / 1024}MB) Speed: ${speed.toInt()} KB/s")
              } else {
                Log.d("VoskSpeech", "Downloaded: ${downloadedSize / 1024 / 1024}MB")
              }
              lastLogTime = currentTime
            }
          }
        }
      }

      connection.disconnect()
      Log.d("VoskSpeech", "Sequential download completed. Total downloaded: ${downloadedSize / 1024 / 1024}MB")
      return true

    } catch (e: Exception) {
      Log.e("VoskSpeech", "Optimized sequential download failed", e)
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
      // Create recognizer with context if sentence is provided
      recognizer = if (sentence.isNotEmpty()) {
        // Create a grammar-based recognizer using the sentence as context
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

        while (isRecording && audioRecord != null && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
          val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0

          if (bytesRead > 0) {
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
      }

    } catch (e: Exception) {
      Log.e("VoskSpeech", "Error starting recognition", e)
      activeResult?.error("RECOGNITION_ERROR", "Failed to start recognition", e.message)
      activeResult = null
    }
  }

  private fun createGrammarFromSentence(sentence: String): String {
    // Remove punctuation and clean the sentence
    val cleanSentence = sentence
      .lowercase()
      .replace(Regex("[^a-zA-Z0-9\\s]"), "") // Remove punctuation
      .trim()

    val words = cleanSentence
      .split("\\s+".toRegex())
      .distinct()
      .filter { it.isNotBlank() }

    Log.d("VoskSpeech", "Grammar words: $words")

    // Create simple JSON array for Vosk
    val wordList = words.joinToString("\", \"", "[\"", "\"]")
    Log.d("VoskSpeech", "Grammar JSON: $wordList")
    return wordList
  }

  private fun isModelValid(): Boolean {
    return model != null && isModelReady && !isModelDownloading
  }

  private fun processVoskResult(result: String, isFinal: Boolean) {
    try {
      val jsonResult = JSONObject(result)
      val text = jsonResult.optString("text", "")

      if (isFinal && text.isNotEmpty()) {
        Handler(Looper.getMainLooper()).post {
          val resultMap = mapOf(
            "correctedPhrase" to text,
            "confidence" to 1.0,
            "detailedAnalysis" to false
          )
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
          val resultMap = mapOf(
            "correctedPhrase" to partial,
            "confidence" to 0.5,
            "detailedAnalysis" to false
          )
          eventSink?.success(resultMap)
        }
      }
    } catch (e: Exception) {
      Log.e("VoskSpeech", "Error processing partial result", e)
    }
  }

  private fun stopRecognition() {
    isRecording = false

    try {
      audioRecord?.stop()
      audioRecord?.release()
      audioRecord = null

      // Get final result before closing recognizer
      val finalResult = recognizer?.finalResult
      if (!finalResult.isNullOrEmpty() && activeResult != null) {
        processVoskResult(finalResult, true)
      }

      recognizer?.close()
      recognizer = null

      recordingThread?.interrupt()
      recordingThread = null

      timeoutHandler?.removeCallbacks(timeoutRunnable!!)
      timeoutHandler = null
      timeoutRunnable = null

    } catch (e: Exception) {
      Log.e("VoskSpeech", "Error stopping recognition", e)
    }
  }

  private fun cleanup() {
    stopRecognition()
    model?.close()
    model = null
    isModelReady = false
    isInitialized = false
  }
}