import 'dart:async';
import 'dart:developer';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'double_metaphone.dart';
import 'homophones.dart';

enum PhoneticType {
  alphabet,
  koreanAlphabet,
  number,
  englishWordsOrSentence,
  japaneseAlphabet,
  koreanNumber,
  allLanguageSupport,
  paragraphsMapping
}

/// Download progress data class
class DownloadProgress {
  final int progress;
  final String status;
  final int downloadedBytes;
  final int totalBytes;
  final int downloadedMB;
  final int totalMB;

  DownloadProgress({
    required this.progress,
    required this.status,
    required this.downloadedBytes,
    required this.totalBytes,
    required this.downloadedMB,
    required this.totalMB,
  });

  factory DownloadProgress.fromMap(Map<String, dynamic> map) {
    return DownloadProgress(
      progress: map['progress'] ?? 0,
      status: map['status'] ?? '',
      downloadedBytes: map['downloadedBytes'] ?? 0,
      totalBytes: map['totalBytes'] ?? 0,
      downloadedMB: map['downloadedMB'] ?? 0,
      totalMB: map['totalMB'] ?? 0,
    );
  }

  double get progressPercent => progress / 100.0;

  String get formattedProgress {
    if (totalMB > 0) {
      return '$downloadedMB MB / $totalMB MB';
    } else {
      return '$downloadedMB MB downloaded';
    }
  }

  @override
  String toString() {
    return 'DownloadProgress(progress: $progress%, status: $status, size: $formattedProgress)';
  }
}

class PhoneticSpeechRecognizer {
  final Map<String, List<String>> homophones = Homophones.homophones;
  static Timer? _sentenceTimeoutTimer;
  static String? _lastPartial;
  // Function words that should always be highlighted as correct
  static const Set<String> functionWords = {
    'a',
  };

  List<int> errorWordsIndexes = [];
  List<int> errorPronouncationList = [];
  List<int> correctPronouncationList = [];

  static const MethodChannel _channel =
  MethodChannel('phonetic_speech_recognizer');

  static const EventChannel _downloadProgressChannel =
  EventChannel('download_model_progress');
  static Stream<DownloadProgress>? _downloadProgressStream;

  static StreamSubscription<DownloadProgress>? _progressSubscription;

  /// Get download progress stream
  static Stream<DownloadProgress> get downloadProgressStream {
    _downloadProgressStream ??= _downloadProgressChannel
        .receiveBroadcastStream("download_progress")
        .map((data) =>
        DownloadProgress.fromMap(Map<String, dynamic>.from(data)));
    return _downloadProgressStream!;
  }

  /// Download model with progress tracking
  static Future<bool> downloadModelWithProgress() async {
    try {
      // Start listening to progress and log only percentage
      _progressSubscription?.cancel();
      _progressSubscription = downloadProgressStream.listen(
            (progress) {
          print('${progress.progress}%');
        },
      );

      final result = await _channel.invokeMethod('downloadModel');
      return result ?? false;
    } catch (e) {
      print('Error downloading model: $e');
      return false;
    }
  }

  static Future<bool> isModelReady() async {
    try {
      final result = await _channel.invokeMethod('isModelReady');
      return result ?? false;
    } catch (e) {
      print('Error checking model status: $e');
      return false;
    }
  }

  ScrollController controller = ScrollController();

  static Future<String?> getPlatformVersion() async {
    try {
      final String? version = await _channel.invokeMethod('getPlatformVersion');
      return version;
    } on PlatformException catch (e) {
      if (kDebugMode) {
        log("Error: ${e.message}");
      }
      return null;
    }
  }

  static Future<bool> stopRecognition({void Function()? callback}) async {
    await Future.delayed(Duration(milliseconds: 500));
    _sentenceTimeoutTimer?.cancel();
    try {
      final bool result = await _channel.invokeMethod('stopRecognition');
      if (callback != null) {
        callback.call();
      }
      return result;
    } catch (e) {
      throw PlatformException(code: 'STOP_ERROR', message: e.toString());
    }
  }

  static Future<bool> isListening() async {
    try {
      final bool result = await _channel.invokeMethod('isListening');
      return result;
    } catch (e) {
      throw PlatformException(code: 'STOP_ERROR', message: e.toString());
    }
  }

  Stream<String> listenToStream() {
    return getDataStream().map((dynamic data) {
      if (data != null && data is Map) {
        return data.keys.first.toString();
      }
      return "";
    });
  }

// The supporting function that gets the raw data stream
  Stream<dynamic> getDataStream() {
    final EventChannel _eventChannel =
    EventChannel('phonetic_speech_recognizer/partial_results');
    return _eventChannel.receiveBroadcastStream();
  }

  /// Builds a lookup table that maps each word in the input text to its corresponding sentence index.
  /// Empty lookup table List<int> sentenceCounts = List.filled(words.length, 0);
  ///
  /// sentence count increases if atleast half of the words in sentence has been spoken
  ///
  /// Sentence: Hello world. This is sentence two! How are you?
  /// Words: ["Hello", "world", "This", "is", "sentence", "two", "How", "are", "you"]
  /// Lookup table: [1, 1, 2, 2, 2, 2, 3, 3, 3]
  ///
  /// This is done by splitting the text into sentences, then splitting each sentence into words, and then
  /// mapping each word to its corresponding sentence index.
  ///
  /// The reason for this is to allow us to know which sentence a word belongs to when we receive the
  /// partial recognition results from the native code.
  List<int> _buildSentenceCountLookup(String text) {
    List<String> words = text.split(RegExp(r'\s+'));
    List<int> sentenceCounts = List.filled(words.length, 0);

    int currentSentenceCount = 0;
    int wordIndex = 0;

    List<String> sentences = text.split(RegExp(r'[.!?]+\s*'));

    for (int sentenceIndex = 0;
    sentenceIndex < sentences.length;
    sentenceIndex++) {
      String sentence = sentences[sentenceIndex].trim();
      if (sentence.isEmpty) continue;

      List<String> sentenceWords = sentence.split(RegExp(r'\s+'));
      currentSentenceCount = sentenceIndex;

      // Calculate the halfway point of the sentence
      int halfwayPoint = (sentenceWords.length / 2).ceil();
      bool hasReachedHalfway = false;

      for (int i = 0;
      i < sentenceWords.length && wordIndex < words.length;
      i++) {
        // If we've reached the halfway point for the first time, increment the sentence count
        if (i >= halfwayPoint && !hasReachedHalfway) {
          currentSentenceCount++;
          hasReachedHalfway = true;
        }

        sentenceCounts[wordIndex] = currentSentenceCount;
        wordIndex++;
      }
    }

    return sentenceCounts;
  }

  /// Helper method to calculate the position of a word in the rendered text
  double _calculateWordPosition({
    required int wordIndex,
    required List<String> words,
    required double fontSize,
    required double lineSpace,
    required double containerWidth,
  }) {
    if (wordIndex >= words.length) return 0.0;

    // Estimate character width (approximately 0.6 * fontSize for most fonts)
    double charWidth = fontSize * 0.6;
    double spaceWidth = fontSize * 0.3;
    double lineHeight = fontSize * lineSpace;

    double currentX = 0.0;
    double currentY = 0.0;
    int currentLine = 0;

    for (int i = 0; i <= wordIndex; i++) {
      String word = words[i];
      double wordWidth = word.length * charWidth;

      // Check if word fits on current line
      if (currentX + wordWidth > containerWidth && currentX > 0) {
        // Move to next line
        currentLine++;
        currentY = currentLine * lineHeight;
        currentX = 0.0;
      }

      if (i == wordIndex) {
        break;
      }

      currentX += wordWidth + spaceWidth;
    }

    return currentY;
  }

  Widget buildRealTimeHighlightedText({
    required String randomText,
    required String partialText,
    required Color highlightCorrectColor,
    required Color defaultTextColor,
    required Color highlightWrongColor,
    required bool isAutoScroll,
    required int autoScrollSpeed,
    required double fontSize,
    required double lineSpace,
    required double endOfScreen,
    required void Function({
    int? correctPronouncationListLength,
    int? errorPronouncationListLength,
    int? errorWordsIndexesLength,
    int? indexedSentenceCount,
    }) callback,
  }) {
    String cleanText(String text) {
      return text.replaceAll(RegExp(r'[^\w\s]'), '').toLowerCase().trim();
    }

    List<int> sentenceCountLookup = _buildSentenceCountLookup(randomText);

    List<String> originalWords = randomText.split(RegExp(r'\s+'));
    List<String> targetWords = originalWords.map(cleanText).toList();
    List<String> partialWords =
    partialText.split(RegExp(r'\s+')).map(cleanText).toList();

    final int maxLookahead = 2;
    final int maxSkipLimit = 2;
    final Set<int> matchedIndexes = {};
    final Set<int> skippedIndexes = {};
    final Set<int> mispronounceIndexes = {};
    final List<String> errorBuffer = [];
    final int consecutiveErrorThreshold = 2;

    int targetIndex = 0;
    int lastProcessedIndex = -1;

    List<int> errorWordsIndexList = [];
    List<int> errorWordsPronunciationList = [];
    List<int> correctWordsList = [];

    bool isHomophone(String word1, String word2) {
      if (word1 == word2) return true;
      if (homophones.containsKey(word1)) {
        return homophones[word1]!.contains(word2);
      }
      return false;
    }

    bool isMetaphoneMatch(String word1, String word2) {
      List<String> metaphone1 = DoubleMetaphone.encode(word1);
      List<String> metaphone2 = DoubleMetaphone.encode(word2);

      return (metaphone1[0].isNotEmpty && metaphone1[0] == metaphone2[0]) ||
          (metaphone1[1].isNotEmpty && metaphone1[1] == metaphone2[1]) ||
          (metaphone1[0].isNotEmpty && metaphone1[0] == metaphone2[1]) ||
          (metaphone1[1].isNotEmpty && metaphone1[1] == metaphone2[0]);
    }

    bool isFunctionWord(String word) {
      return functionWords.contains(word.toLowerCase());
    }

    double wordSimilarity(String word1, String word2) {
      if (word1.length <= 3 || word2.length <= 3) {
        return word1 == word2 ? 1.0 : 0.0;
      }

      List<List<int>> dp = List.generate(
        word1.length + 1,
            (_) => List.filled(word2.length + 1, 0),
      );

      for (int i = 0; i <= word1.length; i++) {
        dp[i][0] = i;
      }

      for (int j = 0; j <= word2.length; j++) {
        dp[0][j] = j;
      }

      for (int i = 1; i <= word1.length; i++) {
        for (int j = 1; j <= word2.length; j++) {
          int cost = word1[i - 1] == word2[j - 1] ? 0 : 1;
          dp[i][j] = [
            dp[i - 1][j] + 1,
            dp[i][j - 1] + 1,
            dp[i - 1][j - 1] + cost
          ].reduce((a, b) => a < b ? a : b);
        }
      }

      int distance = dp[word1.length][word2.length];
      int maxLength = word1.length > word2.length ? word1.length : word2.length;
      return 1.0 - (distance / maxLength);
    }

    bool isExactMatch(String word1, String word2) {
      if (isFunctionWord(word1) && isFunctionWord(word2)) {
        return true;
      }

      return word1 == word2 ||
          isHomophone(word1, word2) ||
          isMetaphoneMatch(word1, word2);
    }

    bool isSimilarMatch(String word1, String word2) {
      if (isFunctionWord(word1) || isFunctionWord(word2)) {
        return false;
      }

      if (isHomophone(word1, word2) || isMetaphoneMatch(word1, word2)) {
        return false;
      }

      if (word1.length >= 4 && word2.length >= 4) {
        double similarity = wordSimilarity(word1, word2);
        return similarity > 0.75 && similarity < 1.0;
      }
      return false;
    }

    bool wordsMatch(String word1, String word2) {
      if (isFunctionWord(word1) && isFunctionWord(word2)) {
        return true;
      }

      if (isExactMatch(word1, word2)) return true;

      if (word1.length >= 4 && word2.length >= 4) {
        return wordSimilarity(word1, word2) > 0.75;
      }
      return false;
    }

    int findPatternInTarget(List<String> pattern, int startIndex) {
      if (pattern.isEmpty) return -1;

      for (int i = 0; i <= targetWords.length - pattern.length; i++) {
        bool match = true;
        for (int j = 0; j < pattern.length; j++) {
          if (!wordsMatch(pattern[j], targetWords[i + j])) {
            match = false;
            break;
          }
        }

        if (match) {
          return i;
        }
      }
      return -1;
    }

    for (int partialIndex = 0;
    partialIndex < partialWords.length;
    partialIndex++) {
      String partialWord = partialWords[partialIndex];
      bool found = false;

      for (int i = targetIndex;
      i < targetIndex + maxLookahead && i < targetWords.length;
      i++) {
        if (isExactMatch(targetWords[i], partialWord)) {
          matchedIndexes.add(i);
          lastProcessedIndex = i;
          targetIndex = i + 1;
          found = true;
          errorBuffer.clear();
          break;
        } else if (isSimilarMatch(targetWords[i], partialWord)) {
          mispronounceIndexes.add(i);
          lastProcessedIndex = i;
          targetIndex = i + 1;
          found = true;
          errorBuffer.clear();
          break;
        }
      }

      if (!found) {
        errorBuffer.add(partialWord);

        if (errorBuffer.length >= consecutiveErrorThreshold) {
          int newIndex = findPatternInTarget(errorBuffer, 0);

          if (newIndex >= 0) {
            int oldTargetIndex = targetIndex;
            int skipCount = newIndex - oldTargetIndex;

            if (skipCount <= maxSkipLimit) {
              for (int j = 0; j < errorBuffer.length; j++) {
                if (isExactMatch(errorBuffer[j], targetWords[newIndex + j])) {
                  matchedIndexes.add(newIndex + j);
                } else if (isSimilarMatch(
                    errorBuffer[j], targetWords[newIndex + j])) {
                  mispronounceIndexes.add(newIndex + j);
                } else {
                  matchedIndexes.add(newIndex + j);
                }
              }

              if (skipCount > 0) {
                for (int i = oldTargetIndex; i < newIndex; i++) {
                  skippedIndexes.add(i);
                }
              }

              lastProcessedIndex = newIndex + errorBuffer.length - 1;
              targetIndex = newIndex + errorBuffer.length;
              errorBuffer.clear();
            } else {
              while (errorBuffer.length > consecutiveErrorThreshold - 1) {
                errorBuffer.removeAt(0);
              }
            }
          } else {
            while (errorBuffer.length > consecutiveErrorThreshold - 1) {
              errorBuffer.removeAt(0);
            }
          }
        }
      }
    }

    void autoHighlightFunctionalWords() {
      List<int> allMatchedIndexes = [...matchedIndexes, ...mispronounceIndexes];
      allMatchedIndexes.sort();

      for (int i = 0; i < allMatchedIndexes.length - 1; i++) {
        int currentIndex = allMatchedIndexes[i];
        int nextIndex = allMatchedIndexes[i + 1];

        for (int j = currentIndex + 1; j < nextIndex; j++) {
          if (isFunctionWord(targetWords[j]) &&
              !matchedIndexes.contains(j) &&
              !mispronounceIndexes.contains(j)) {
            matchedIndexes.add(j);
            skippedIndexes.remove(j);
          }
        }
      }

      if (allMatchedIndexes.isNotEmpty) {
        int firstMatchedIndex = allMatchedIndexes.first;
        for (int i = 0; i < firstMatchedIndex; i++) {
          if (isFunctionWord(targetWords[i]) &&
              !matchedIndexes.contains(i) &&
              !mispronounceIndexes.contains(i)) {
            if (firstMatchedIndex - i <= 2) {
              matchedIndexes.add(i);
              skippedIndexes.remove(i);
            }
          }
        }
      }
    }

    autoHighlightFunctionalWords();

    for (int index = 0; index < originalWords.length; index++) {
      if (matchedIndexes.contains(index)) {
        // Correctly pronounced
        correctWordsList.add(index);
        log('Correct word at index $index: "${originalWords[index]}"');
      } else if (mispronounceIndexes.contains(index)) {
        // Incorrectly pronounced
        errorWordsPronunciationList.add(index);
        log('Mispronounced word at index $index: "${originalWords[index]}" -> "${targetWords[index]}"');
      } else if (skippedIndexes.contains(index)) {
        // Skipped/not attempted
        errorWordsIndexList.add(index);
        log('Skipped word at index $index: "${originalWords[index]}"');
      } else {
        // Unaccounted for - this shouldn't happen if your logic is complete
        errorWordsIndexList.add(index);
        // log('Unaccounted word at index $index: "${originalWords[index]}"');
      }
    }

    int latestIndex = -1;

    for (int index in matchedIndexes) {
      if (index > latestIndex) latestIndex = index;
    }
    for (int index in mispronounceIndexes) {
      if (index > latestIndex) latestIndex = index;
    }
    for (int index in skippedIndexes) {
      if (index > latestIndex) latestIndex = index;
    }

    if (lastProcessedIndex > latestIndex) {
      latestIndex = lastProcessedIndex;
    }

    int indexedSentenceCount = 0;
    if (latestIndex >= 0 && latestIndex < sentenceCountLookup.length) {
      indexedSentenceCount = sentenceCountLookup[latestIndex];
    }

    errorWordsIndexes = errorWordsIndexList;
    errorPronouncationList = errorWordsPronunciationList;
    correctPronouncationList = correctWordsList;

    callback(
        errorWordsIndexesLength: errorWordsIndexList.length,
        errorPronouncationListLength: errorWordsPronunciationList.length,
        correctPronouncationListLength: correctWordsList.length,
        indexedSentenceCount: indexedSentenceCount);
    ScrollController secondcontroller = ScrollController();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (controller.hasClients && isAutoScroll) {
        int currentSentence =
        _getSentenceFromWordIndex(latestIndex, originalWords);
        double lineHeight = fontSize * lineSpace;
        double advanceOffset = lineHeight * 1.0; // 4 lines in advance

        // Estimate position based on sentence rather than individual words
        double estimatedPosition = _estimateSentencePosition(
          sentenceIndex: currentSentence,
          fontSize: fontSize,
          lineSpace: lineSpace,
        );

        double advancedPosition = estimatedPosition + advanceOffset;

        double viewportHeight = controller.position.viewportDimension;
        // Position the advanced content in the upper portion of the screen
        double targetPosition = advancedPosition - (viewportHeight * 0.3);

        double maxScroll = controller.position.maxScrollExtent;
        targetPosition = targetPosition.clamp(0.0, maxScroll);

        double currentScroll = controller.offset;

        double currentScreenPosition = estimatedPosition - currentScroll;

        bool shouldScroll = currentScreenPosition < viewportHeight * 0.5;

        print('shouldScroll: $shouldScroll');
        if (shouldScroll) {
          _scrollToCurrentPosition(
            controller: controller,
            currentWordIndex: latestIndex,
            words: originalWords,
            fontSize: fontSize,
            lineSpace: lineSpace,
            scrollSpeedPerSecond: 20,
            // autoScrollSpeed: autoScrollSpeed,
          );
        } else {
          isAutoScroll = false;
        }
      }
    });

    return SingleChildScrollView(
      controller: controller,
      child: Padding(
        padding: EdgeInsets.only(top: endOfScreen),
        child: RichText(
          text: TextSpan(
            children: List.generate(originalWords.length, (index) {
              String word = originalWords[index];
              Color wordColor;
              Color borderColor;
              Color backgroundColor = Color(0xFFFFFFFF);
              FontWeight weight = FontWeight.normal;

              if (matchedIndexes.contains(index)) {
                wordColor = highlightCorrectColor;
                backgroundColor = highlightCorrectColor;
                borderColor = highlightCorrectColor;
              } else if (mispronounceIndexes.contains(index)) {
                wordColor = highlightCorrectColor;
                borderColor = highlightCorrectColor;
                backgroundColor = highlightCorrectColor;
                weight = FontWeight.normal;
              } else if (skippedIndexes.contains(index)) {
                wordColor = highlightCorrectColor;
                backgroundColor = highlightCorrectColor;
                borderColor = Colors.blue;
                weight = FontWeight.normal;
              } else if (index < targetIndex) {
                wordColor = highlightWrongColor;
                borderColor = highlightWrongColor;
                backgroundColor = highlightWrongColor;
                weight = FontWeight.normal;
              } else {
                wordColor = defaultTextColor;
                borderColor = Color(0xFFFFFFFF);
              }

              return WidgetSpan(
                child: Container(
                  margin: EdgeInsets.symmetric(vertical: 2),
                  padding: EdgeInsets.symmetric(horizontal: 2),
                  decoration: BoxDecoration(
                    // color: backgroundColor.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    word,
                    style: TextStyle(
                      fontSize: fontSize,
                      height: lineSpace,
                      fontWeight: weight,
                      color: wordColor,
                    ),
                  ),
                ),
              );
            }),
          ),
        ),
      ),
    );
  }

  void _scrollToCurrentPosition({
    required ScrollController controller,
    required int currentWordIndex,
    required List<String> words,
    required double fontSize,
    required double lineSpace,
    required double scrollSpeedPerSecond, // pixels per second
  }) {
    if (!controller.hasClients || currentWordIndex < 0) return;

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!controller.hasClients) return;

      int currentSentence = _getSentenceFromWordIndex(currentWordIndex, words);

      double estimatedPosition = _estimateSentencePosition(
        sentenceIndex: currentSentence,
        fontSize: fontSize,
        lineSpace: lineSpace,
      );

      double maxScroll = controller.position.maxScrollExtent;
      estimatedPosition = estimatedPosition.clamp(0.0, maxScroll);
      if (estimatedPosition <= 100) {
        estimatedPosition = 100;
      }

      // Cancel any running scroll animation for smoother control
      controller.jumpTo(controller.offset);

      // Calculate distance
      double distance = estimatedPosition - controller.offset;

      // Determine time from speed
      int durationMs = (distance.abs() / scrollSpeedPerSecond * 1000).round();

      if (durationMs > 0) {
        controller.animateTo(
          estimatedPosition,
          duration: Duration(milliseconds: durationMs),
          curve: Curves.linear, // constant speed
        );
      }
    });
  }

// Helper method to determine which sentence a word index belongs to
  int _getSentenceFromWordIndex(int wordIndex, List<String> words) {
    if (wordIndex < 0 || wordIndex >= words.length) return 0;

    int sentenceCount = 0;
    int currentWordCount = 0;

    String fullText = words.join(' ');
    List<String> sentences = fullText.split(RegExp(r'[.!?]+\s*'));

    for (String sentence in sentences) {
      List<String> sentenceWords = sentence.trim().split(RegExp(r'\s+'));
      if (sentence.trim().isEmpty) continue;

      if (wordIndex < currentWordCount + sentenceWords.length) {
        return sentenceCount;
      }

      currentWordCount += sentenceWords.length;
      sentenceCount++;
    }

    return sentenceCount;
  }

// Helper method to get the actual sentence text
  String _getSentenceText(int sentenceIndex, List<String> words) {
    if (sentenceIndex < 0) return "";

    String fullText = words.join(' ');
    List<String> sentences = fullText.split(RegExp(r'[.!?]+\s*'));

    if (sentenceIndex < sentences.length) {
      return sentences[sentenceIndex].trim();
    }

    return "";
  }

// Estimate position based on sentence index
  double _estimateSentencePosition({
    required int sentenceIndex,
    required double fontSize,
    required double lineSpace,
  }) {
    if (sentenceIndex < 0) return 0.0;

    // Assume average 3-4 lines per sentence (more realistic for reading passages)
    double linesPerSentence = 3;
    double lineHeight = fontSize * lineSpace;

    return sentenceIndex * linesPerSentence * lineHeight;
  }

  Widget displayMistakeWords({
    required List<int> errorWordsList,
    required List<int> errorPronunciationList,
    required List<int> correctPronouncationList,
    required String randomText,
    required int totalWords,
    required Color defaultTextColor,
    required Color highlightWrongColor,
    required double fontSize,
    required double lineSpace,
  }) {
    int correctWords = correctPronouncationList.length;
    int mispronounced = errorPronunciationList.length;
    int skippedWords = errorWordsList.length;

    int totalSpokenWords = correctWords + mispronounced;
    int totalProcessedWords = correctWords + mispronounced + skippedWords;

    // Scores
    int pronunciationScore = correctWords; // out of totalSpokenWords
    int fluencyScore = correctWords; // out of totalWords

    // Accuracy: correct words out of total words
    double accuracyPercentageDouble = (correctWords / totalWords) * 100;
    int accuracyPercentage = accuracyPercentageDouble.toInt();
    AccuracyStore().accuracyPercentage = accuracyPercentage;

    // Split the text into words
    final List<String> words = randomText.split(' ');

    // Function to truncate error words
    String _processWord(String word, bool isError) {
      // if (isError && word.length > 3) {
      //   int midPoint = word.length ~/ 2;
      //   return '${word.substring(0, midPoint)}•••${word.substring(word.length - midPoint)}';
      // }
      return word;
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Top section with progress indicators
        Padding(
          padding: const EdgeInsets.only(bottom: 16.0),
          child: Container(
            padding: const EdgeInsets.all(16.0),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
              boxShadow: [
                BoxShadow(
                  color: Colors.grey.withOpacity(0.2),
                  spreadRadius: 1,
                  blurRadius: 6,
                  offset: const Offset(0, 2),
                ),
              ],
            ),
            child: Row(
              children: [
                // Circular progress indicator
                SizedBox(
                  width: 80, //progress bar circular height
                  height: 80,
                  child: Stack(
                    alignment: Alignment.center,
                    children: [
                      SizedBox(
                        width: 120,
                        height: 120,
                        child: CircularProgressIndicator(
                          value: accuracyPercentage / 100, // 60%
                          strokeWidth: 10,
                          backgroundColor: Colors.grey.shade200,
                          valueColor: AlwaysStoppedAnimation<Color>(
                            Colors.blue,
                          ),
                        ),
                      ),
                      Text(
                        "${accuracyPercentage}%",
                        style: TextStyle(
                          fontSize: 18, // Increased font size
                          fontWeight: FontWeight.bold,
                          color: Colors.blue,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 24),
                // Metrics
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _buildMetricRow("Pronunciation", pronunciationScore,
                          totalWords, Colors.green),
                      const SizedBox(height: 8),
                      // _buildMetricRow("Fluency", fluencyScore,  totalSpokenWords, Colors.blue),
                      // const SizedBox(height: 8),
                      _buildMetricRow(
                          "Mistakes",
                          (totalWords - pronunciationScore),
                          totalWords,
                          Colors.red),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),

        // Text content
        Flexible(
          child: Container(
            padding: const EdgeInsets.all(16.0),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
              boxShadow: [
                BoxShadow(
                  color: Colors.grey.withOpacity(0.2),
                  spreadRadius: 1,
                  blurRadius: 6,
                  offset: const Offset(0, 2),
                ),
              ],
            ),
            child: SingleChildScrollView(
              child: RichText(
                textAlign: TextAlign.left,
                text: TextSpan(
                  style: TextStyle(
                    fontSize: fontSize,
                    color: defaultTextColor,
                    height: lineSpace,
                  ),
                  children: List.generate(words.length, (index) {
                    bool isError = errorWordsList.contains(index);
                    return TextSpan(
                      text: _processWord(words[index], isError) +
                          (index < words.length - 1 ? ' ' : ''),
                      style: TextStyle(
                        color: isError ? highlightWrongColor : defaultTextColor,
                        fontWeight:
                        isError ? FontWeight.bold : FontWeight.normal,
                        decoration: isError
                            ? TextDecoration.lineThrough
                            : TextDecoration.none,
                      ),
                    );
                  }),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }

// Helper widget for the metrics
  Widget _buildMetricRow(String label, int value, int totalWords, Color color) {
    return Row(
      children: [
        Text(
          "$label: ",
          style: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w500,
            color: Colors.black,
          ),
        ),
        const SizedBox(width: 8),
        Text(
          "${value}/$totalWords",
          style: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.bold,
            color: color,
          ),
        ),
      ],
    );
  }

  void startAutoScroll(ScrollController controller, int autoScrollSpeed) {
    if (!controller.hasClients) return;

    if (autoScrollSpeed == 0) {
      final double currentOffset = controller.offset;
      controller.jumpTo(currentOffset);
      return;
    }
    final double maxScroll = controller.position.maxScrollExtent;
    final double currentOffset = controller.offset;

    // Check if already at the end
    if (currentOffset >= maxScroll) return;

    // Calculate remaining distance and animation duration
    final double distance = maxScroll - currentOffset;
    final int durationMs = (distance * autoScrollSpeed / 10).ceil();
    final Duration duration = Duration(milliseconds: durationMs);

    // Start smooth scroll animation
    controller.animateTo(
      maxScroll,
      duration: duration,
      curve: Curves.linear,
    );
  }

  /// Start recognition
  static Future<dynamic> recognize({
    required PhoneticType type,
    String? languageCode,
    required int timeout,
    void Function()? callback,
    int? timeoutPerSentence,
    String? sentence,
    bool sendKeyOnly = true,
  }) async {
    if (timeout < 0) {
      throw ArgumentError('Timeout must be a positive value');
    }

    try {
      final dynamic raw = await _channel.invokeMethod('recognize', {
        'type': type.toString().split('.').last,
        'languageCode': languageCode,
        'timeout': timeout,
        'sentence': sentence,
        'sendKeyOnly': sendKeyOnly,
      });

      if (raw == null) {
        debugPrint("RESULT LIBS: null response from native side");
        return "";
      }

      debugPrint("RESULT LIBS: Raw native response: $raw");
      return raw;
    } on PlatformException catch (e) {
      debugPrint("RESULT LIBS: Platform Exception - ${e.code}: ${e.message}");
      if (kDebugMode) {
        log("Speech Recognition Error: ${e.code} - ${e.message}");
      }
      return "";
    } catch (e) {
      debugPrint("RESULT LIBS: Unexpected error: $e");
      return "";
    }
  }

  /// Checks if the recognized sentence contains mandatory words.
  ///
  /// If [andCase] is true, all words in [mandatoryWordsList] must be present
  /// in the [recognizedSentence] in order. If [andCase] is false, at least one
  /// word from [mandatoryWordsList] must be present in the [recognizedSentence].
  ///
  /// If [andCase] is false, atleast one word in [mandatoryWordsList] must be present
  bool mandatoryWords(
      {required List<String> mandatoryWordsList,
        required String recognizedSentence,
        bool andCase = true}) {
    final lowerCaseSentence = recognizedSentence.toLowerCase();
    final lowerCaseMandatoryWords =
    mandatoryWordsList.map((word) => word.toLowerCase()).toList();

    if (andCase) {
      // AND case: All words must be present in order
      int lastIndex = -1;

      for (String word in lowerCaseMandatoryWords) {
        int currentIndex = lowerCaseSentence.indexOf(word);

        if (currentIndex == -1 || currentIndex < lastIndex) {
          return false; // word not found or order is incorrect
        }

        lastIndex = currentIndex;
      }

      return true;
    } else {
      // OR case: At least one word from mandatory list must be present
      for (String word in lowerCaseMandatoryWords) {
        if (lowerCaseSentence.contains(word)) {
          return true; // Found at least one mandatory word
        }
      }

      return false; // No mandatory words found
    }
  }

  static bool _arePhoneticallySimilar(String a, String b) {
    final Map<String, String> similarMap = {
      'j': 'g',
      'g': 'j',
      't': 'd',
      'd': 't',
      'm': 'n',
      'n': 'm',
      'b': 'p',
      'p': 'b',
    };

    // Normalize to lowercase
    a = a.trim().toLowerCase();
    b = b.trim().toLowerCase();

    if (a == b) return true;
    if (a.length == 1 && b.length == 1 && similarMap[a] == b) return true;

    return false;
  }
}

class AccuracyStore {
  static final AccuracyStore _instance = AccuracyStore._internal();
  factory AccuracyStore() => _instance;
  AccuracyStore._internal();

  int accuracyPercentage = 0;
}
