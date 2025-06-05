import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'dart:async';
import 'package:permission_handler/permission_handler.dart';
import 'package:phonetic_speech_recognizer/phonetic_speech_recognizer.dart';
import 'package:phonetic_speech_recognizer_example/randomsetencegenerator.dart';

enum RecognitionType {
  alphabets,
  numbers,
  koreanAlphabets,
  sentences,
  koreanNumber,
  japaneseAlphabet,
  allLanguageSupport,
  koreanNumbers,
  paragraphMapping
}

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
  double _confidence = 0.0;
  final int _timeoutDuration = 120000;
  Timer? _timer;
  RecognitionType _selectedType = RecognitionType.sentences;
  String _randomText = "This is an apple";
  String _randomNumber = RandomSentenceGenerator.generateSerialKoreanNumber();
  String _partialText = "";
  bool _isTextReceived = false;
  bool _isRealTIme = false;
  Ticker? _ticker;
  String _latestPartialText = '';

  PhoneticSpeechRecognizer recognizer = PhoneticSpeechRecognizer();
  StreamSubscription? subscription;

  @override
  void dispose() {
    _timer?.cancel();
    subscription?.cancel();
    super.dispose();
  }

  Future<void> _requestAudioPermission() async {
    _startRecognition();
  }

  void stopRecognition() {
    PhoneticSpeechRecognizer.stopRecognition();
    _timer?.cancel();
    subscription?.cancel();
    setState(() {
      _isListening = false;
      _progress = 1.0;
      _partialText = "";
      // Don't reset confidence here - keep the last received value
    });
  }

  void _listenForPartialResults() {
    subscription?.cancel();
    _ticker?.dispose();

    // Step 1: Capture stream data into a buffer
    subscription = recognizer.listenToStream().listen((data) {
      _latestPartialText = data;
    }, onError: (error) {
      if (kDebugMode) {
        print("Stream error: $error");
      }
    });

    // Step 2: Poll buffer at 30 FPS
    _ticker = Ticker((_) {
      if (_partialText != _latestPartialText) {
        setState(() {
          _partialText = _latestPartialText;
        });
      }
    });

    _ticker!.start();
  }

  Future<void> _startRecognition() async {
    if (_isListening) return;

    setState(() {
      _isTextReceived = false;
      _isListening = true;
      _progress = 1.0;
      _partialText = "";
      // Only reset confidence at the start of new recognition
      _confidence = 0.0;
      _recognizedText = "";
    });

    _timer = Timer.periodic(const Duration(milliseconds: 100), (timer) {
      setState(() {
        _progress -= (100 / _timeoutDuration);
        if (_progress <= 0) {
          timer.cancel();
          if (_isListening) stopRecognition();
        }
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
      case RecognitionType.paragraphMapping:
        phoneticType = PhoneticType.paragraphsMapping;
        languageCode = "en-US";
        _listenForPartialResults();
        break;
      case RecognitionType.numbers:
        phoneticType = PhoneticType.number;
        languageCode = "ne-NP";
        break;
      case RecognitionType.koreanNumber:
        phoneticType = PhoneticType.koreanNumber;
        languageCode = "ko-KR";
        break;
      case RecognitionType.japaneseAlphabet:
        phoneticType = PhoneticType.japaneseAlphabet;
        languageCode = "ja-JP";
        break;
      case RecognitionType.koreanNumbers:
        String numericPart = _randomNumber.substring(_randomNumber.indexOf('(') + 1, _randomNumber.indexOf(')'));
        int number = int.tryParse(numericPart) ?? 0;
        final koreanNumbers = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 21, 100};
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

    bool sendKeyOnly = _selectedType == RecognitionType.alphabets ||
        _selectedType == RecognitionType.numbers ||
        _selectedType == RecognitionType.paragraphMapping;

    try {
      final result = await PhoneticSpeechRecognizer.recognize(
        languageCode: languageCode,
        type: phoneticType,
        timeout: _timeoutDuration,
        sentence: textToRecognize,
        sendKeyOnly: sendKeyOnly,
      );

      setState(() {
        if (!sendKeyOnly && result is Map) {
          if (result.isNotEmpty) {
            final confidenceStr = result['confidence'] ?? 0.0;
            final recognizedValue = result['text']?.toString() ?? '';

            _recognizedText = recognizedValue;
            _confidence = confidenceStr;

            // Compare recognized text with expected text (_randomText)
            if (_recognizedText == _randomText) {
              _generateRandomText();
            }
          } else {
            _recognizedText = "Recognition failed";
            _confidence = 0.0;
          }
        } else {
          // When sendKeyOnly is true, result is just string
          _recognizedText = result?.toString() ?? "Recognition failed";
          if (result != null && result.toString().isNotEmpty) {
            _confidence = 1.0;
          } else {
            _confidence = 0.0;
          }

          if (_selectedType == RecognitionType.koreanNumbers) {
            String insideBrackets = _randomNumber.substring(
                _randomNumber.indexOf('(') + 1,
                _randomNumber.indexOf(')')
            );
            if (insideBrackets.contains(_recognizedText)) {
              _generateRandomText();
            }
          } else {
            if (_recognizedText == _randomText) {
              _generateRandomText();
            }
          }
        }
        _isTextReceived = _recognizedText.isNotEmpty;
      });

      if (!_isRealTIme) stopRecognition();
    } catch (error) {
      setState(() {
        _recognizedText = "Error: $error";
        _isTextReceived = false;
        _confidence = 0.0;
      });
      stopRecognition();
    }
  }

  void _generateRandomText() {
    switch (_selectedType) {
      case RecognitionType.alphabets:
        _isRealTIme = false;
        _randomText = String.fromCharCode(65 + (DateTime.now().millisecondsSinceEpoch % 26));
        break;
      case RecognitionType.numbers:
        _isRealTIme = false;
        _randomText = (DateTime.now().millisecondsSinceEpoch % 100).toString();
        break;
      case RecognitionType.koreanAlphabets:
        _isRealTIme = false;
        _randomText = String.fromCharCode(0xAC00 + (DateTime.now().millisecondsSinceEpoch % 11172));
        break;
      case RecognitionType.japaneseAlphabet:
        _isRealTIme = false;
        _randomText = String.fromCharCode(0x3040 + (DateTime.now().millisecondsSinceEpoch % 96));
        break;
      case RecognitionType.koreanNumber:
        _isRealTIme = false;
        _randomText = String.fromCharCode(0x30A0 + (DateTime.now().millisecondsSinceEpoch % 96));
        break;
      case RecognitionType.allLanguageSupport:
        _isRealTIme = false;
        _randomText = String.fromCharCode(0x3040 + (DateTime.now().millisecondsSinceEpoch % 96));
        break;
      case RecognitionType.koreanNumbers:
        _isRealTIme = false;
        _randomNumber = RandomSentenceGenerator.generateSerialKoreanNumber();
        break;
      case RecognitionType.paragraphMapping:
        _recognizedText = "";
        _isRealTIme = true;
        _randomText = "This is a random paragraph created for the testing purpose. The test is to be carried out for speech recognizer to see if it can "
            "accurately detect the words being spoken. This is a much simpler form of paragraph. This paragraph does not contain the words that are conflicting "
            "with each others. The conflicts can appear when there are multiple words that sounds the same but are different in spellings like [RIGHT] and [WRITE]. "
            "When both words are being used then there is no way to check which of the two spellings are required to be registered.";
        break;
      default:
        _isRealTIme = false;
        _randomText = RandomSentenceGenerator.generateSentence();
        break;
    }
    setState(() {});
  }

  Widget _buildHighlightedText() {
    if (_selectedType == RecognitionType.paragraphMapping && _isListening) {
      return recognizer.buildRealTimeHighlightedText(
        randomText: _randomText,
        partialText: _partialText,
        highlightCorrectColor: Colors.green,
        defaultTextColor: Colors.black,
        highlightWrongColor: Colors.red,
        isAutoScroll: true,
        autoScrollSpeed: 1,
        fontSize: 18,
        lineSpace: 1.5,
        endOfScreen: 0.5,
      );
    } else {
      List<String> words = _randomText.split(" ");
      return RichText(
        text: TextSpan(
          children: List.generate(words.length, (index) {
            return TextSpan(
              text: "${words[index]} ",
              style: const TextStyle(
                fontSize: 18,
                color: Colors.black,
                fontWeight: FontWeight.bold,
                backgroundColor: Colors.transparent,
              ),
            );
          }),
        ),
      );
    }
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
                const PopupMenuItem(value: RecognitionType.koreanNumber, child: Text('Korean Number')),
                const PopupMenuItem(value: RecognitionType.japaneseAlphabet, child: Text('Japanese Alphabet')),
                const PopupMenuItem(value: RecognitionType.allLanguageSupport, child: Text('All Language Support')),
                const PopupMenuItem(value: RecognitionType.koreanNumbers, child: Text('Korean Numbers')),
                const PopupMenuItem(value: RecognitionType.paragraphMapping, child: Text('Paragraph Mapping')),
              ],
            ),
          ],
        ),
        body: Container(
          padding: const EdgeInsets.all(16),
          child: Column(
            children: [
              Expanded(
                child: SingleChildScrollView(
                  child: _buildHighlightedText(),
                ),
              ),
              const SizedBox(height: 20),
              Text(
                _isListening ? "Listening..." : "Tap to start recognition",
                style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 10),
              LinearProgressIndicator(value: _progress),
              const SizedBox(height: 20),
              Text(
                "Recognized Text:",
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
              ),
              Text(
                _recognizedText,
                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.blue),
              ),
              const SizedBox(height: 10),
              Text(
                "Confidence: ${_confidence.toStringAsFixed(2)}",
                style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: Colors.green),
              ),
              const SizedBox(height: 20),
              GestureDetector(
                onTap: () {
                  if (_isListening) {
                    stopRecognition();
                  } else {
                    _requestAudioPermission();
                  }
                },
                child: Container(
                  padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 32),
                  decoration: BoxDecoration(
                    color: _isListening ? Colors.red : Colors.blue,
                    borderRadius: BorderRadius.circular(8),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withOpacity(0.2),
                        spreadRadius: 2,
                        blurRadius: 4,
                        offset: const Offset(0, 2),
                      ),
                    ],
                  ),
                  child: Text(
                    _isListening ? "Stop" : "Start",
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}