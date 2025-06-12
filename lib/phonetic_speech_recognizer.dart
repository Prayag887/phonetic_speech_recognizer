import 'dart:async';
import 'dart:developer';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'double_metaphone.dart';
import 'homophones.dart';

enum PhoneticType { alphabet, koreanAlphabet, number, englishWordsOrSentence, japaneseAlphabet, koreanNumber, allLanguageSupport, paragraphsMapping }

class PhoneticSpeechRecognizer {
  final Map<String, List<String>> homophones = Homophones.homophones;


  // Function words that should always be highlighted as correct
  static const Set<String> functionWords = {
    'a', 'an', 'the', "i", "was", "were", "old"
  };

  List<int> errorWordsIndexes = [];
  List<int> errorPronouncationList= [];
  List<int> correctPronouncationList= [];

  static const MethodChannel _channel = MethodChannel('phonetic_speech_recognizer');

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

  static Future<bool> stopRecognition() async {
    await Future.delayed(Duration(seconds: 1));
    try {
      final bool result = await _channel.invokeMethod('stopRecognition');
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
    final EventChannel _eventChannel = EventChannel('phonetic_speech_recognizer/partial_results');
    return _eventChannel.receiveBroadcastStream();
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
    required void Function({int? correctPronouncationListLength, int? errorPronouncationListLength, int? errorWordsIndexesLength}) callback,
  }) {
    String cleanText(String text) {
      return text.replaceAll(RegExp(r'[^\w\s]'), '').toLowerCase().trim();
    }

    List<String> originalWords = randomText.split(RegExp(r'\s+'));
    List<String> targetWords = originalWords.map(cleanText).toList();
    List<String> partialWords = partialText.split(RegExp(r'\s+')).map(cleanText).toList();

    // Configuration constants
    const int maxLookahead = 2;
    const int maxSkipLimit = 2;
    const int consecutiveErrorThreshold = 2;
    const double similarityThreshold = 0.75;

    // Tracking sets and lists
    final Set<int> matchedIndexes = {};
    final Set<int> skippedIndexes = {};
    final Set<int> mispronounceIndexes = {};
    final List<String> errorBuffer = [];

    // Progress tracking
    int targetIndex = 0;
    int lastProcessedIndex = -1;
    int actuallyProcessedWords = 0;

    // Result lists
    List<int> errorWordsIndexList = [];
    List<int> errorWordsPronunciationList = [];
    List<int> correctWordsList = [];

    // Optimized word matching functions
    bool isHomophone(String word1, String word2) {
      if (word1 == word2) return true;
      return homophones.containsKey(word1) && homophones[word1]!.contains(word2);
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
        word1.length + 1, (_) => List.filled(word2.length + 1, 0),
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
      // Function words are always considered exact matches with each other
      if (isFunctionWord(word1) && isFunctionWord(word2)) {
        return true;
      }

      return word1 == word2 ||
          isHomophone(word1, word2) ||
          isMetaphoneMatch(word1, word2);
    }

    bool isSimilarMatch(String word1, String word2) {
      // Don't apply similarity matching to function words
      if (isFunctionWord(word1) || isFunctionWord(word2)) {
        return false;
      }

      // Don't consider already exact matches as similar
      if (isHomophone(word1, word2) || isMetaphoneMatch(word1, word2)) {
        return false;
      }

      // Only check similarity for words of sufficient length
      if (word1.length >= 4 && word2.length >= 4) {
        double similarity = wordSimilarity(word1, word2);
        return similarity > similarityThreshold && similarity < 1.0;
      }
      return false;
    }

    bool wordsMatch(String word1, String word2) {
      if (isExactMatch(word1, word2)) return true;
      if (word1.length >= 4 && word2.length >= 4) {
        return wordSimilarity(word1, word2) > similarityThreshold;
      }
      return false;
    }

    int findPatternInTarget(List<String> pattern, int startIndex) {
      if (pattern.isEmpty) return -1;

      for (int i = startIndex; i <= targetWords.length - pattern.length; i++) {
        bool match = true;
        for (int j = 0; j < pattern.length; j++) {
          if (!wordsMatch(pattern[j], targetWords[i + j])) {
            match = false;
            break;
          }
        }
        if (match) return i;
      }
      return -1;
    }

    void processErrorBuffer() {
      if (errorBuffer.length < consecutiveErrorThreshold) return;

      int newIndex = findPatternInTarget(errorBuffer, targetIndex);

      if (newIndex >= 0) {
        int skipCount = newIndex - targetIndex;

        if (skipCount <= maxSkipLimit) {
          // Mark skipped words
          for (int i = targetIndex; i < newIndex; i++) {
            skippedIndexes.add(i);
          }

          // Process error buffer words
          for (int j = 0; j < errorBuffer.length; j++) {
            int targetIdx = newIndex + j;
            if (isExactMatch(errorBuffer[j], targetWords[targetIdx])) {
              matchedIndexes.add(targetIdx);
            } else if (isSimilarMatch(errorBuffer[j], targetWords[targetIdx])) {
              mispronounceIndexes.add(targetIdx);
            } else {
              matchedIndexes.add(targetIdx); // Force match for pattern
            }
          }

          lastProcessedIndex = newIndex + errorBuffer.length - 1;
          targetIndex = newIndex + errorBuffer.length;
          actuallyProcessedWords += errorBuffer.length;
          errorBuffer.clear();
        } else {
          // Remove oldest word if skip limit exceeded
          errorBuffer.removeAt(0);
        }
      } else {
        // No pattern found, remove oldest word
        errorBuffer.removeAt(0);
      }
    }

    void autoHighlightFunctionalWords() {
      if (lastProcessedIndex < 0) return;

      List<int> allMatchedIndexes = [...matchedIndexes, ...mispronounceIndexes];
      allMatchedIndexes.sort();

      // Only highlight functional words between matched words and within processed range
      for (int i = 0; i < allMatchedIndexes.length - 1; i++) {
        int currentIndex = allMatchedIndexes[i];
        int nextIndex = allMatchedIndexes[i + 1];

        // Don't go beyond what was actually processed
        if (currentIndex > lastProcessedIndex) break;
        int endCheck = nextIndex > lastProcessedIndex ? lastProcessedIndex + 1 : nextIndex;

        for (int j = currentIndex + 1; j < endCheck; j++) {
          if (isFunctionWord(targetWords[j]) &&
              !matchedIndexes.contains(j) &&
              !mispronounceIndexes.contains(j)) {
            matchedIndexes.add(j);
            skippedIndexes.remove(j);
          }
        }
      }

      // Highlight functional words at the beginning if close to first match
      if (allMatchedIndexes.isNotEmpty) {
        int firstMatchedIndex = allMatchedIndexes.first;
        for (int i = 0; i < firstMatchedIndex && i <= lastProcessedIndex; i++) {
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

    // Main processing loop - only process actual partial words
    for (int partialIndex = 0; partialIndex < partialWords.length; partialIndex++) {
      String partialWord = partialWords[partialIndex];
      bool found = false;

      // Look ahead within the target words
      for (int i = targetIndex; i < targetIndex + maxLookahead && i < targetWords.length; i++) {
        if (isExactMatch(targetWords[i], partialWord)) {
          matchedIndexes.add(i);
          lastProcessedIndex = i;
          targetIndex = i + 1;
          actuallyProcessedWords++;
          found = true;
          errorBuffer.clear();
          break;
        } else if (isSimilarMatch(targetWords[i], partialWord)) {
          mispronounceIndexes.add(i);
          lastProcessedIndex = i;
          targetIndex = i + 1;
          actuallyProcessedWords++;
          found = true;
          errorBuffer.clear();
          break;
        }
      }

      if (!found) {
        errorBuffer.add(partialWord);
        processErrorBuffer();
      }
    }

    // Auto-highlight functional words within processed range
    autoHighlightFunctionalWords();

    // Final categorization - only process words up to what was actually spoken
    int maxProcessedIndex = lastProcessedIndex >= 0 ? lastProcessedIndex : -1;

    for (int index = 0; index < originalWords.length; index++) {
      if (matchedIndexes.contains(index)) {
        correctWordsList.add(index);
      } else if (mispronounceIndexes.contains(index)) {
        errorWordsPronunciationList.add(index);
      } else if (skippedIndexes.contains(index)) {
        errorWordsIndexList.add(index);
        errorWordsPronunciationList.add(index);
      }
      // Don't categorize words beyond what was processed
    }

    // Logging for debugging
    log('SUMMARY:');
    log('Partial words (${partialWords.length}): $partialWords');
    log('Target words: $targetWords');
    log('Actually processed words: $actuallyProcessedWords');
    log('Last processed index: $lastProcessedIndex');
    log('Matched indexes: $matchedIndexes');
    log('Mispronounce indexes: $mispronounceIndexes');
    log('Skipped indexes: $skippedIndexes');
    log('Correct words list: $correctWordsList');
    log('Error pronunciation list: $errorWordsPronunciationList');
    log('Error words index list: $errorWordsIndexList');

    // Set global variables
    errorWordsIndexes = errorWordsIndexList;
    errorPronouncationList = errorWordsPronunciationList;
    correctPronouncationList = correctWordsList;

    // Callback with results
    callback(
      errorWordsIndexesLength: errorWordsIndexList.length,
      errorPronouncationListLength: errorWordsPronunciationList.length,
      correctPronouncationListLength: correctWordsList.length,
    );

    ScrollController controller = ScrollController();

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (controller.hasClients) {
        if (isAutoScroll && autoScrollSpeed > 0) {
          startAutoScroll(controller, autoScrollSpeed);
        } else {
          controller.jumpTo(controller.offset);
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
              FontWeight weight = FontWeight.normal;

              if (matchedIndexes.contains(index)) {
                wordColor = highlightCorrectColor;
                // borderColor = highlightCorrectColor;
              } else if (mispronounceIndexes.contains(index)) {
                wordColor = highlightCorrectColor;
                // borderColor = highlightCorrectColor;
                weight = FontWeight.normal;
              } else if (skippedIndexes.contains(index)) {
                wordColor = highlightWrongColor;
                // borderColor = Colors.blue;
                weight = FontWeight.normal;
              } else if (index < targetIndex) {
                wordColor = highlightWrongColor;
                // borderColor = highlightWrongColor;
                weight = FontWeight.normal;
              } else {
                wordColor = defaultTextColor;
                // borderColor = Color(0xFFFFFFFF);
              }

              return WidgetSpan(
                child: Container(
                  margin: EdgeInsets.symmetric(vertical: 2),
                  padding: EdgeInsets.symmetric(horizontal: 2),
                  decoration: BoxDecoration(
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

  Widget displayMistakeWords({required List<int> errorWordsList, required List<int> errorPronunciationList, required List<int> correctPronouncationList, required String randomText, required int totalWords, required Color defaultTextColor, required Color highlightWrongColor, required double fontSize, required double lineSpace,}) {

    int totalSpokenWords = correctPronouncationList.length + errorPronouncationList.length;
    int totalSkippedWords = totalWords - totalSpokenWords;

    int pronunciationMistakes = errorPronouncationList.length;
    int fluencyMistakes = errorWordsList.length;

    // int totalErrors = pronunciationMistakes + fluencyMistakes;
    int totalErrors = fluencyMistakes;

    int pronunciationScore = totalSpokenWords - pronunciationMistakes;
    int fluencyScore = totalSpokenWords - fluencyMistakes;

    double accuracyPercentageDouble = (((totalWords - (totalErrors + totalSkippedWords)) / totalWords) * 100);
    int accuracyPercentage = (accuracyPercentageDouble > 0) ? accuracyPercentageDouble.toInt() : 0;
    // (((totalWords - (totalErrors + totalSkippedWords)) / totalWords) * 100).toInt();


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
                          value: accuracyPercentage/100, // 60%
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
                          fontSize: 18,  // Increased font size
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
                      _buildMetricRow("Pronunciation", pronunciationScore, totalSpokenWords, Colors.green),
                      const SizedBox(height: 8),
                      // _buildMetricRow("Fluency", fluencyScore,  totalSpokenWords, Colors.blue),
                      // const SizedBox(height: 8),
                      _buildMetricRow("Mistakes", totalErrors, totalWords, Colors.red),
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
                      text: _processWord(words[index], isError) + (index < words.length - 1 ? ' ' : ''),
                      style: TextStyle(
                        color: isError ? highlightWrongColor : defaultTextColor,
                        fontWeight: isError ? FontWeight.bold : FontWeight.normal,
                        decoration: isError ? TextDecoration.lineThrough : TextDecoration.none,
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

    if (autoScrollSpeed == 0){
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

  static Future<dynamic> recognize({
    required PhoneticType type,
    String? languageCode,
    required int timeout,
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
      });

      if (raw == null) return "";

      // Case 1: raw is a String
      if (raw is String) {
        if (sendKeyOnly) {

          return raw;
        } else {
          return {'text': raw, 'confidence': null};
        }
      }

      // Case 2: raw is a Map
      if (raw is Map) {
        final Map<String, double> result = raw.map(
              (key, value) => MapEntry(key.toString(), (value as num).toDouble()),
        );

        if (result.isEmpty) return "";

        final entry = result.entries.first;
        final String text = entry.key;
        final double confidence = entry.value;

        if (sendKeyOnly) {
          return text;
        } else {
          return {'text': text, 'confidence': confidence};
        }
      }

      return "";
    } on PlatformException catch (e) {
      if (kDebugMode) {
        log("Speech Recognition Error: ${e.code} - ${e.message}");
      }
      return "";
    }
  }

  //this check if the mandatory words are in the recognized sentence or not
  bool mandatoryWords({
    required List<String> mandatoryWordsList,
    required String recognizedSentence,
  }) {
    final lowerCaseSentence = recognizedSentence.toLowerCase();
    final lowerCaseMandatoryWords =
    mandatoryWordsList.map((word) => word.toLowerCase()).toList();

    int lastIndex = -1;

    for (String word in lowerCaseMandatoryWords) {
      int currentIndex = lowerCaseSentence.indexOf(word);

      if (currentIndex == -1 || currentIndex < lastIndex) {
        return false; // word not found or order is incorrect
      }

      lastIndex = currentIndex;
    }

    return true;
  }

}