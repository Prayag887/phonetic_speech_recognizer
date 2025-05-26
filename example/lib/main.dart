import 'package:flutter/foundation.dart';
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
  // ValueNotifiers to replace setState
  final ValueNotifier<String> _recognizedTextNotifier = ValueNotifier<String>("Press the button to start");
  final ValueNotifier<bool> _isListeningNotifier = ValueNotifier<bool>(false);
  final ValueNotifier<double> _progressNotifier = ValueNotifier<double>(1.0);
  final ValueNotifier<RecognitionType> _selectedTypeNotifier = ValueNotifier<RecognitionType>(RecognitionType.sentences);
  final ValueNotifier<String> _randomTextNotifier = ValueNotifier<String>("This is an apple");
  final ValueNotifier<String> _randomNumberNotifier = ValueNotifier<String>(RandomSentenceGenerator.generateSerialKoreanNumber());
  final ValueNotifier<String> _partialTextNotifier = ValueNotifier<String>("");
  final ValueNotifier<String> _newTextNotifier = ValueNotifier<String>("");
  final ValueNotifier<bool> _isTextReceivedNotifier = ValueNotifier<bool>(false);
  final ValueNotifier<bool> _isRealTimeNotifier = ValueNotifier<bool>(false);

  final int _timeoutDuration = 3000;
  Timer? _timer;

  PhoneticSpeechRecognizer recognizer = PhoneticSpeechRecognizer();
  StreamSubscription? subscription;

  @override
  void dispose() {
    _timer?.cancel();
    subscription?.cancel();

    // Dispose all ValueNotifiers
    _recognizedTextNotifier.dispose();
    _isListeningNotifier.dispose();
    _progressNotifier.dispose();
    _selectedTypeNotifier.dispose();
    _randomTextNotifier.dispose();
    _randomNumberNotifier.dispose();
    _partialTextNotifier.dispose();
    _newTextNotifier.dispose();
    _isTextReceivedNotifier.dispose();
    _isRealTimeNotifier.dispose();

    super.dispose();
  }

  Future<void> _requestAudioPermission() async {
    PermissionStatus status = await Permission.microphone.request();
    if (status.isGranted) {
      _startRecognition();
    } else {
      _recognizedTextNotifier.value = "Permission denied. Can't start recognition.";
    }
  }

  Future<void> stopRecognition() async {
    _timer?.cancel();
    await Future.delayed(Duration(seconds: 2));
    _recognizedTextNotifier.value = " ${_newTextNotifier.value}";
    subscription?.cancel();
    PhoneticSpeechRecognizer.stopRecognition();
    _isListeningNotifier.value = false;
    _progressNotifier.value = 1.0;
  }

  void _listenForPartialResults() {
    subscription?.cancel();
    subscription = recognizer.listenToStream().listen((data) {
      _partialTextNotifier.value = data;
      if (kDebugMode) {
        print("--------------- $data");
      }
    }, onError: (error) {
      if (kDebugMode) {
        print("Stream error: $error");
      }
    });
  }

  Future<void> _startRecognition() async {
    if (_isListeningNotifier.value) return;
    _partialTextNotifier.value = "";

    _isTextReceivedNotifier.value = false;
    _isListeningNotifier.value = true;
    _progressNotifier.value = 1.0;

    _timer = Timer.periodic(const Duration(milliseconds: 100), (timer) {
      _progressNotifier.value -= (100 / _timeoutDuration);
      if (_progressNotifier.value <= 0) timer.cancel();
    });

    PhoneticType phoneticType;
    String languageCode;
    String textToRecognize = _selectedTypeNotifier.value == RecognitionType.koreanNumbers
        ? _randomNumberNotifier.value
        : _randomTextNotifier.value;

    switch (_selectedTypeNotifier.value) {
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
        String numericPart = _randomNumberNotifier.value.substring(
            _randomNumberNotifier.value.indexOf('(') + 1,
            _randomNumberNotifier.value.indexOf(')')
        );
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
      _recognizedTextNotifier.value = result ?? "Recognition failed";
      if(_recognizedTextNotifier.value.isNotEmpty){
        _isTextReceivedNotifier.value = true;
      } else {
        _isTextReceivedNotifier.value = false;
      }

      if (_selectedTypeNotifier.value == RecognitionType.koreanNumbers) {
        String insideBrackets = _randomNumberNotifier.value.substring(
            _randomNumberNotifier.value.indexOf('(') + 1,
            _randomNumberNotifier.value.indexOf(')')
        );
        if (insideBrackets.contains(_recognizedTextNotifier.value)) {
          _generateRandomText();
        }
      } else {
        if (_recognizedTextNotifier.value == _randomTextNotifier.value) {
          _generateRandomText();
        }
      }
    }).catchError((error) {
      _recognizedTextNotifier.value = "Error: $error";
    }).whenComplete(() {
      _timer?.cancel();
      _isListeningNotifier.value = false;
      _progressNotifier.value = 1.0;
      subscription?.cancel();
    });
  }

  void _generateRandomText() {
    switch (_selectedTypeNotifier.value) {
      case RecognitionType.alphabets:
        _isRealTimeNotifier.value = false;
        _randomTextNotifier.value = String.fromCharCode(65 + (DateTime.now().millisecondsSinceEpoch % 26));
        break;
      case RecognitionType.numbers:
        _isRealTimeNotifier.value = false;
        _randomTextNotifier.value = (DateTime.now().millisecondsSinceEpoch % 100).toString();
        break;
      case RecognitionType.koreanAlphabets:
        _isRealTimeNotifier.value = false;
        _randomTextNotifier.value = String.fromCharCode(0xAC00 + (DateTime.now().millisecondsSinceEpoch % 11172));
        break;
      case RecognitionType.japaneseAlphabet:
        _isRealTimeNotifier.value = false;
        _randomTextNotifier.value = String.fromCharCode(0x3040 + (DateTime.now().millisecondsSinceEpoch % 96));
        break;
      case RecognitionType.koreanNumber:
        _isRealTimeNotifier.value = false;
        _randomTextNotifier.value = String.fromCharCode(0x30A0 + (DateTime.now().millisecondsSinceEpoch % 96));
        break;
      case RecognitionType.allLanguageSupport:
        _isRealTimeNotifier.value = false;
        _randomTextNotifier.value = String.fromCharCode(0x3040 + (DateTime.now().millisecondsSinceEpoch % 96));
        break;
      case RecognitionType.koreanNumbers:
        _isRealTimeNotifier.value = false;
        _randomNumberNotifier.value = RandomSentenceGenerator.generateSerialKoreanNumber();
        break;
      case RecognitionType.paragraphMapping:
        _recognizedTextNotifier.value = "";
        _isRealTimeNotifier.value = true;
        _randomTextNotifier.value = "The curtain is hanging on the wall. The cotton is hanging on the wall. The curtain and cotton are hanging on the wall. The curtain and cotton in the cartoon are hanging on the wall.";
        break;
      default:
        _randomTextNotifier.value = RandomSentenceGenerator.generateSentence();
        break;
    }
  }

  int getWordCount(String text) {
    return text.trim().split(RegExp(r'\s+')).where((word) => word.isNotEmpty).length;
  }

  Widget _displayMistakes(){
    return ValueListenableBuilder<String>(
      valueListenable: _randomTextNotifier,
      builder: (context, randomText, child) {
        int wordCount = getWordCount(randomText);
        return recognizer.displayMistakeWords(
            errorWordsList: recognizer.errorWordsIndexes,
            randomText: randomText,
            defaultTextColor: Colors.black,
            highlightWrongColor: Colors.red,
            fontSize: 18,
            lineSpace: 1.2,
            errorPronunciationList: recognizer.errorPronouncationList,
            totalWords: wordCount,
            correctPronouncationList: recognizer.correctPronouncationList
        );
      },
    );
  }

  Widget _buildHighlightedText() {
    return ValueListenableBuilder<RecognitionType>(
      valueListenable: _selectedTypeNotifier,
      builder: (context, selectedType, child) {
        return ValueListenableBuilder<bool>(
          valueListenable: _isListeningNotifier,
          builder: (context, isListening, child) {
            return ValueListenableBuilder<String>(
              valueListenable: _randomTextNotifier,
              builder: (context, randomText, child) {
                return ValueListenableBuilder<String>(
                  valueListenable: _recognizedTextNotifier,
                  builder: (context, recognizedText, child) {
                    return ValueListenableBuilder<String>(
                      valueListenable: _partialTextNotifier,
                      builder: (context, partialText, child) {
                        return ValueListenableBuilder<String>(
                          valueListenable: _newTextNotifier,
                          builder: (context, newText, child) {
                            if (selectedType == RecognitionType.paragraphMapping && isListening) {
                              _newTextNotifier.value = "$recognizedText $partialText";
                              if (kDebugMode) {
                                print("Recognized Text: ${_newTextNotifier.value}");
                              }
                              return recognizer.buildRealTimeHighlightedText(
                                randomText: randomText,
                                partialText: _newTextNotifier.value,
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
                                randomText: randomText,
                                partialText: newText,
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
                          },
                        );
                      },
                    );
                  },
                );
              },
            );
          },
        );
      },
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
                _selectedTypeNotifier.value = type;
                _generateRandomText();
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
              Expanded(
                child: ValueListenableBuilder<double>(
                  valueListenable: _progressNotifier,
                  builder: (context, progress, child) {
                    return ValueListenableBuilder<bool>(
                      valueListenable: _isListeningNotifier,
                      builder: (context, isListening, child) {
                        return ValueListenableBuilder<bool>(
                          valueListenable: _isTextReceivedNotifier,
                          builder: (context, isTextReceived, child) {
                            return progress <= 0.001 || (!isListening && isTextReceived)
                                ? _displayMistakes()
                                : _buildHighlightedText();
                          },
                        );
                      },
                    );
                  },
                ),
              ),
              SizedBox(height: 10),
              ValueListenableBuilder<bool>(
                valueListenable: _isTextReceivedNotifier,
                builder: (context, isTextReceived, child) {
                  return isTextReceived
                      ? Container()
                      : ValueListenableBuilder<bool>(
                    valueListenable: _isListeningNotifier,
                    builder: (context, isListening, child) {
                      return Text(
                        isListening ? "Listening..." : "",
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.blue),
                      );
                    },
                  );
                },
              ),
              const SizedBox(height: 20),
              ValueListenableBuilder<bool>(
                valueListenable: _isListeningNotifier,
                builder: (context, isListening, child) {
                  return GestureDetector(
                    onLongPressStart: (_) => _requestAudioPermission(),
                    onLongPressEnd: (_) {
                      if (_isRealTimeNotifier.value) {
                        print("error words list: ${recognizer.errorWordsIndexes}");
                        stopRecognition();
                      } else {
                        _isTextReceivedNotifier.value ? stopRecognition() : print("Still analyzing");
                      }
                    },
                    child: Container(
                      padding: EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: isListening ? Colors.red : Colors.blue,
                        shape: BoxShape.circle,
                      ),
                      child: Icon(Icons.mic, color: Colors.white, size: 32),
                    ),
                  );
                },
              ),
              SizedBox(height: 10),
              ValueListenableBuilder<double>(
                valueListenable: _progressNotifier,
                builder: (context, progress, child) {
                  return LinearProgressIndicator(value: progress);
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}