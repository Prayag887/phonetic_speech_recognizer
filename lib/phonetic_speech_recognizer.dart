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
    'a', 'an',"i"
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

    //dont search for words that are farther than 2 words away
    final int maxLookahead = 2;
    //dont let the users skip more than 2 words ahead
    final int maxSkipLimit = 2;
    //populated after checking double metaphones and  homophones
    final Set<int> matchedIndexes = {};
    final Set<int> skippedIndexes = {};
    final Set<int> mispronounceIndexes = {};
    final List<String> errorBuffer = [];
    //if more tham 2 words are consecutively wrong, then skip
    final int consecutiveErrorThreshold = 2;


    //to check the current targeted words
    int targetIndex = 0;
    //to check the last spoken word at the end of the partial text
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

    /// Finds the starting index of the first occurrence of the given pattern
    /// in the target words list, starting from the specified index.
    ///
    /// This function compares each word in the pattern with corresponding words
    /// in the target words list using the `wordsMatch` method. A match is
    /// considered successful if all words in the pattern match with a contiguous
    /// sequence of words in the target words list.
    ///
    /// Returns the starting index of the match if found, or -1 if the pattern
    /// is not found in the target words list.
    ///
    /// - Parameters:
    ///   - pattern: A list of words to find in the target words list.
    ///   - startIndex: The index in the target words list to start the search from.
    /// - Returns: The starting index of the first matching occurrence, or -1 if no match is found.

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


    /// Iterate through the partial words and try to find a match in the target words list.
    /// If a match is found, add the index to the matchedIndexes list and move the targetIndex forward.
    /// If a similar match is found, add the index to the mispronounceIndexes list and move the targetIndex forward.
    /// If no match is found, add the partial word to the error buffer and check if the buffer has reached the
    /// consecutive error threshold. If it has, try to find a match of the entire buffer in the target words list.
    /// If a match is found, add the indexes to the matchedIndexes list and move the targetIndex forward.
    // If no match is found, remove the oldest word from the buffer until the buffer is no longer at the threshold.
    for (int partialIndex = 0; partialIndex < partialWords.length; partialIndex++) {
      String partialWord = partialWords[partialIndex];
      bool found = false;

      for (int i = targetIndex; i < targetIndex + maxLookahead && i < targetWords.length; i++) {
        if (isExactMatch(targetWords[i], partialWord)) {
          matchedIndexes.add(i);
          lastProcessedIndex = i;
          targetIndex = i + 1;
          found = true;
          errorBuffer.clear();
          break;
        }
        else if (isSimilarMatch(targetWords[i], partialWord)) {
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
                } else if (isSimilarMatch(errorBuffer[j], targetWords[newIndex + j])) {
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


    /// NEW: Auto-highlight skipped functional words between matched words
    /// Automatically highlight functional words between matched words.
    ///
    /// This function goes through all matched words and checks if there are any
    /// functional words between them. If there are, and they are not already
    /// matched or mispronounced, they are added to the matched list and removed
    /// from the skipped list. This is done to highlight functional words that are
    /// close to the correct words, even if they are not part of the correct phrase.
    ///
    /// This function also checks for functional words at the beginning of the
    /// target phrase, if there are any matches. If there are, and they are close
    /// enough to the first matched word, they are also highlighted.
    void autoHighlightFunctionalWords() {
      List<int> allMatchedIndexes = [...matchedIndexes, ...mispronounceIndexes];
      allMatchedIndexes.sort();

      for (int i = 0; i < allMatchedIndexes.length - 1; i++) {
        int currentIndex = allMatchedIndexes[i];
        int nextIndex = allMatchedIndexes[i + 1];

        // Check all words between current and next matched word
        for (int j = currentIndex + 1; j < nextIndex; j++) {
          if (isFunctionWord(targetWords[j]) && !matchedIndexes.contains(j) && !mispronounceIndexes.contains(j)) {
            matchedIndexes.add(j);
            // Remove from skipped if it was there
            skippedIndexes.remove(j);
          }
        }
      }

      // Also check for functional words at the beginning if we have matches
      if (allMatchedIndexes.isNotEmpty) {
        int firstMatchedIndex = allMatchedIndexes.first;
        for (int i = 0; i < firstMatchedIndex; i++) {
          if (isFunctionWord(targetWords[i]) && !matchedIndexes.contains(i) && !mispronounceIndexes.contains(i)) {
            // Only highlight if it's very close to the first matched word (within 2 positions)
            if (firstMatchedIndex - i <= 2) {
              matchedIndexes.add(i);
              skippedIndexes.remove(i);
            }
          }
        }
      }
    }

    // Call the new function to auto-highlight functional words
    autoHighlightFunctionalWords();

    // Process all words for final categorization
    for (int index = 0; index < originalWords.length; index++) {
      if (matchedIndexes.contains(index)) {
        correctWordsList.add(index);
        log('Correct word at index $index: "${originalWords[index]}" -> "${targetWords[index]}"');
      } else if (mispronounceIndexes.contains(index)) {
        errorWordsPronunciationList.add(index);
        log('Mispronounced word at index $index: "${originalWords[index]}" -> "${targetWords[index]}"');
      } else if (skippedIndexes.contains(index)) {
        errorWordsIndexList.add(index);
        errorWordsPronunciationList.add(index);
        log('Skipped word at index $index: "${originalWords[index]}" -> "${targetWords[index]}"');
      }
    }

    log('SUMMARY:');
    log('Original words: $originalWords');
    log('Target words: $targetWords');
    log('Matched indexes: $matchedIndexes');
    log('Mispronounce indexes: $mispronounceIndexes');
    log('Skipped indexes: $skippedIndexes');
    log('Last processed index: $lastProcessedIndex');
    log('Correct words list: $correctWordsList');
    log('Error pronunciation list: $errorWordsPronunciationList');
    log('Error words index list: $errorWordsIndexList');

    // Set global variables
    errorWordsIndexes = errorWordsIndexList;
    errorPronouncationList = errorWordsPronunciationList;
    correctPronouncationList = correctWordsList;

    callback(
        errorWordsIndexesLength: errorWordsIndexList.length,
        errorPronouncationListLength: errorWordsPronunciationList.length,
        correctPronouncationListLength: correctWordsList.length
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
              Color borderColor;
              Color backgroundColor = Color(0xFFFFFFFF);
              FontWeight weight = FontWeight.normal;

              if (matchedIndexes.contains(index)) {
                wordColor = highlightCorrectColor;
                borderColor = highlightCorrectColor;
              } else if (mispronounceIndexes.contains(index)) {
                wordColor = highlightCorrectColor;
                borderColor = highlightCorrectColor;
                weight = FontWeight.normal;
              } else if (skippedIndexes.contains(index)) {
                wordColor = highlightWrongColor;
                borderColor = Colors.blue;
                weight = FontWeight.normal;
              } else if (index < targetIndex) {
                wordColor = highlightWrongColor;
                borderColor = highlightWrongColor;
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