import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

enum PhoneticType { alphabet, koreanAlphabet, number, englishWordsOrSentence, japaneseAlphabet, koreanNumber, allLanguageSupport, paragraphsMapping }

class PhoneticSpeechRecognizer {
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

  // build the real time highlight when speaking
  Widget buildRealTimeHighlightedText(String _randomText, String _partialText) {
    String cleanText(String text) {
      return text.replaceAll(RegExp(r'[^\w\s]'), '').toLowerCase().trim();
    }

    List<String> originalWords = _randomText.split(RegExp(r'\s+'));
    List<String> targetWords = originalWords.map(cleanText).toList();
    List<String> partialWords = _partialText.split(RegExp(r'\s+')).map(cleanText).toList();

    int startIndex = -1;

    // Find the first word in partialText within targetWords
    for (int i = 0; i < targetWords.length; i++) {
      if (targetWords[i].contains(partialWords.firstOrNull ?? "")) {
        startIndex = i;
        break;
      }
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        RichText(
          text: TextSpan(
            children: List.generate(originalWords.length, (index) {
              String originalWord = originalWords[index];
              bool isHighlighted = startIndex != -1 && index >= startIndex &&
                  (index - startIndex) < partialWords.length &&
                  targetWords[index] == partialWords[index - startIndex];

              return TextSpan(
                text: "$originalWord ",
                style: TextStyle(
                  fontSize: 16,
                  color: Colors.black,
                  fontWeight: isHighlighted ? FontWeight.bold : FontWeight.normal,
                  backgroundColor: isHighlighted ? Colors.yellow : Colors.transparent,
                ),
              );
            }),
          ),
        ),
        const SizedBox(height: 20),
        Text(
          "Current recognition: $_partialText",
          style: const TextStyle(
            fontSize: 18,
            fontStyle: FontStyle.italic,
            color: Colors.grey,
          ),
        ),
      ],
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