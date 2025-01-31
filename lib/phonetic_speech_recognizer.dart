import 'package:flutter/services.dart';

enum PhoneticType { alphabet, number, wordsOrSentence }

class PhoneticSpeechRecognizer {
  static const MethodChannel _channel =
  MethodChannel('phonetic_speech_recognizer');

  static Future<String?> getPlatformVersion() async {
    try {
      final String? version = await _channel.invokeMethod('getPlatformVersion');
      return version;
    } on PlatformException catch (e) {
      print("Error: ${e.message}");
      return null;
    }
  }

  static Future<String?> recognize({
    required PhoneticType type,
    String? languageCode, required int timeout,
  }) async {
    try {
      final String result = await _channel.invokeMethod('recognize', {
        'type': type.toString().split('.').last,
        'languageCode': languageCode,
      });

      if (result.isEmpty || result == "null") {
        return "Recognition failed or no result";
      }
      return result;
    } on PlatformException catch (e) {
      print("Error: ${e.message}");
      return "Error during recognition";
    }
  }
}