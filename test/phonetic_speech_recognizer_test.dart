import 'package:flutter_test/flutter_test.dart';
import 'package:phonetic_speech_recognizer/phonetic_speech_recognizer.dart';
import 'package:phonetic_speech_recognizer/phonetic_speech_recognizer_platform_interface.dart';
import 'package:phonetic_speech_recognizer/phonetic_speech_recognizer_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockPhoneticSpeechRecognizerPlatform
    with MockPlatformInterfaceMixin
    implements PhoneticSpeechRecognizerPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final PhoneticSpeechRecognizerPlatform initialPlatform = PhoneticSpeechRecognizerPlatform.instance;

  test('$MethodChannelPhoneticSpeechRecognizer is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelPhoneticSpeechRecognizer>());
  });

  test('getPlatformVersion', () async {
    PhoneticSpeechRecognizer phoneticSpeechRecognizerPlugin = PhoneticSpeechRecognizer();
    MockPhoneticSpeechRecognizerPlatform fakePlatform = MockPhoneticSpeechRecognizerPlatform();
    PhoneticSpeechRecognizerPlatform.instance = fakePlatform;

    expect(await phoneticSpeechRecognizerPlugin.getPlatformVersion(), '42');
  });
}
