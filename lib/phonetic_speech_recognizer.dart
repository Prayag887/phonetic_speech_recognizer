import 'package:flutter/services.dart';

enum PhoneticType { alphabet, koreanAlphabet, number, englishWordsOrSentence, hiraganaJapanese, katakanaJapanese }

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


  Future<bool> stopRecognition() async {
    try {
      final bool result = await _channel.invokeMethod('stopRecognition');
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
    try {
      final String result = await _channel.invokeMethod('recognize', {
        'type': type.toString().split('.').last,
        'languageCode': languageCode,
        'timeout': timeout,
        'sentence' : sentence
      });

      if (result.isEmpty || result == "null") {
        return "";
      }
      return result;
    } on PlatformException catch (e) {
      print("Error: ${e.message}");
      return "";
    }
  }
}