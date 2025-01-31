import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:phonetic_speech_recognizer/phonetic_speech_recognizer_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelPhoneticSpeechRecognizer platform = MethodChannelPhoneticSpeechRecognizer();
  const MethodChannel channel = MethodChannel('phonetic_speech_recognizer');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      (MethodCall methodCall) async {
        return '42';
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

  test('getPlatformVersion', () async {
    expect(await platform.getPlatformVersion(), '42');
  });
}
