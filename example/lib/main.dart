import 'package:flutter/material.dart';
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
  final int _timeoutDuration = 220000;
  Timer? _timer;
  RecognitionType _selectedType = RecognitionType.sentences;
  String _randomText = "This is an apple";
  String _randomNumber = RandomSentenceGenerator.generateSerialKoreanNumber();
  String _partialText = "";
  bool _isTextReceived = false;
  bool _isRealTIme = false;

  PhoneticSpeechRecognizer recognizer = PhoneticSpeechRecognizer();
  // For streaming partial results
  StreamSubscription? subscription;

  @override
  void dispose() {
    _timer?.cancel();
    subscription?.cancel();
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
    subscription?.cancel();
    setState(() {
      _isListening = false;
      _progress = 1.0;
      _partialText = "";
    });
  }

  // this is to populate the partial data for the real time data mapping
  void _listenForPartialResults() {
    // Cancel any existing subscription
    subscription?.cancel();
    // Start listening to the stream
    subscription = recognizer.listenToStream().listen((data) {
      setState(() {
        _partialText = data;
      });
    }, onError: (error) {
      print("Stream error: $error");
    });
  }

  Future<void> _startRecognition() async {
    if (_isListening) return;

    setState(() {
      _isTextReceived = false;
      _isListening = true;
      _progress = 1.0;
      // _recognizedText = "Listening...";
      _partialText = "";
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
      case RecognitionType.paragraphMapping:
        phoneticType = PhoneticType.paragraphsMapping;
        languageCode = "en-US";
        // Start listening to partial results
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
      // Extract the numeric part from the Korean number string
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

    PhoneticSpeechRecognizer.recognize(
      languageCode: languageCode,
      type: phoneticType,
      timeout: _timeoutDuration,
      sentence: textToRecognize,
    ).then((result) {
      setState(() {
        _recognizedText = result ?? "Recognition failed";
        if(_recognizedText != null || _recognizedText.isNotEmpty){
          _isTextReceived = true;
        } else {
          _isTextReceived = false;
        }

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
        // Cancel subscription when done
        subscription?.cancel();
        _partialText = "";
      });
    });
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
        _randomText = RandomSentenceGenerator.generateSentence();
        break;
    }
    setState(() {});
  }

  Widget _buildHighlightedText() {
    if (_selectedType == RecognitionType.paragraphMapping && _isListening) {
      // For real-time highlighting during paragraph mapping
      return recognizer.buildRealTimeHighlightedText(randomText: _randomText, partialText: _partialText,
        highlightCorrectColor : Color(0xFF00BC7D), defaultTextColor: Colors.black, highlightWrongColor: Colors.red,
        isAutoScroll: true, autoScrollSpeed: 280, fontSize: 30, lineSpace: 1.5);
    } else {
      // For normal highlighting
      List<String> words = _randomText.split(" ");

      return SingleChildScrollView(child: RichText(
        text: TextSpan(
          children: List.generate(words.length, (index) {

            return TextSpan(
              text: "${words[index]} ",
              style: TextStyle(
                fontSize: 22,
                color: Color(0xFF444444),
                fontWeight: FontWeight.normal,
                backgroundColor:Colors.transparent,
              ),
            );
          }),
        ),
      ));
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
              _buildHighlightedText(),
              SizedBox(
                height: 10,
              ),
              _isTextReceived
                  ? Container() // If _isTextReceived is true, show nothing
                  : Text(
                _isListening ? "Listening..." : "",
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.blue),
              ),

              // _selectedType == RecognitionType.koreanNumbers
              //     ? Text(
              //   _randomNumber,
              //   style: TextStyle(fontSize: 22, color: Color(0xFF444444)),
              // )
              //     : Text(
              //   _randomText,
              //   style: TextStyle(fontSize: 22, color: Color(0xFF444444)),
              // ),

              const SizedBox(height: 20),
              GestureDetector(
                onLongPressStart: (_) => _requestAudioPermission(),
                onLongPressEnd: (_) {
                  if (_isRealTIme) {
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