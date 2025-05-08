import 'dart:async';
import 'dart:ffi';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

enum PhoneticType { alphabet, koreanAlphabet, number, englishWordsOrSentence, japaneseAlphabet, koreanNumber, allLanguageSupport, paragraphsMapping }

class PhoneticSpeechRecognizer {
  // Homophones dictionary - add more as needed
  final Map<String, List<String>> homophones = {
    'accept': ['except'],
    'affect': ['effect'],
    'aisle': ['isle', "I'll"],
    'aloud': ['allowed'],
    'allowed': ['aloud'],
    'ate': ['eight'],
    'bare': ['bear'],
    'bean': ['been'],
    'been': ['bin'],
    'blew': ['blue'],
    'blue': ['blew'],
    'brake': ['break'],
    'break': ['brake'],
    'buy': ['by', 'bye'],
    'by': ['buy', 'bye'],
    'bye': ['buy', 'by'],
    'cell': ['sell'],
    'cent': ['scent', 'sent'],
    'dear': ['deer'],
    'days': ['daze'],
    'deer': ['dear'],
    'die': ['dye'],
    'dye': ['die'],
    'eight': ['ate'],
    'effect': ['affect'],
    'except': ['accept'],
    'fair': ['fare'],
    'fare': ['fair'],
    'fir': ['fur'],
    'flour': ['flower'],
    'flower': ['flour'],
    'for': ['fore', 'four'],
    'fore': ['for', 'four'],
    'four': ['for', 'fore'],
    'fur': ['fir'],
    'hear': ['here'],
    'here': ['hear'],
    'hole': ['whole'],
    'hour': ['our'],
    'idle': ['idol'],
    'idol': ['idle'],
    'I\'ll': ['aisle', 'isle'],
    'isle': ['aisle', 'I\'ll'],
    'knew': ['new'],
    'knows': ['nose'],
    'mail': ['male'],
    'maid': ['made'],
    'male': ['mail'],
    'made': ['maid'],
    'meat': ['meet'],
    'meet': ['meat'],
    'morning': ['mourning'],
    'mourning': ['morning'],
    'new': ['knew'],
    'nose': ['knows'],
    'our': ['hour'],
    'pair': ['pare', 'pear'],
    'pare': ['pair', 'pear'],
    'peace': ['piece'],
    'pear': ['pair', 'pare'],
    'piece': ['peace'],
    'principal': ['principle'],
    'principle': ['principal'],
    'rain': ['rein', 'reign'],
    'recogniser': ['recognizer'],
    'recognizer': ['recogniser'],
    'rein': ['rain', 'reign'],
    'reign': ['rain', 'rein'],
    'right': ['rite', 'write'],
    'rite': ['right', 'write'],
    'roll': ['role'],
    'role': ['roll'],
    'scene': ['seen'],
    'scent': ['cent', 'sent'],
    'sea': ['see'],
    'see': ['sea'],
    'sell': ['cell'],
    'sent': ['cent', 'scent'],
    'spirit': ['speech', 'speed'],
    'speech': ['spirit', 'speed', 'spit'],
    'some': ['sum'],
    'steel': ['steal'],
    'steal': ['steel'],
    'suite': ['sweet'],
    'sum': ['some'],
    'sweet': ['suite'],
    'tail': ['tale'],
    'tale': ['tail'],
    'their': ['there', "they're"],
    'there': ['their', "they're"],
    "they're": ['their', 'there'],
    'threw': ['through', 'thru'],
    'through': ['threw', 'thru'],
    'thru': ['threw', 'through'],
    'to': ['too', 'two'],
    'too': ['to', 'two'],
    'two': ['to', 'too'],
    'wait': ['weight'],
    'way': ['weigh'],
    'wear': ['ware', 'where'],
    'weather': ['whether'],
    'weigh': ['way'],
    'weight': ['wait'],
    'whether': ['weather'],
    'where': ['wear', 'ware'],
    'whole': ['hole'],
    'wood': ['would'],
    'won': ['one'],
    'would': ['wood'],
    'write': ['right', 'rite'],
    'you\'re': ['your'],
    'your': ['you\'re']
  };


  late List<int> errorWordsIndexes;

  static const MethodChannel _channel = MethodChannel('phonetic_speech_recognizer');

  static Future<String?> getPlatformVersion() async {
    try {
      final String? version = await _channel.invokeMethod('getPlatformVersion');
      return version;
    } on PlatformException catch (e) {
      if (kDebugMode) {
        print("Error: ${e.message}");
      }
      return null;
    }
  }

  static Future<bool> stopRecognition() async {
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
      // Ensure the data is returned as a String
      return data != null ? data.toString() : "";
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
  }) {
    String cleanText(String text) {
      return text.replaceAll(RegExp(r'[^\w\s]'), '').toLowerCase().trim();
    }

    List<String> originalWords = randomText.split(RegExp(r'\s+'));
    List<String> targetWords = originalWords.map(cleanText).toList();
    List<String> partialWords = partialText.split(RegExp(r'\s+')).map(cleanText).toList();

    final int maxLookahead = 4;
    final int maxSkipLimit = 4;
    final Set<int> matchedIndexes = {};
    final Set<int> skippedIndexes = {};
    final Set<int> mispronounceIndexes = {};
    final List<String> errorBuffer = [];
    final int consecutiveErrorThreshold = 3;
    List<int> errorWordsIndexList= [];

    bool isHomophone(String word1, String word2) {
      if (word1 == word2) return true;

      if (homophones.containsKey(word1)) {
        return homophones[word1]!.contains(word2);
      }

      return false;
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
      return word1 == word2 || isHomophone(word1, word2);
    }

    bool isSimilarMatch(String word1, String word2) {
      if (isHomophone(word1, word2)) return false;

      if (word1.length >= 4 && word2.length >= 4) {
        double similarity = wordSimilarity(word1, word2);
        return similarity > 0.75 && similarity < 1.0;
      }
      return false;
    }

    bool wordsMatch(String word1, String word2) {
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

    int targetIndex = 0;

    for (int partialIndex = 0; partialIndex < partialWords.length; partialIndex++) {
      String partialWord = partialWords[partialIndex];
      bool found = false;

      for (int i = targetIndex; i < targetIndex + maxLookahead && i < targetWords.length; i++) {
        // Check for exact match or homophone
        if (isExactMatch(targetWords[i], partialWord)) {
          matchedIndexes.add(i);
          targetIndex = i + 1;
          found = true;
          errorBuffer.clear();
          break;
        }
        // Check for similar match (mispronunciation)
        else if (isSimilarMatch(targetWords[i], partialWord)) {
          mispronounceIndexes.add(i);
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
              // Process the matched pattern words
              for (int j = 0; j < errorBuffer.length; j++) {
                // Check if it's exact match or similar
                if (isExactMatch(errorBuffer[j], targetWords[newIndex + j])) {
                  matchedIndexes.add(newIndex + j);
                } else if (isSimilarMatch(errorBuffer[j], targetWords[newIndex + j])) {
                  mispronounceIndexes.add(newIndex + j);
                } else {
                  // This shouldn't happen with wordsMatch, but just in case
                  matchedIndexes.add(newIndex + j);
                }
              }

              if (skipCount > 0) {
                for (int i = oldTargetIndex; i < newIndex; i++) {
                  skippedIndexes.add(i);
                }
              }

              targetIndex = newIndex + errorBuffer.length;
              errorBuffer.clear();
            } else {
              // Skip not allowed, keep sliding window
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

    ScrollController controller = ScrollController();

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (controller.hasClients) {
        if (isAutoScroll && autoScrollSpeed > 0) {
          startAutoScroll(controller, autoScrollSpeed);
        } else {
          // Explicitly stop any ongoing scroll
          controller.jumpTo(controller.offset);
        }
      }
    });

    return Flexible(
      child: SingleChildScrollView(
        controller: controller,
        child: Padding(
          padding: EdgeInsets.only(top: endOfScreen),
          child: RichText(
            text: TextSpan(
              children: List.generate(originalWords.length, (index) {
                String word = originalWords[index];
                Color wordColor;
                Color borderColor;
                Color backgroundColor;
                FontWeight weight = FontWeight.normal;

                if (matchedIndexes.contains(index)) {
                  wordColor = highlightCorrectColor;
                  borderColor = highlightCorrectColor;
                  backgroundColor = highlightCorrectColor;
                } else if (mispronounceIndexes.contains(index)) {
                  wordColor = Colors.blue;
                  backgroundColor = Colors.blue;
                  borderColor = Colors.blue;
                  weight = FontWeight.bold;
                } else if (skippedIndexes.contains(index)) {
                  wordColor = highlightWrongColor;
                  backgroundColor = highlightWrongColor;
                  borderColor = highlightWrongColor;
                  errorWordsIndexList.add(index);
                  weight = FontWeight.bold;
                } else if (index < targetIndex) {
                  wordColor = highlightWrongColor;
                  backgroundColor = highlightWrongColor;
                  borderColor = highlightWrongColor;
                  errorWordsIndexList.add(index);
                  weight = FontWeight.bold;
                } else {
                  wordColor = defaultTextColor;
                  backgroundColor = Color(0xFFFFFFFF);
                  borderColor = Color(0xFFFFFFFF);
                }

                // print("WRONG INDEXES: ${errorWordsIndexList}");
                errorWordsIndexes = errorWordsIndexList;

                return WidgetSpan(
                  child: Container(
                    margin: EdgeInsets.symmetric(vertical: 2),
                    padding: EdgeInsets.symmetric(horizontal: 2),
                    decoration: BoxDecoration(
                      border: Border.all(
                        color: borderColor,
                        width: 1.0,
                      ),
                      color: backgroundColor.withAlpha(25),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      word,
                      style: TextStyle(
                        fontSize: fontSize,
                        height: lineSpace,
                        fontWeight: weight,
                        color: wordColor, // Text color
                      ),
                    ),
                  ),
                );
              }),
            ),
          ),
        ),
      ),
    );
  }


  void startAutoScroll(ScrollController controller, int autoScrollSpeed) {
    if (!controller.hasClients) return;

    if (autoScrollSpeed == 0){
      print("it has to be paused");
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

  static Future<String?> recognize({
    required PhoneticType type,
    String? languageCode,
    required int timeout,
    String? sentence,
  }) async {
    // Validate timeout
    if (timeout < 0) {
      throw ArgumentError('Timeout must be a positive value');
    }

    try {
      final String result = await _channel.invokeMethod('recognize', {
        'type': type.toString().split('.').last,
        'languageCode': languageCode,
        'timeout': timeout,
        'sentence': sentence
      });

      if (kDebugMode) {
        print("Received result: $result");
      }

      if (result.isEmpty || result == "null") {
        return "";
      }
      return result;
    } on PlatformException catch (e) {
      if (kDebugMode) {
        print("Speech Recognition Error: ${e.code} - ${e.message}");
      }
      // You might want to handle specific error codes differently
      switch (e.code) {
        case 'TIMEOUT':
          return "";
        case 'SPEECH_ERROR':
          return "";
        case 'ALREADY_ACTIVE':
          return "";
        default:
          return "";
      }
    }
  }
}