import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'phonetic_speech_recognizer_method_channel.dart';

abstract class PhoneticSpeechRecognizerPlatform extends PlatformInterface {
  /// Constructs a PhoneticSpeechRecognizerPlatform.
  PhoneticSpeechRecognizerPlatform() : super(token: _token);

  static final Object _token = Object();

  static PhoneticSpeechRecognizerPlatform _instance = MethodChannelPhoneticSpeechRecognizer();

  /// The default instance of [PhoneticSpeechRecognizerPlatform] to use.
  ///
  /// Defaults to [MethodChannelPhoneticSpeechRecognizer].
  static PhoneticSpeechRecognizerPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [PhoneticSpeechRecognizerPlatform] when
  /// they register themselves.
  static set instance(PhoneticSpeechRecognizerPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
