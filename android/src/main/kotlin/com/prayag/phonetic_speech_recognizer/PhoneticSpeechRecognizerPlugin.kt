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
import okhttp3.*
import okio.buffer
import okio.sink
import okio.source
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import com.prayag.phonetic_speech_recognizer.LanguageHandlers

class PhoneticSpeechRecognizerPlugin : FlutterPlugin, MethodChannel.MethodCallHandler,
  EventChannel.StreamHandler, ActivityAware {

  private lateinit var context: Context
  private lateinit var channel: MethodChannel
  private lateinit var eventChannel: EventChannel
  private lateinit var eventChannelDownload: EventChannel
  private var eventSink: EventChannel.EventSink? = null
  private var eventSinkDownload: EventChannel.EventSink? = null
  var activeResult: MethodChannel.Result? = null

  private var useGrammar: Boolean = false

  private var hasProcessedFinalResult: Boolean = false
  private var hasSpeechBeenDetected: Boolean = false

  private var model: Model? = null
  private var recognizer: Recognizer? = null
  private var audioRecord: AudioRecord? = null
  private var isRecording = false
  private var recordingThread: Thread? = null
  private var timeoutHandler: Handler? = null
  private var timeoutRunnable: Runnable? = null
  private var activity: Activity? = null
  private var executorService: ExecutorService = Executors.newCachedThreadPool()
  private var downloadExecutorService: ExecutorService = Executors.newFixedThreadPool(8) // Increased threads
  private var isModelDownloading = AtomicBoolean(false)
  private var isModelReady = false
  private var isInitialized = false

  private val utils = Utils()
  private val RECORD_AUDIO_PERMISSION_REQUEST = 1001

  private var isProcessing: Boolean = false
  private var isListening = false

  // Model configuration
  private val modelUrl = "https://agimgcdndev.b-cdn.net/vosk-assets/vosk-model-en-us-0.22-lgraph.zip"
  private val modelFileName = "vosk-model-en-us-0.22-lgraph.zip"
  private val modelDirName = "vosk-model-en-us-0.22-lgraph"
  private val MODEL_SIZE_BYTES = 128L * 1024 * 1024 // 128 MB

  private var lastSpeechTime: Long = 0
  private val silenceThresholdMs = 3000L // 3 sec

  // Ultra-optimized download configuration
  private val maxRetries = 3
  private val maxConcurrentConnections = 8 // Increased for fast CDN
  private val chunkSizeBytes = 2 * 1024 * 1024 // 2MB chunks for optimal CDN performance
  private val connectionTimeout = 10000 // 10 seconds
  private val readTimeout = 30000 // 30 seconds
  private val bufferSize = 128 * 1024 // 128KB buffer for maximum I/O performance

  // Performance monitoring
  private val downloadStartTime = AtomicLong(0)
  private val bytesDownloaded = AtomicLong(0)

  // Optimized HTTP client for maximum CDN performance
  private val ultraFastClient by lazy {
    OkHttpClient.Builder()
      .protocols(listOf(Protocol.QUIC, Protocol.HTTP_2, Protocol.HTTP_1_1))
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .writeTimeout(60, TimeUnit.SECONDS)
      .callTimeout(0, TimeUnit.SECONDS)
      // Maximum connection pool for parallel downloads
      .connectionPool(ConnectionPool(20, 5, TimeUnit.MINUTES))
      // Aggressive connection keep-alive
      .retryOnConnectionFailure(true)
      // Disable compression since we're downloading already compressed files
      .addNetworkInterceptor { chain ->
        val request = chain.request().newBuilder()
          .removeHeader("Accept-Encoding")
          .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:117.0) Chrome/117.0.0.0")
          .header("Connection", "keep-alive")
          .header("Cache-Control", "no-cache")
          .build()
        chain.proceed(request)
      }
      .build()
  }

  private lateinit var languageHandlers: LanguageHandlers
  private var speechRecognizer: SpeechRecognizer? = null

  // Audio configuration
  private val sampleRate = 16000
  private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
  private val channelConfig = AudioFormat.CHANNEL_IN_MONO
  private val audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION

  companion object {
    private var isPluginInitialized = false
  }
  private val initializationLock = Any() // For thread-safe initialization

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    synchronized(initializationLock) {
      if (isPluginInitialized) {
        Log.d("VoskSpeech", "Plugin already initialized, skipping")
        return
      }
      isPluginInitialized = true
    }

    context = binding.applicationContext
    channel = MethodChannel(binding.binaryMessenger, "phonetic_speech_recognizer")
    channel.setMethodCallHandler(this)

    eventChannel = EventChannel(binding.binaryMessenger, "phonetic_speech_recognizer/partial_results")
    eventChannel.setStreamHandler(this)

    eventChannelDownload = EventChannel(binding.binaryMessenger, "download_model_progress")
    eventChannelDownload.setStreamHandler(this)

    languageHandlers = LanguageHandlers(context)
    languageHandlers.setPluginInstance(this)

    initializeVosk()
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
    eventChannelDownload.setStreamHandler(null)
    cleanup()
    executorService.shutdown()
    downloadExecutorService.shutdown()
    ultraFastClient.dispatcher.executorService.shutdown()
    ultraFastClient.connectionPool.evictAll()

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

  private fun sendDownloadProgress(progress: Int, status: String, downloaded: Long = 0, total: Long = 0, speedMBps: Double = 0.0) {
    Handler(Looper.getMainLooper()).post {
      val progressData = mapOf(
        "progress" to progress,
        "status" to status,
        "downloadedBytes" to downloaded,
        "totalBytes" to total,
        "downloadedMB" to (downloaded / 1024 / 1024).toInt(),
        "totalMB" to (total / 1024 / 1024).toInt(),
        "speedMBps" to speedMBps
      )
      eventSinkDownload?.success(progressData)
      Log.d("VoskSpeech", "Progress: $progress% - $status - Speed: ${String.format("%.1f", speedMBps)} MB/s")
    }
  }

  private fun initializeVosk() {
    synchronized(initializationLock) {
      if (isInitialized) {
        Log.d("VoskSpeech", "Vosk already initialized, skipping")
        return
      }
      isInitialized = true
    }

    executorService.execute {
      try {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        Log.d("VoskSpeech", "Vosk library initialized")
        initializeModel()
      } catch (e: Exception) {
        Log.e("VoskSpeech", "Error initializing Vosk library", e)
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

    if (modelDir.exists() && isValidModel(modelDir)) {
      Log.d("VoskSpeech", "Model already exists at: ${modelDir.absolutePath}")
      return modelDir.absolutePath
    }

    return try {
      downloadModelUltraFast(modelFile, modelDir)
    } catch (e: Exception) {
      Log.e("VoskSpeech", "Failed to download model", e)
      null
    }
  }

  private fun isValidModel(modelDir: File): Boolean {
    val requiredFiles = listOf("am", "conf", "graph", "ivector")
    return requiredFiles.all { File(modelDir, it).exists() }
  }

  /**
   * fast download implementation optimized for fast CDNs
   */
  private fun downloadModelUltraFast(modelFile: File, modelDir: File): String? {
    if (!isModelDownloading.compareAndSet(false, true)) {
      Log.d("VoskSpeech", "Model download already in progress")
      return null
    }

    try {
      downloadStartTime.set(System.currentTimeMillis())
      bytesDownloaded.set(0)

      sendDownloadProgress(0, "Initializing ultra-fast download...")
      Log.d("VoskSpeech", "Starting ultra-fast download from: $modelUrl")

      modelFile.parentFile?.mkdirs()
      if (modelDir.exists()) deleteDirectory(modelDir)
      modelDir.mkdirs()

      val totalSize = getFileSize()
      if (totalSize <= 0) {
        sendDownloadProgress(0, "Failed to get file size")
        return null
      }

      sendDownloadProgress(5, "File size: ${totalSize / 1024 / 1024}MB, starting download...")

      val success = performUltraFastDownload(modelFile, totalSize)

      if (!success) {
        sendDownloadProgress(0, "Download failed")
        return null
      }

      sendDownloadProgress(80, "Download completed! Extracting...")
      Log.d("VoskSpeech", "Download completed in ${(System.currentTimeMillis() - downloadStartTime.get()) / 1000.0}s")

      extractZipFileOptimized(modelFile, modelDir.parentFile!!)

      if (!isValidModel(modelDir)) {
        sendDownloadProgress(0, "Model validation failed")
        Log.e("VoskSpeech", "Model extraction failed or incomplete")
        deleteDirectory(modelDir)
        modelFile.delete()
        return null
      } else {
        modelFile.delete()
        sendDownloadProgress(100, "Model ready!")
        val totalTime = (System.currentTimeMillis() - downloadStartTime.get()) / 1000.0
        Log.d("VoskSpeech", "Model ready at: ${modelDir.absolutePath} (Total time: ${totalTime}s)")
        return modelDir.absolutePath
      }

    } catch (e: Exception) {
      sendDownloadProgress(0, "Download error: ${e.message}")
      Log.e("VoskSpeech", "Error downloading model", e)
      modelFile.delete()
      deleteDirectory(modelDir)
      return null
    } finally {
      isModelDownloading.set(false)
    }
  }

  private fun getFileSize(): Long {
    return try {
      val url = URL(modelUrl)
      val connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "HEAD"
      connection.instanceFollowRedirects = true
      connection.connectTimeout = 10000
      connection.readTimeout = 10000
      connection.connect()

      val length = connection.contentLengthLong
      connection.disconnect()

      if (length > 0) length else fetchSizeWithGET(url)
    } catch (e: Exception) {
      Log.e("VoskSpeech", "Error getting file size", e)
      -1
    }
  }

  private fun fetchSizeWithGET(url: URL): Long {
    return try {
      val connection = url.openConnection() as HttpURLConnection
      connection.instanceFollowRedirects = true
      connection.connect()
      val length = connection.contentLengthLong
      connection.disconnect()
      length
    } catch (e: Exception) {
      -1
    }
  }


  private fun performUltraFastDownload(outputFile: File, totalSize: Long): Boolean {
    try {
      // Check if server supports range requests
      val supportsRanges = checkRangeSupport()

      return if (supportsRanges && totalSize > chunkSizeBytes) {
        Log.d("VoskSpeech", "Using parallel download with ${maxConcurrentConnections} connections")
        downloadParallelUltraFast(outputFile, totalSize)
      } else {
        Log.d("VoskSpeech", "Using single-threaded download")
        downloadSingleThreadedOptimized(outputFile, totalSize)
      }
    } catch (e: Exception) {
      Log.e("VoskSpeech", "Download failed", e)
      return false
    }
  }

  private fun checkRangeSupport(): Boolean {
    return try {
      val request = Request.Builder()
        .url(modelUrl)
        .head()
        .header("User-Agent", "Vosk-Android-Plugin-UltraFast")
        .build()

      ultraFastClient.newCall(request).execute().use { response ->
        val acceptRanges = response.header("Accept-Ranges")
        val supportsRanges = acceptRanges?.lowercase() == "bytes"
        Log.d("VoskSpeech", "Range support: $supportsRanges (Accept-Ranges: $acceptRanges)")
        supportsRanges
      }
    } catch (e: Exception) {
      Log.e("VoskSpeech", "Failed to check range support", e)
      false
    }
  }

  private fun downloadParallelUltraFast(outputFile: File, totalSize: Long): Boolean {
    val numChunks = min(maxConcurrentConnections, (totalSize / chunkSizeBytes).toInt().coerceAtLeast(1))
    val chunkSize = totalSize / numChunks

    Log.d("VoskSpeech", "Parallel download: $numChunks chunks of ${chunkSize / 1024 / 1024}MB each")

    val tempFiles = mutableListOf<File>()
    val downloadFutures = mutableListOf<CompletableFuture<Boolean>>()
    val chunkProgress = ConcurrentHashMap<Int, Long>()

    // Initialize chunk progress tracking
    repeat(numChunks) { chunkProgress[it] = 0L }

    // Start progress monitoring
    val progressMonitor = startProgressMonitoring(chunkProgress, totalSize)

    try {
      // Create download tasks for each chunk
      for (i in 0 until numChunks) {
        val rangeStart = i * chunkSize
        val rangeEnd = if (i == numChunks - 1) totalSize - 1 else (i + 1) * chunkSize - 1
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.part$i")
        tempFiles.add(tempFile)

        val future = CompletableFuture.supplyAsync({
          downloadChunkUltraFast(rangeStart, rangeEnd, tempFile, i, chunkProgress)
        }, downloadExecutorService)

        downloadFutures.add(future)
      }

      // Wait for all downloads to complete
      val results = downloadFutures.map { future ->
        try {
          future.get(10, TimeUnit.MINUTES)
        } catch (e: Exception) {
          Log.e("VoskSpeech", "Chunk download failed", e)
          false
        }
      }

      progressMonitor.cancel(true) // Cancel with interruption

      if (results.all { it }) {
        sendDownloadProgress(70, "Combining chunks...")
        val success = combineChunksOptimized(tempFiles, outputFile)
        tempFiles.forEach { it.delete() }
        return success
      } else {
        tempFiles.forEach { it.delete() }
        return false
      }

    } catch (e: Exception) {
      Log.e("VoskSpeech", "Parallel download failed", e)
      tempFiles.forEach { it.delete() }
      return false
    } finally {
      progressMonitor.cancel(true) // Cancel with interruption
    }
  }

  private fun downloadChunkUltraFast(
    rangeStart: Long,
    rangeEnd: Long,
    tempFile: File,
    chunkIndex: Int,
    chunkProgress: ConcurrentHashMap<Int, Long>
  ): Boolean {
    var attempt = 0
    while (attempt < maxRetries) {
      try {
        val request = Request.Builder()
          .url(modelUrl)
          .header("Range", "bytes=$rangeStart-$rangeEnd")
          .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:117.0) Chrome/117.0.0.0")
          .header("Connection", "keep-alive")
          .build()

        val startTime = System.currentTimeMillis()
        ultraFastClient.newCall(request).execute().use { response ->
          if (!response.isSuccessful && response.code != 206) {
            Log.e("VoskSpeech", "Chunk $chunkIndex failed: HTTP ${response.code}")
            attempt++
            return@use
          }

          val responseBody = response.body ?: return false
          var chunkDownloaded = 0L

          responseBody.byteStream().buffered(bufferSize).use { inputStream ->
            FileOutputStream(tempFile).buffered(bufferSize).use { outputStream ->
              val buffer = ByteArray(bufferSize)
              var bytesRead: Int
              while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                val writeStart = System.currentTimeMillis()
                outputStream.write(buffer, 0, bytesRead)
                val writeTime = System.currentTimeMillis() - writeStart
                chunkDownloaded += bytesRead
                chunkProgress[chunkIndex] = chunkDownloaded
              }
            }
          }
          Log.d("VoskSpeech", "Chunk $chunkIndex completed in ${System.currentTimeMillis() - startTime}ms")
          return true
        }
      } catch (e: Exception) {
        attempt++
        Log.e("VoskSpeech", "Chunk $chunkIndex attempt $attempt failed", e)
        if (attempt >= maxRetries) return false
        Thread.sleep(100 * attempt.toLong())
      }
    }
    return false
  }

  private fun downloadSingleThreadedOptimized(outputFile: File, totalSize: Long): Boolean {
    return try {
      val request = Request.Builder()
        .url(modelUrl)
        .header("User-Agent", "Vosk-Android-Plugin-UltraFast")
        .header("Connection", "keep-alive")
        .build()

      ultraFastClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          Log.e("VoskSpeech", "Single download failed: HTTP ${response.code}")
          return false
        }

        val responseBody = response.body ?: return false
        var downloaded = 0L
        var lastProgressTime = System.currentTimeMillis()

        responseBody.byteStream().buffered(bufferSize).use { inputStream ->
          FileOutputStream(outputFile).buffered(bufferSize).use { outputStream ->
            val buffer = ByteArray(bufferSize)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
              outputStream.write(buffer, 0, bytesRead)
              downloaded += bytesRead
              bytesDownloaded.set(downloaded)

              val currentTime = System.currentTimeMillis()
              if (currentTime - lastProgressTime > 500) { // Update every 500ms
                updateSingleThreadProgress(downloaded, totalSize)
                lastProgressTime = currentTime
              }
            }
          }
        }

        Log.d("VoskSpeech", "Single-threaded download completed: ${downloaded / 1024 / 1024}MB")
        true
      }
    } catch (e: Exception) {
      Log.e("VoskSpeech", "Single-threaded download failed", e)
      false
    }
  }

  private fun stringSimilarity(s1: String, s2: String): Double {
    if (s1.isEmpty() || s2.isEmpty()) return 0.0
    if (s1 == s2) return 1.0

    val mtp = matches(s1, s2)
    val m = mtp[0].toDouble()
    if (m == 0.0) return 0.0

    val j = (m / s1.length + m / s2.length + (m - mtp[1]) / m) / 3.0
    val jw = if (j < 0.7) j else j + Math.min(0.1, 1.0 / mtp[3].toDouble()) * mtp[2] * (1 - j)
    return jw
  }

  private fun matches(s1: String, s2: String): IntArray {
    val max: String
    val min: String
    if (s1.length > s2.length) {
      max = s1; min = s2
    } else {
      max = s2; min = s1
    }
    val range = Math.max(max.length / 2 - 1, 0)
    val matchIndexes = IntArray(min.length) { -1 }
    val matchFlags = BooleanArray(max.length)
    var matches = 0
    for (i in min.indices) {
      val c1 = min[i]
      val start = Math.max(i - range, 0)
      val end = Math.min(i + range + 1, max.length)
      for (j in start until end) {
        if (!matchFlags[j] && c1 == max[j]) {
          matchIndexes[i] = j
          matchFlags[j] = true
          matches++
          break
        }
      }
    }
    val ms1 = CharArray(matches)
    val ms2 = CharArray(matches)
    var si = 0
    for (i in min.indices) {
      if (matchIndexes[i] != -1) {
        ms1[si] = min[i]
        si++
      }
    }
    si = 0
    for (j in max.indices) {
      if (matchFlags[j]) {
        ms2[si] = max[j]
        si++
      }
    }
    var transpositions = 0
    for (i in ms1.indices) {
      if (ms1[i] != ms2[i]) transpositions++
    }
    return intArrayOf(matches, transpositions / 2, 0, max.length)
  }


  private fun startProgressMonitoring(
    chunkProgress: ConcurrentHashMap<Int, Long>,
    totalSize: Long
  ): CompletableFuture<Void> {
    val isUpdating = AtomicBoolean(false) // Prevent overlapping updates
    return CompletableFuture.runAsync({
      try {
        var lastProgress = -1 // Track last sent progress to avoid duplicates
        while (!Thread.currentThread().isInterrupted) {
          if (isUpdating.compareAndSet(false, true)) {
            try {
              val totalDownloaded = chunkProgress.values.sum()
              bytesDownloaded.set(totalDownloaded)
              val progress = ((totalDownloaded * 100) / totalSize).toInt().coerceAtMost(100)

              // Only send update if progress has changed significantly
              if (progress != lastProgress && totalDownloaded < totalSize) {
                updateParallelProgress(totalDownloaded, totalSize)
                lastProgress = progress
              }
            } finally {
              isUpdating.set(false)
            }
          }
          Thread.sleep(500) // Update every 500ms
        }
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
      }
    }, downloadExecutorService)
  }


  private fun updateParallelProgress(downloaded: Long, total: Long) {
    val elapsedTime = (System.currentTimeMillis() - downloadStartTime.get()) / 1000.0
    val speedMBps = if (elapsedTime > 0) (downloaded / 1024.0 / 1024.0) / elapsedTime else 0.0

    val actualTotal = if (total > 0) total else MODEL_SIZE_BYTES
    val progress = ((downloaded * 100) / actualTotal).toInt().coerceAtMost(100)

    if (downloaded >= actualTotal) return

    sendDownloadProgress(
      progress,
      "Downloading at ${String.format("%.1f", speedMBps)} MB/s...",
      downloaded,
      actualTotal,
      speedMBps
    )
  }

  private fun updateSingleThreadProgress(downloaded: Long, total: Long) {
    val elapsedTime = (System.currentTimeMillis() - downloadStartTime.get()) / 1000.0
    val speedMBps = if (elapsedTime > 0) (downloaded / 1024.0 / 1024.0) / elapsedTime else 0.0

    val actualTotal = if (total > 0) total else MODEL_SIZE_BYTES
    val progress = ((downloaded * 100) / actualTotal).toInt().coerceAtMost(100)

    if (downloaded >= actualTotal) return

    sendDownloadProgress(
      progress,
      "Downloading at ${String.format("%.1f", speedMBps)} MB/s...",
      downloaded,
      actualTotal,
      speedMBps
    )
  }


  private fun combineChunksOptimized(tempFiles: List<File>, outputFile: File): Boolean {
    return try {
      // Use NIO for faster file operations
      FileOutputStream(outputFile).channel.use { outputChannel ->
        tempFiles.forEach { tempFile ->
          if (!tempFile.exists()) {
            Log.e("VoskSpeech", "Temp file missing: ${tempFile.name}")
            return false
          }

          FileInputStream(tempFile).channel.use { inputChannel ->
            var position = 0L
            val size = inputChannel.size()

            while (position < size) {
              val transferred = inputChannel.transferTo(position, size - position, outputChannel)
              if (transferred <= 0) break
              position += transferred
            }
          }
        }
      }
      true
    } catch (e: Exception) {
      Log.e("VoskSpeech", "Failed to combine chunks", e)
      false
    }
  }

  private fun extractZipFileOptimized(zipFile: File, destinationDir: File) {
    Log.d("VoskSpeech", "Starting optimized extraction...")
    var extractedFiles = 0
    val startTime = System.currentTimeMillis()

    ZipInputStream(BufferedInputStream(FileInputStream(zipFile), bufferSize * 2)).use { zipInput ->
      var entry: ZipEntry? = zipInput.nextEntry

      while (entry != null) {
        val file = File(destinationDir, entry.name)

        // Security check
        if (!file.canonicalPath.startsWith(destinationDir.canonicalPath)) {
          throw SecurityException("Zip entry is outside target directory: ${entry.name}")
        }

        if (entry.isDirectory) {
          file.mkdirs()
        } else {
          file.parentFile?.mkdirs()

          // Use larger buffer for extraction
          BufferedOutputStream(FileOutputStream(file), bufferSize * 2).use { output ->
            val buffer = ByteArray(bufferSize * 2)
            var bytesRead: Int
            while (zipInput.read(buffer).also { bytesRead = it } != -1) {
              output.write(buffer, 0, bytesRead)
            }
          }

          extractedFiles++
          if (extractedFiles % 50 == 0) {
            val progress = 80 + (extractedFiles * 15 / 1000).coerceAtMost(15)
            sendDownloadProgress(progress, "Extracting... ($extractedFiles files)")
          }
        }

        zipInput.closeEntry()
        entry = zipInput.nextEntry
      }
    }

    val extractionTime = (System.currentTimeMillis() - startTime) / 1000.0
    Log.d("VoskSpeech", "Extraction completed: $extractedFiles files in ${extractionTime}s")
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

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "recognize" -> {
        if (!hasRecordAudioPermission()) {
          result.error("PERMISSION_REQUESTED", "Microphone permission requested from user", null)
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

        if (isModelDownloading.get()) {
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

  fun startVoskRecognition(timeoutMillis: Int, sentence: String, getPartialTexts: Boolean = false, useGrammar: Boolean = false) {
    if (isRecording) return

    if (!isModelValid()) {
      activeResult?.error("MODEL_ERROR", "Vosk model not ready", null)
      activeResult = null
      return
    }

    this.useGrammar = useGrammar
    try {
      expectedSentence = sentence
      shouldReturnPartialResults = getPartialTexts
      hasProcessedFinalResult = false

      // FIXED: Use grammar based on the useGrammar parameter
      recognizer = if (useGrammar && sentence.isNotEmpty()) {
        val grammar = createGrammarFromSentence(sentence)
        Log.d("VoskSpeech", "Creating grammar-based recognizer with sentence: $sentence")
        Recognizer(model, sampleRate.toFloat(), grammar)
      } else {
        Log.d("VoskSpeech", "Creating standard recognizer (no grammar)")
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

      val outputFile = File(context.cacheDir, "vosk_recording.wav")
      val outputStream = FileOutputStream(outputFile)
      val dataOutputStream = DataOutputStream(BufferedOutputStream(outputStream))

      utils.writeWavHeader(dataOutputStream, sampleRate, 1, 16)

      // Track silence
      lastSpeechTime = System.currentTimeMillis()
      hasSpeechBeenDetected = false

      // Handler that checks for silence
      timeoutHandler = Handler(Looper.getMainLooper())
      timeoutRunnable = object : Runnable {
        override fun run() {
          val now = System.currentTimeMillis()
          if (now - lastSpeechTime >= silenceThresholdMs && hasSpeechBeenDetected) {
            // Only timeout if we've detected speech and then silence
            Log.d("VoskSpeech", "Silence timeout - processing any final results")
            stopRecognition() // This will process final results
          } else if (!hasSpeechBeenDetected && now - lastSpeechTime >= (silenceThresholdMs * 2)) {
            // Extended timeout if no speech detected at all
            activeResult?.error("SILENCE_TIMEOUT", "No speech detected", null)
            activeResult = null
            stopRecognition()
          } else {
            // Continue monitoring
            timeoutHandler?.postDelayed(this, 1000)
          }
        }
      }
      timeoutHandler?.postDelayed(timeoutRunnable!!, 1000)

      // Recording thread
      recordingThread = thread {
        val buffer = ByteArray(bufferSize)

        try {
          while (isRecording && audioRecord != null &&
            audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
          ) {
            val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0

            if (bytesRead > 0) {
              dataOutputStream.write(buffer, 0, bytesRead)

              // Check for voice activity
              val hasVoiceActivity = hasVoiceActivity(buffer, bytesRead)

              if (recognizer?.acceptWaveForm(buffer, bytesRead) == true) {
                val result = recognizer?.result
                if (!result.isNullOrEmpty()) {
                  hasSpeechBeenDetected = true
                  lastSpeechTime = System.currentTimeMillis()
                  Log.d("VoskSpeech", "Final result from acceptWaveForm: $result")
                  processVoskResult(result, true)
                }
              } else {
                val partialResult = recognizer?.partialResult
                if (!partialResult.isNullOrEmpty()) {
                  val partialText = extractPartialText(partialResult)
                  if (partialText.isNotEmpty()) {
                    hasSpeechBeenDetected = true
                    lastSpeechTime = System.currentTimeMillis()

                    if (shouldReturnPartialResults) {
                      Log.d("VoskSpeech", "Partial result: $partialResult")
                      processPartialResult(partialResult)
                    }
                  }
                } else if (hasVoiceActivity) {
                  // Update speech time for voice activity without recognized text
                  lastSpeechTime = System.currentTimeMillis()
                  hasSpeechBeenDetected = true
                }
              }
            }
          }
        } finally {
          dataOutputStream.flush()
          dataOutputStream.close()
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
    val cleanSentence = sentence
      .lowercase()
      .replace(Regex("[^a-zA-Z0-9\\s]"), "")
      .trim()

    val withoutShortWords = cleanSentence.replace("\\b(is|a|the|of|and|for|up)\\b".toRegex(), "").trim()

    val grammar = if (withoutShortWords != cleanSentence) {
      "[\"$cleanSentence\", \"$withoutShortWords\"]"
    } else {
      "[\"$cleanSentence\"]"
    }

    Log.d("VoskSpeech", "Grammar JSON: $grammar")
    return grammar
  }

  private fun isModelValid(): Boolean {
    return model != null && isModelReady && !isModelDownloading.get()
  }

  private fun calculateConfidence(recognizedText: String, expectedSentence: String): Double {
    if (expectedSentence.isEmpty()) {
      return 1.0
    }

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

    var matchingWords = 0
    val totalWords = cleanExpected.size

    for (expectedWord in cleanExpected) {
      if (cleanRecognized.contains(expectedWord)) {
        matchingWords++
      }
    }

    var sequenceScore = 0.0
    val minLength = minOf(cleanRecognized.size, cleanExpected.size)

    for (i in 0 until minLength) {
      if (i < cleanRecognized.size && i < cleanExpected.size) {
        if (cleanRecognized[i] == cleanExpected[i]) {
          sequenceScore += 1.0
        }
      }
    }

    if (cleanExpected.isNotEmpty()) {
      sequenceScore /= cleanExpected.size
    }

    val wordAccuracy = matchingWords.toDouble() / totalWords
    val finalConfidence = (wordAccuracy * 0.7 + sequenceScore * 0.3)

    Log.d("VoskSpeech", "Word accuracy: $wordAccuracy, Sequence score: $sequenceScore, Final confidence: $finalConfidence")

    return minOf(1.0, maxOf(0.0, finalConfidence))
  }

  // FIXED: Lowered confidence threshold from 0.9 to 0.7
  private fun shouldReturnExpectedSentence(confidence: Double): Boolean {
    return confidence >= 0.7
  }

  // FIXED: Enhanced result processing with better race condition handling
  private fun processVoskResult(result: String, isFinal: Boolean) {
    try {
      val jsonResult = JSONObject(result)
      val recognizedText = jsonResult.optString("text", "").trim()

      Log.d("VoskSpeech", "Processing result - Text: '$recognizedText', isFinal: $isFinal")

      if (isFinal) {
        synchronized(this) {
          if (hasProcessedFinalResult) {
            Log.d("VoskSpeech", "Final result already processed, skipping")
            return
          }
          hasProcessedFinalResult = true
        }

        Handler(Looper.getMainLooper()).post {
          val currentActiveResult = activeResult

          val finalText: String
          val actualConfidence: Double

          if (!useGrammar) {
            // 🔹 Single-word mode: use similarity
            val similarity = stringSimilarity(recognizedText.lowercase(), expectedSentence.lowercase())
            Log.d("VoskSpeech", "Single-word similarity: $similarity")
            actualConfidence = similarity
            finalText = if (similarity >= 0.8) {
              Log.d("VoskSpeech", "High similarity ($similarity), returning expected sentence")
              expectedSentence
            } else {
              recognizedText
            }
          } else {
            // 🔹 Normal mode: use confidence calculation
            actualConfidence = calculateConfidence(recognizedText, expectedSentence)
            finalText = if (shouldReturnExpectedSentence(actualConfidence) && expectedSentence.isNotEmpty()) {
              expectedSentence
            } else {
              recognizedText
            }
          }

          val resultMap = mapOf(
            "correctedPhrase" to finalText,
            "confidence" to actualConfidence,
            "detailedAnalysis" to mapOf(
              "recognizedText" to recognizedText,
              "expectedText" to expectedSentence,
              "actualConfidence" to actualConfidence,
              "isFinal" to true
            )
          )

          Log.d("VoskSpeech", "Final result - Text: $finalText, Confidence: $actualConfidence")
          currentActiveResult?.success(resultMap)
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
          val partialConfidence = calculateConfidence(partial, expectedSentence) * 0.8

          // Use your event sink pattern
          val fullText = mapOf(partial to partialConfidence)
          eventSink?.success(fullText)
//                eventSink?.success(mapper(fullText))

          Log.d("VoskSpeech", "Partial result sent - Text: $partial, Confidence: $partialConfidence")
        }
      }
    } catch (e: Exception) {
      Log.e("VoskSpeech", "Error processing partial result", e)
    }
  }

  private var expectedSentence: String = ""
  private var shouldReturnPartialResults: Boolean = false

  // ENHANCED: stopRecognition with better final result handling
  private fun stopRecognition() {
    if (!isRecording) return

    Log.d("VoskSpeech", "Stopping recognition...")
    isRecording = false

    try {
      // Stop timeout handler first
      timeoutRunnable?.let { r ->
        timeoutHandler?.removeCallbacks(r)
      }
      timeoutRunnable = null
      timeoutHandler = null

      // Stop recording thread
      recordingThread?.let { thread ->
        thread.interrupt()
        thread.join(200)
      }
      recordingThread = null

      // Stop audio recording
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

      // FIXED: Always try to get final result and handle empty results better
      recognizer?.let { rec ->
        try {
          val finalResult = rec.finalResult
          Log.d("VoskSpeech", "Final result from recognizer: $finalResult")

          if (!finalResult.isNullOrEmpty() && !hasProcessedFinalResult) {
            processVoskResult(finalResult, true)
          } else if (!hasProcessedFinalResult && hasSpeechBeenDetected) {
            // If we detected speech but got no final result, create an empty result
            Log.d("VoskSpeech", "Creating empty final result for detected speech")
            processVoskResult(" ", true)
          }
        } catch (e: Exception) {
          Log.e("VoskSpeech", "Error getting final result", e)
        }

        rec.close()
      }
      recognizer = null

    } catch (e: Exception) {
      Log.e("VoskSpeech", "Error stopping recognition", e)
    }
  }

// HELPER FUNCTIONS (added to support the fixes)

  // Voice activity detection using RMS energy
  private fun hasVoiceActivity(buffer: ByteArray, bytesRead: Int): Boolean {
    var sum = 0L
    for (i in 0 until bytesRead step 2) {
      if (i + 1 < bytesRead) {
        val sample = (buffer[i].toInt() and 0xFF) or ((buffer[i + 1].toInt() and 0xFF) shl 8)
        sum += (sample * sample)
      }
    }
    val rms = Math.sqrt(sum.toDouble() / (bytesRead / 2))
    return rms > 500 // Adjust threshold as needed
  }

  // Helper to extract partial text safely
  private fun extractPartialText(partialResult: String): String {
    return try {
      val jsonResult = JSONObject(partialResult)
      jsonResult.optString("partial", "").trim()
    } catch (e: Exception) {
      ""
    }
  }


  private fun initializeSpeechRecognizer() {
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