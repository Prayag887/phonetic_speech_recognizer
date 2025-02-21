import 'package:flutter/material.dart';
import 'dart:async';
import 'package:permission_handler/permission_handler.dart';
import 'package:phonetic_speech_recognizer/phonetic_speech_recognizer.dart';
import 'package:phonetic_speech_recognizer_example/randomsetencegenerator.dart';

enum RecognitionType { alphabets, numbers, koreanAlphabets, sentences, japaneseNumber, japaneseAlphabet, allLanguageSupport, koreanNumbers }

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
  final int _timeoutDuration = 5000;
  Timer? _timer;
  RecognitionType _selectedType = RecognitionType.sentences;
  String _randomText = "this is an apple";
  String _randomNumber = RandomSentenceGenerator.generateSerialKoreanNumber();

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

  void stopRecognition() {
    PhoneticSpeechRecognizer.stopRecognition();
    _timer?.cancel();
    setState(() {
      _isListening = false;
      _progress = 1.0;
      _recognizedText = "Recognition has been stopped";
    });
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
    String languageCode;
    String textToRecognize = _selectedType == RecognitionType.koreanNumbers ? _randomNumber : _randomText;

    switch (_selectedType) {
      case RecognitionType.alphabets:
        phoneticType = PhoneticType.alphabet;
        languageCode = "en-US";
        break;

      case RecognitionType.numbers:
        phoneticType = PhoneticType.number;
        languageCode = "ne-NP";
        break;
      case RecognitionType.japaneseNumber:
        phoneticType = PhoneticType.koreanNumber;
        languageCode = "ko-KR";
        break;
      case RecognitionType.japaneseAlphabet:
        phoneticType = PhoneticType.japaneseAlphabet;
        languageCode = "ja-JP";
        break;

      case RecognitionType.koreanNumbers:
      // Extract the numeric part from the Korean number string
        String numericPart = _randomNumber.substring(_randomNumber.indexOf('(') + 1, _randomNumber.indexOf(')'));
        int number = int.tryParse(numericPart) ?? 0;
        final koreanNumbers = {1, 2, 3, 4, 5, 6, 7, 8, 9 ,10, 20, 21, 100};
    // 18
        phoneticType = koreanNumbers.contains(number)
            ? PhoneticType.koreanNumber
            : PhoneticType.allLanguageSupport;
        languageCode = "ko-KR";
        break;

      case RecognitionType.koreanAlphabets:
        phoneticType = PhoneticType.koreanAlphabet;
        languageCode = "en-US";
        break;

      case RecognitionType.allLanguageSupport:
        phoneticType = PhoneticType.allLanguageSupport;
        languageCode = "ja-JP";
        break;

      case RecognitionType.sentences:
      default:
        phoneticType = PhoneticType.englishWordsOrSentence;
        languageCode = "en-US";
        break;
    }

    PhoneticSpeechRecognizer.recognize(
      languageCode: languageCode,
      type: phoneticType,
      timeout: _timeoutDuration,
      sentence: textToRecognize,
    ).then((result) {
      setState(() {
        _recognizedText = result ?? "Recognition failed";

        // Check if recognition was successful
        if (_selectedType == RecognitionType.koreanNumbers) {
          // For Korean numbers, we need to compare with the Korean number
          String insideBrackets = _randomNumber.substring(
              _randomNumber.indexOf('(') + 1,
              _randomNumber.indexOf(')')
          );
          if (insideBrackets.contains(_recognizedText)) {
            _generateRandomText();
          }
        } else {
          // For other types, compare with _randomText
          if (_recognizedText == _randomText) {
            _generateRandomText();
          }
        }
      });
    }).catchError((error) {
      setState(() {
        _recognizedText = "Error: $error";
      });
    }).whenComplete(() {
      _timer?.cancel();
      setState(() {
        _isListening = false;
        _progress = 1.0;
      });
    });
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
      case RecognitionType.japaneseAlphabet:
        _randomText = String.fromCharCode(0x3040 + (DateTime.now().millisecondsSinceEpoch % 96));
        break;
      case RecognitionType.japaneseNumber:
        _randomText = String.fromCharCode(0x30A0 + (DateTime.now().millisecondsSinceEpoch % 96));
        break;
      case RecognitionType.allLanguageSupport:
        _randomText = String.fromCharCode(0x3040 + (DateTime.now().millisecondsSinceEpoch % 96));
        break;
      case RecognitionType.koreanNumbers:
        _randomNumber = RandomSentenceGenerator.generateSerialKoreanNumber();
        break;
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
                const PopupMenuItem(value: RecognitionType.japaneseAlphabet, child: Text('Japanese (Alphabets)')),
                const PopupMenuItem(value: RecognitionType.koreanNumbers, child: Text('Korean (Numbers)')),
                const PopupMenuItem(
                  value: RecognitionType.allLanguageSupport,
                  child: Text('Japanese (Numbers)'),
                ),
              ],
            ),
          ],
        ),
        body: Padding(
          padding: const EdgeInsets.all(20.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                _selectedType == RecognitionType.koreanNumbers ? _randomNumber : _randomText,
                style: const TextStyle(fontSize: 30),
              ),
              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: _isListening ? null : _requestAudioPermission,
                child: Text(_isListening ? 'Processing...' : 'Start Recognition'),
              ),
              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: _isListening ? stopRecognition : null,
                child: const Text('Stop Recognition'),
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