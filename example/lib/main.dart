import 'package:flutter/material.dart';
import 'dart:async';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:phonetic_speech_recognizer/phonetic_speech_recognizer.dart';
import 'package:phonetic_speech_recognizer_example/randomsetencegenerator.dart';

enum RecognitionType { alphabets, numbers, koreanAlphabets, sentences, katakanaJapanese, hiraganaJapanese }

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _recognizedText = "Press the button to start";
  bool _isListening = false;
  double _progress = 1.0;
  int _timeoutDuration = 5000;
  Timer? _timer;
  RecognitionType _selectedType = RecognitionType.sentences;
  String _randomText = RandomSentenceGenerator.generateSentence();

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _requestAudioPermission() async {
    PermissionStatus status = await Permission.microphone.request();
    if (status.isGranted) {
      _startRecognition();
    } else {
      setState(() {
        _recognizedText = "Permission denied. Can't start recognition.";
      });
    }
  }

  Future<void> _startRecognition() async {
    if (_isListening) return;

    setState(() {
      _isListening = true;
      _progress = 1.0;
      _recognizedText = "Listening...";
    });

    _timer = Timer.periodic(const Duration(milliseconds: 100), (timer) {
      setState(() {
        _progress -= (100 / _timeoutDuration);
        if (_progress <= 0) timer.cancel();
      });
    });

    PhoneticType phoneticType;
    switch (_selectedType) {
      case RecognitionType.alphabets:
        phoneticType = PhoneticType.alphabet;
        break;
      case RecognitionType.numbers:
        phoneticType = PhoneticType.number;
        break;

      case RecognitionType.katakanaJapanese:
        phoneticType = PhoneticType.katakanaJapanese;
        break;
      case RecognitionType.hiraganaJapanese:
        phoneticType = PhoneticType.hiraganaJapanese;
        break;
      case RecognitionType.koreanAlphabets:
        phoneticType = PhoneticType.koreanAlphabet;
        break;

      case RecognitionType.sentences:
      default:
        phoneticType = PhoneticType.englishWordsOrSentence;
        break;
    }

    try {
      final result = await PhoneticSpeechRecognizer.recognize(
          languageCode: "en-US",
          type: phoneticType,
          timeout: _timeoutDuration,
          sentence: _randomText
      );

      // Print the result to the console
      print("Recognized Text: $result");

      setState(() {
        _recognizedText = result ?? "Recognition failed";
        if (_recognizedText == _randomText) {
          _generateRandomText();
        }
      });
    } on PlatformException catch (e) {
      setState(() {
        _recognizedText = e.code == 'TIMEOUT' ? "Timeout: No speech detected" : "Error: ${e.message}";
      });
    } finally {
      _timer?.cancel();
      setState(() {
        _isListening = false;
        _progress = 1.0;
      });
    }
  }

  void _generateRandomText() {
    switch (_selectedType) {
      case RecognitionType.alphabets:
        _randomText = String.fromCharCode(65 + (DateTime.now().millisecondsSinceEpoch % 26));
        break;
      case RecognitionType.numbers:
        _randomText = (DateTime.now().millisecondsSinceEpoch % 100).toString();
        break;
      case RecognitionType.koreanAlphabets:
        _randomText = String.fromCharCode(0xAC00 + (DateTime.now().millisecondsSinceEpoch % 11172));
        break;
      case RecognitionType.hiraganaJapanese:
        _randomText = String.fromCharCode(0x3040 + (DateTime.now().millisecondsSinceEpoch % 96)); // Range for Hiragana
        break;
      case RecognitionType.katakanaJapanese:
        _randomText = String.fromCharCode(0x30A0 + (DateTime.now().millisecondsSinceEpoch % 96)); // Range for Katakana
        break;
      case RecognitionType.sentences:
      default:
        _randomText = RandomSentenceGenerator.generateSentence();
        break;
    }
    setState(() {});
  }


  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Speech Recognizer'),
          actions: [
            PopupMenuButton<RecognitionType>(
              onSelected: (RecognitionType type) {
                setState(() {
                  _selectedType = type;
                  _generateRandomText();
                });
              },
              itemBuilder: (BuildContext context) => <PopupMenuEntry<RecognitionType>>[
                const PopupMenuItem(value: RecognitionType.alphabets, child: Text('Alphabets')),
                const PopupMenuItem(value: RecognitionType.numbers, child: Text('Numbers')),
                const PopupMenuItem(value: RecognitionType.koreanAlphabets, child: Text('Korean Alphabets')),
                const PopupMenuItem(value: RecognitionType.sentences, child: Text('Sentences')),
                const PopupMenuItem(value: RecognitionType.hiraganaJapanese, child: Text('Japanese (Hiragana)')),
                const PopupMenuItem(value: RecognitionType.katakanaJapanese, child: Text('Japanese (Katakana)')),
              ],
            ),
          ],
        ),
        body: Padding(
          padding: const EdgeInsets.all(20.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(_randomText, style: const TextStyle(fontSize: 16)),
              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: _isListening ? null : _requestAudioPermission,
                child: Text(_isListening ? 'Processing...' : 'Start Recognition'),
              ),
              const SizedBox(height: 20),
              LinearProgressIndicator(value: _progress),
              const SizedBox(height: 20),
              Text(_recognizedText, textAlign: TextAlign.center),
            ],
          ),
        ),
      ),
    );
  }
}
