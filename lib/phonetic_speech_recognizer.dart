import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

enum PhoneticType { alphabet, koreanAlphabet, number, englishWordsOrSentence, hiraganaJapanese, katakanaJapanese, allLanguageSupport }

class PhoneticSpeechRecognizer {
  static const MethodChannel _channel =
  MethodChannel('phonetic_speech_recognizer');

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