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
  String _newText = "";
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
        _randomText = "This is a test paragraph for speech recognition. The goal is to check if it can detect spoken words accurately. It's a simple paragraph without confusing words. Conflicts happen when similar sounding words like [RIGHT] and [WRITE] are used. In such cases, it's hard to know which spelling is correct."
       " I visited Bandipur, a small hill town. The streets were clean with old houses and stone paths. I saw mountain views while walking around. People were friendly and smiling. I ate local food and watched the sunset. Bandipur was quiet and peaceful.";
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
      _newText = "$_recognizedText $_partialText";
      print("Recognized Text: $_newText");
      return recognizer.buildRealTimeHighlightedText(
        randomText: _randomText,
        partialText: _newText,
        highlightCorrectColor: Color(0xFF00BC7D),
        defaultTextColor: Colors.black,
        highlightWrongColor: Colors.red,
        isAutoScroll: true,
        autoScrollSpeed: 200,
        fontSize: 30,
        lineSpace: 1.5,
        endOfScreen: 300,
      );
    } else {
      return recognizer.buildRealTimeHighlightedText(
        randomText: _randomText,
        partialText: _newText,
        highlightCorrectColor: Color(0xFF00BC7D),
        defaultTextColor: Colors.black,
        highlightWrongColor: Colors.red,
        isAutoScroll: false,
        autoScrollSpeed: 0,
        fontSize: 30,
        lineSpace: 1.5,
        endOfScreen: 300,
      );
    }
  }

  int getWordCount(String text) {
    return text.trim().split(RegExp(r'\s+')).where((word) => word.isNotEmpty).length;
  }

  Widget _displayMistakes(){
    int wordCount = getWordCount(_randomText);
    return recognizer.displayMistakeWords(
        errorWordsList: recognizer.errorWordsIndexes,
        randomText: _randomText,
        defaultTextColor: Colors.black,
        highlightWrongColor: Colors.red,
        fontSize: 18,
        lineSpace: 1.2,
        errorPronunciationList: recognizer.errorPronouncationList,
        totalWords: wordCount,
        correctPronouncationList: recognizer.correctPronouncationList
    );
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
                const PopupMenuItem(value: RecognitionType.allLanguageSupport, child: Text('Japanese (Numbers)')),
                const PopupMenuItem(value: RecognitionType.paragraphMapping, child: Text('Paragraphs')),
              ],
            ),
          ],
        ),
        body: Padding(
          padding: const EdgeInsets.all(20.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // Use a more precise condition to check if the timer is complete
              Expanded(
                child: _progress <= 0.001 || (!_isListening && _isTextReceived)
                    ? _displayMistakes()
                    : _buildHighlightedText(),
              ),
              SizedBox(
                height: 10,
              ),
              _isTextReceived
                  ? Container() // If _isTextReceived is true, show nothing
                  : Text(
                _isListening ? "Listening..." : "",
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.blue),
              ),

              const SizedBox(height: 20),
              GestureDetector(
                onLongPressStart: (_) => _requestAudioPermission(),
                onLongPressEnd: (_) {
                  if (_isRealTIme) {
                    print("error words list: ${recognizer.errorWordsIndexes}");
                    stopRecognition();  // Stop recognition immediately if _isRealTime is true
                  } else {
                    _isTextReceived ? stopRecognition() : print("Still analyzing");
                  }
                },
                child: Container(
                  padding: EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: _isListening ? Colors.red : Colors.blue,
                    shape: BoxShape.circle,
                  ),
                  child: Icon(Icons.mic, color: Colors.white, size: 32),
                ),
              ),
              SizedBox(
                height: 10,
              ),
              LinearProgressIndicator(value: _progress),
            ],
          ),
        ),
      ),
    );
  }
}