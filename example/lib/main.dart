import 'dart:async';
import 'dart:developer';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:just_audio/just_audio.dart';
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
  // ValueNotifiers for state management
  final ValueNotifier<String> _recognizedTextNotifier =
      ValueNotifier<String>("Press the button to start");
  final ValueNotifier<bool> _isListeningNotifier = ValueNotifier<bool>(false);
  final ValueNotifier<double> _progressNotifier = ValueNotifier<double>(1.0);
  final ValueNotifier<int> _modelDownloadProgressNotifier =
      ValueNotifier<int>(1);
  final ValueNotifier<double> _confidenceNotifier = ValueNotifier<double>(0.0);
  final ValueNotifier<RecognitionType> _selectedTypeNotifier =
      ValueNotifier<RecognitionType>(RecognitionType.sentences);
  final ValueNotifier<String> _randomTextNotifier =
      ValueNotifier<String>("This is a house.");
  final ValueNotifier<String> _randomNumberNotifier = ValueNotifier<String>(
      RandomSentenceGenerator.generateSerialKoreanNumber());
  final ValueNotifier<String> _partialTextNotifier = ValueNotifier<String>("");
  final ValueNotifier<String> _newTextNotifier = ValueNotifier<String>("");
  final ValueNotifier<bool> _isTextReceivedNotifier =
      ValueNotifier<bool>(false);
  final ValueNotifier<bool> _isRealTimeNotifier = ValueNotifier<bool>(false);

  // NEW: ValueNotifiers for detected answer display
  final ValueNotifier<String> _detectedAnswerNotifier =
      ValueNotifier<String>('');
  final ValueNotifier<bool> _showDetectedAnswerNotifier =
      ValueNotifier<bool>(false);

  late AudioPlayer _audioPlayer;
  final ValueNotifier<bool> _isPlayingNotifier = ValueNotifier<bool>(false);

  final int _timeoutDuration = 12000;
  Timer? _timer;
  Timer? _answerDisplayTimer; // NEW: Timer for answer display
  Ticker? _ticker;
  String _latestPartialText = '';

  PhoneticSpeechRecognizer recognizer = PhoneticSpeechRecognizer();
  StreamSubscription? subscription;

  @override
  void dispose() {
    _timer?.cancel();
    _answerDisplayTimer?.cancel(); // NEW: Cancel answer display timer
    subscription?.cancel();
    _ticker?.dispose();

    // Dispose all ValueNotifiers
    _recognizedTextNotifier.dispose();
    _isListeningNotifier.dispose();
    _progressNotifier.dispose();
    _confidenceNotifier.dispose();
    _selectedTypeNotifier.dispose();
    _randomTextNotifier.dispose();
    _randomNumberNotifier.dispose();
    _partialTextNotifier.dispose();
    _newTextNotifier.dispose();
    _isTextReceivedNotifier.dispose();
    _isRealTimeNotifier.dispose();
    _detectedAnswerNotifier.dispose(); // NEW: Dispose new notifiers
    _showDetectedAnswerNotifier.dispose(); // NEW: Dispose new notifiers

    _audioPlayer.dispose();
    _isPlayingNotifier.dispose();
    super.dispose();

    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    _audioPlayer = AudioPlayer();

    // Listen to player state changes
    _audioPlayer.playerStateStream.listen((state) {
      _isPlayingNotifier.value = state.playing;

      // Reset when playback completes
      if (state.processingState == ProcessingState.completed) {
        _isPlayingNotifier.value = false;
      }
    });
  }

  Future<void> _requestAudioPermission() async {
    _startRecognition();
  }

  Future<void> _toggleAudioPlayback() async {
    try {
      final audioFile = File(
          '/data/user/0/com.prayag.phonetic_speech_recognizer_example/cache/vosk_recording.wav');

      if (!await audioFile.exists()) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Audio file not found')),
        );
        return;
      }

      if (_audioPlayer.playing) {
        await _audioPlayer.stop();
      } else {
        await _audioPlayer.setFilePath(audioFile.path);
        await _audioPlayer.play();
      }
    } catch (e) {
      print('Error playing audio: $e');
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error playing audio: $e')),
      );
    }
  }

  void stopRecognition() {
    PhoneticSpeechRecognizer.stopRecognition();
    _timer?.cancel();
    subscription?.cancel();
    _ticker?.dispose();

    _isListeningNotifier.value = false;
    _progressNotifier.value = 1.0;
    _partialTextNotifier.value = "";
  }

  // NEW: Method to display detected answer for 3 seconds
  void _showDetectedAnswerForDuration(String detectedText) {
    _detectedAnswerNotifier.value = detectedText;
    _showDetectedAnswerNotifier.value = true;

    // Cancel any existing timer
    _answerDisplayTimer?.cancel();

    // Hide the detected answer after 3 seconds and then proceed to next sentence
    _answerDisplayTimer = Timer(Duration(seconds: 3), () {
      _showDetectedAnswerNotifier.value = false;
      _detectedAnswerNotifier.value = '';

      // Check answer and proceed to next sentence
      _checkAnswerAndProceedToNext(detectedText);
    });
  }

  // NEW: Method to check answer and proceed to next sentence
  void _checkAnswerAndProceedToNext(String detectedText) {
    bool isCorrect = false;

    print(
        "checking the answer:::: ${detectedText.toLowerCase()} ::: ${_randomTextNotifier.value.toLowerCase()}");
    if (detectedText.toLowerCase() == _randomTextNotifier.value.toLowerCase()) {
      print("this is correct");
      _generateRandomText();
    }

    if (_selectedTypeNotifier.value == RecognitionType.koreanNumbers) {
      String insideBrackets = _randomNumberNotifier.value.substring(
          _randomNumberNotifier.value.indexOf('(') + 1,
          _randomNumberNotifier.value.indexOf(')'));
      isCorrect = insideBrackets.contains(_recognizedTextNotifier.value);
    } else {
      isCorrect = _recognizedTextNotifier.value.toLowerCase() ==
          _randomTextNotifier.value
              .replaceAll(RegExp(r"[^\w\s]"), "")
              .toLowerCase();
    }

    if (isCorrect) {
      _generateRandomText();
    } else {
      _recognizedTextNotifier.value = "Recognition failed";
      _confidenceNotifier.value = 0.0;
    }
  }

  void _listenForPartialResults() {
    subscription?.cancel();
    _ticker?.dispose();

    // Step 1: Capture stream data into a buffer
    subscription = recognizer.listenToStream().listen((data) {
      _latestPartialText = data;
    }, onError: (error) {
      if (kDebugMode) {
        log("Stream error: $error");
      }
    });

    // Step 2: Poll buffer at 30 FPS
    _ticker = Ticker((_) {
      if (_partialTextNotifier.value != _latestPartialText) {
        _partialTextNotifier.value = _latestPartialText;
      }
    });

    _ticker!.start();
  }

  Future<void> _startRecognition() async {
    PhoneticSpeechRecognizer.downloadProgressStream.listen((progress) {
      _modelDownloadProgressNotifier.value = progress.progress;
    });

    if (_isListeningNotifier.value) return;

    _isTextReceivedNotifier.value = false;
    _isListeningNotifier.value = true;
    _progressNotifier.value = 1.0;
    _partialTextNotifier.value = "";
    // reset confidence at the start of new recognition
    _confidenceNotifier.value = 0.0;
    _recognizedTextNotifier.value = "";

    _timer = Timer.periodic(const Duration(milliseconds: 100), (timer) {
      _progressNotifier.value -= (100 / _timeoutDuration);
      if (_progressNotifier.value <= 0) {
        timer.cancel();
        if (_isListeningNotifier.value) stopRecognition();
      }
    });

    PhoneticType phoneticType;
    String languageCode;
    String textToRecognize =
        _selectedTypeNotifier.value == RecognitionType.koreanNumbers
            ? _randomNumberNotifier.value
            : _randomTextNotifier.value;

    switch (_selectedTypeNotifier.value) {
      case RecognitionType.alphabets:
        phoneticType = PhoneticType.alphabet;
        languageCode = "en-US";
        break;
      case RecognitionType.paragraphMapping:
        phoneticType = PhoneticType.paragraphsMapping;
        languageCode = "en-GB";
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
            _randomNumberNotifier.value.indexOf(')'));
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

    bool sendKeyOnly =
        _selectedTypeNotifier.value == RecognitionType.alphabets ||
            _selectedTypeNotifier.value == RecognitionType.numbers ||
            _selectedTypeNotifier.value == RecognitionType.paragraphMapping;

    try {
      final result = await PhoneticSpeechRecognizer.recognize(
        languageCode: languageCode,
        type: phoneticType,
        timeout: _timeoutDuration,
        sentence: textToRecognize,
        sendKeyOnly: sendKeyOnly,
      );

      print("result:::::: $result");

      if (result.isNotEmpty) {
        final confidenceStr = result['confidence'] ?? 0.0;
        final recognizedValue = result['correctedPhrase']?.toString() ?? '';

        _recognizedTextNotifier.value = recognizedValue;
        _confidenceNotifier.value = confidenceStr;

        print(
            "recognizedValue:::::: ${_recognizedTextNotifier.value}, ${_randomTextNotifier.value}");
        print("_randomTextNotifier:::::: ");

        // NEW: Show detected answer for 3 seconds instead of immediately checking
        _showDetectedAnswerForDuration(recognizedValue);
      } else {
        // When sendKeyOnly is true, result is just string
        final recognizedValue = result?.toString() ?? "Recognition failed";
        _recognizedTextNotifier.value = recognizedValue;

        if (result != null && result.toString().isNotEmpty) {
          _confidenceNotifier.value = 1.0;
          // NEW: Show detected answer for 3 seconds
          _showDetectedAnswerForDuration(recognizedValue);
        } else {
          _confidenceNotifier.value = 0.0;
        }
      }

      _isTextReceivedNotifier.value = _recognizedTextNotifier.value.isNotEmpty;

      if (!_isRealTimeNotifier.value) stopRecognition();
    } catch (error) {
      _recognizedTextNotifier.value = "Error: $error";
      _isTextReceivedNotifier.value = false;
      _confidenceNotifier.value = 0.0;
      stopRecognition();
    }
  }

  void _generateRandomText() {
    switch (_selectedTypeNotifier.value) {
      case RecognitionType.alphabets:
        _isRealTimeNotifier.value = false;
        _randomTextNotifier.value = String.fromCharCode(
            65 + (DateTime.now().millisecondsSinceEpoch % 26));
        break;
      case RecognitionType.numbers:
        _isRealTimeNotifier.value = false;
        _randomTextNotifier.value =
            (DateTime.now().millisecondsSinceEpoch % 10).toString();
        break;
      case RecognitionType.koreanAlphabets:
        _isRealTimeNotifier.value = false;
        _randomTextNotifier.value = String.fromCharCode(
            0xAC00 + (DateTime.now().millisecondsSinceEpoch % 11172));
        break;
      case RecognitionType.japaneseAlphabet:
        _isRealTimeNotifier.value = false;
        _randomTextNotifier.value = String.fromCharCode(
            0x3040 + (DateTime.now().millisecondsSinceEpoch % 96));
        break;
      case RecognitionType.koreanNumber:
        _isRealTimeNotifier.value = false;
        _randomTextNotifier.value = String.fromCharCode(
            0x30A0 + (DateTime.now().millisecondsSinceEpoch % 96));
        break;
      case RecognitionType.allLanguageSupport:
        _isRealTimeNotifier.value = false;
        _randomTextNotifier.value = String.fromCharCode(
            0x3040 + (DateTime.now().millisecondsSinceEpoch % 96));
        break;
      case RecognitionType.koreanNumbers:
        _isRealTimeNotifier.value = false;
        _randomNumberNotifier.value =
            RandomSentenceGenerator.generateSerialKoreanNumber();
        break;
      case RecognitionType.paragraphMapping:
        _recognizedTextNotifier.value = "";
        _isRealTimeNotifier.value = true;
        // _randomTextNotifier.value = "I visited Bandipur, a small hill town. The streets were clean with old houses and stone paths. I walked around and saw beautiful views of the mountains. People were friendly and smiling. I ate local food and watched the sunset from the hill. Bandipur was peaceful and quiet.";
        _randomTextNotifier.value =
            "I went to Rara Lake in Mugu. It took a long time to reach, but it was worth it. The blue water of the lake was very clear and beautiful. The mountains around the lake made it look like a painting. I sat near the lake and felt very calm and happy.";
        break;
      default:
        _isRealTimeNotifier.value = false;
        _randomTextNotifier.value = RandomSentenceGenerator.generateSentence();
        break;
    }
  }

  Widget _buildHighlightedText() {
    return ValueListenableBuilder<String>(
      valueListenable: _partialTextNotifier,
      builder: (context, partialText, child) {
        return ValueListenableBuilder<String>(
          valueListenable: _recognizedTextNotifier,
          builder: (context, recognizedText, child) {
            return ValueListenableBuilder<String>(
              valueListenable: _randomTextNotifier,
              builder: (context, randomText, child) {
                return ValueListenableBuilder<RecognitionType>(
                  valueListenable: _selectedTypeNotifier,
                  builder: (context, selectedType, child) {
                    return ValueListenableBuilder<bool>(
                      valueListenable: _isListeningNotifier,
                      builder: (context, isListening, child) {
                        if (selectedType == RecognitionType.paragraphMapping &&
                            isListening) {
                          _newTextNotifier.value =
                              "$recognizedText $partialText";
                          log("Recognized Text: ${_newTextNotifier.value}");
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
                            callback: (
                                {correctPronouncationListLength,
                                errorPronouncationListLength,
                                errorWordsIndexesLength,
                                indexedSentenceCount}) {
                              // log("Correct Pronouncation List Length: $correctPronouncationListLength");
                              // log("Error Pronouncation List Length: $errorPronouncationListLength");
                              // log("Error Words Indexes Length: $errorWordsIndexesLength");
                              // log("Current sentence index: $indexedSentenceCount");
                            },
                          );
                        } else {
                          return recognizer.buildRealTimeHighlightedText(
                            randomText: randomText,
                            partialText: _newTextNotifier.value,
                            highlightCorrectColor: Color(0xFF00BC7D),
                            defaultTextColor: Colors.black,
                            highlightWrongColor: Colors.red,
                            isAutoScroll: false,
                            autoScrollSpeed: 0,
                            fontSize: 30,
                            lineSpace: 1.5,
                            endOfScreen: 300,
                            callback: (
                                {correctPronouncationListLength,
                                errorPronouncationListLength,
                                errorWordsIndexesLength,
                                indexedSentenceCount}) {
                              // log("Correct Pronouncation List Length:::::::::: $correctPronouncationListLength");
                              // log("Error Pronouncation List Length: $errorPronouncationListLength");
                              // log("Error Words Indexes Length: $errorWordsIndexesLength");
                              // log("Current sentence index: $indexedSentenceCount");
                            },
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
  }

  int getWordCount(String text) {
    return text
        .trim()
        .split(RegExp(r'\s+'))
        .where((word) => word.isNotEmpty)
        .length;
  }

  PreferredSizeWidget buildDownloadProgressBar(
      Stream<DownloadProgress> progressStream) {
    return PreferredSize(
      preferredSize:
          const Size.fromHeight(40.0), // enough height for text + bar
      child: StreamBuilder<DownloadProgress>(
        stream: progressStream,
        builder: (context, snapshot) {
          final progressValue = snapshot.data?.progress ?? 0;
          final progress = progressValue / 100;
          final isComplete = progressValue >= 100;

          if (isComplete) return const SizedBox.shrink();

          return Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            color: Colors.white, // background for better visibility
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  "${progressValue.toStringAsFixed(0)}%",
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w500,
                    color: Colors.black87,
                  ),
                ),
                const SizedBox(height: 6),
                LinearProgressIndicator(
                  value: snapshot.hasData ? progress : null,
                  minHeight: 6,
                  backgroundColor: Colors.grey[300],
                  valueColor: const AlwaysStoppedAnimation<Color>(Colors.blue),
                  borderRadius: BorderRadius.circular(10),
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _displayMistakes() {
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
            correctPronouncationList: recognizer.correctPronouncationList);
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    PhoneticSpeechRecognizer.downloadModelWithProgress();
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Speech Recognizer'),
          bottom: buildDownloadProgressBar(
              PhoneticSpeechRecognizer.downloadProgressStream),
          actions: [
            PopupMenuButton<RecognitionType>(
              onSelected: (RecognitionType type) {
                _selectedTypeNotifier.value = type;
                _generateRandomText();
              },
              itemBuilder: (BuildContext context) =>
                  <PopupMenuEntry<RecognitionType>>[
                const PopupMenuItem(
                    value: RecognitionType.alphabets, child: Text('Alphabets')),
                const PopupMenuItem(
                    value: RecognitionType.numbers, child: Text('Numbers')),
                const PopupMenuItem(
                    value: RecognitionType.koreanAlphabets,
                    child: Text('Korean Alphabets')),
                const PopupMenuItem(
                    value: RecognitionType.sentences, child: Text('Sentences')),
                const PopupMenuItem(
                    value: RecognitionType.japaneseAlphabet,
                    child: Text('Japanese (Alphabets)')),
                const PopupMenuItem(
                    value: RecognitionType.koreanNumbers,
                    child: Text('Korean (Numbers)')),
                const PopupMenuItem(
                    value: RecognitionType.allLanguageSupport,
                    child: Text('Japanese (Numbers)')),
                const PopupMenuItem(
                    value: RecognitionType.paragraphMapping,
                    child: Text('Paragraphs')),
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
                child: ValueListenableBuilder<double>(
                  valueListenable: _progressNotifier,
                  builder: (context, progress, child) {
                    return ValueListenableBuilder<bool>(
                      valueListenable: _isListeningNotifier,
                      builder: (context, isListening, child) {
                        return ValueListenableBuilder<bool>(
                          valueListenable: _isTextReceivedNotifier,
                          builder: (context, isTextReceived, child) {
                            return progress <= 0.001 ||
                                    (!isListening && isTextReceived)
                                ? _displayMistakes()
                                : _buildHighlightedText();
                          },
                        );
                      },
                    );
                  },
                ),
              ),

              // NEW: Display detected answer section
              ValueListenableBuilder<String>(
                valueListenable: _detectedAnswerNotifier,
                builder: (context, detectedAnswer, child) {
                  return ValueListenableBuilder<bool>(
                    valueListenable: _showDetectedAnswerNotifier,
                    builder: (context, showDetectedAnswer, child) {
                      return showDetectedAnswer && detectedAnswer.isNotEmpty
                          ? Container(
                              margin: EdgeInsets.symmetric(vertical: 10),
                              padding: EdgeInsets.all(16),
                              decoration: BoxDecoration(
                                color: Colors.green.withOpacity(0.1),
                                border:
                                    Border.all(color: Colors.green, width: 2),
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Column(
                                children: [
                                  Text(
                                    "Detected Answer:",
                                    style: TextStyle(
                                      fontSize: 16,
                                      fontWeight: FontWeight.bold,
                                      color: Colors.green[700],
                                    ),
                                  ),
                                  SizedBox(height: 8),
                                  Text(
                                    detectedAnswer,
                                    style: TextStyle(
                                      fontSize: 18,
                                      color: Colors.black87,
                                    ),
                                    textAlign: TextAlign.center,
                                  ),
                                ],
                              ),
                            )
                          : Container();
                    },
                  );
                },
              ),

              SizedBox(height: 10),
              ValueListenableBuilder<bool>(
                valueListenable: _isTextReceivedNotifier,
                builder: (context, isTextReceived, child) {
                  return ValueListenableBuilder<bool>(
                    valueListenable: _isListeningNotifier,
                    builder: (context, isListening, child) {
                      return isTextReceived
                          ? Container() // If _isTextReceived is true, show nothing
                          : Text(
                              isListening ? "Listening..." : "",
                              style: TextStyle(
                                  fontSize: 18,
                                  fontWeight: FontWeight.bold,
                                  color: Colors.blue),
                            );
                    },
                  );
                },
              ),
              const SizedBox(height: 20),

              // Row containing both microphone and play buttons
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // Microphone button
                  ValueListenableBuilder<bool>(
                    valueListenable: _isListeningNotifier,
                    builder: (context, isListening, child) {
                      return ValueListenableBuilder<bool>(
                        valueListenable: _isTextReceivedNotifier,
                        builder: (context, isTextReceived, child) {
                          return GestureDetector(
                            onTapDown: (_) => _requestAudioPermission(),
                            onLongPressEnd: (_) {
                              if (_isRealTimeNotifier.value) {
                                log("error words list: ${recognizer.errorWordsIndexes}");
                                stopRecognition(); // Stop recognition immediately if _isRealTime is true
                              } else {
                                isTextReceived
                                    ? stopRecognition()
                                    : log("Still analyzing");
                              }
                            },
                            child: Container(
                              padding: EdgeInsets.all(16),
                              decoration: BoxDecoration(
                                color: isListening ? Colors.red : Colors.blue,
                                shape: BoxShape.circle,
                              ),
                              child: Icon(Icons.mic,
                                  color: Colors.white, size: 32),
                            ),
                          );
                        },
                      );
                    },
                  ),

                  SizedBox(width: 20), // Space between buttons

                  // Play button
                  ValueListenableBuilder<bool>(
                    valueListenable: _isPlayingNotifier,
                    builder: (context, isPlaying, child) {
                      return GestureDetector(
                        onTap: () => _toggleAudioPlayback(),
                        child: Container(
                          padding: EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            color: isPlaying ? Colors.orange : Colors.green,
                            shape: BoxShape.circle,
                          ),
                          child: Icon(
                            isPlaying ? Icons.stop : Icons.play_arrow,
                            color: Colors.white,
                            size: 32,
                          ),
                        ),
                      );
                    },
                  ),
                ],
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
