import 'package:flutter/material.dart';
import 'dart:async';
import 'package:flutter/services.dart';
import 'package:phonetic_speech_recognizer/phonetic_speech_recognizer.dart';

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

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _startRecognition() async {
    if (_isListening) return;

    setState(() {
      _isListening = true;
      _progress = 1.0;
      _recognizedText = "Listening...";
    });

    if (_timeoutDuration > 0) {
      _timer = Timer.periodic(const Duration(milliseconds: 100), (timer) {
        setState(() {
          _progress -= (100 / _timeoutDuration);
          if (_progress <= 0) timer.cancel();
        });
      });
    }

    try {
      final result = await PhoneticSpeechRecognizer.recognize(
        type: PhoneticType.koreanAlphabet,
        timeout: _timeoutDuration,
      );

      setState(() {
        _recognizedText = result ?? "Recognition failed";
      });
    } on PlatformException catch (e) {
      setState(() {
        _recognizedText = e.code == 'TIMEOUT'
            ? "Timeout: No speech detected"
            : "Error: ${e.message}";
      });
    } finally {
      _timer?.cancel();
      setState(() {
        _isListening = false;
        _progress = 1.0;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Speech Recognizer'),
        ),
        body: Padding(
          padding: const EdgeInsets.all(20.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              TextField(
                enabled: !_isListening,
                keyboardType: TextInputType.number,
                decoration: InputDecoration(
                  labelText: 'Timeout (ms)',
                  border: const OutlineInputBorder(),
                  suffixIcon: IconButton(
                    icon: const Icon(Icons.info),
                    onPressed: () => showInfoDialog(context),
                  ),
                ),
                onChanged: (value) => setState(() {
                  _timeoutDuration = int.tryParse(value) ?? 5000;
                }),
              ),
              const SizedBox(height: 30),
              ElevatedButton(
                onPressed: _isListening ? null : _startRecognition,
                style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(horizontal: 30, vertical: 15),
                ),
                child: Text(
                  _isListening ? 'Processing...' : 'Start Recognition',
                  style: const TextStyle(fontSize: 18),
                ),
              ),
              const SizedBox(height: 30),
              AnimatedSwitcher(
                duration: const Duration(milliseconds: 300),
                child: _buildMicIndicator(),
              ),
              const SizedBox(height: 20),
              LinearProgressIndicator(
                value: _progress,
                backgroundColor: Colors.grey[300],
                valueColor: AlwaysStoppedAnimation<Color>(
                    _isListening ? Colors.blue : Colors.transparent
                ),
              ),
              const SizedBox(height: 30),
              Text(
                _recognizedText,
                style: TextStyle(
                  fontSize: 20,
                  color: _recognizedText.startsWith('Error:')
                      ? Colors.red
                      : Colors.black,
                ),
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildMicIndicator() {
    return Stack(
      alignment: Alignment.center,
      children: [
        if (_isListening && _timeoutDuration > 0)
          SizedBox(
            width: 100,
            height: 100,
            child: CircularProgressIndicator(
              value: _progress,
              strokeWidth: 4,
              backgroundColor: Colors.grey[300],
              valueColor: const AlwaysStoppedAnimation<Color>(Colors.blue),
            ),
          ),
        Icon(
          _isListening ? Icons.mic : Icons.mic_none,
          size: 48,
          color: _isListening ? Colors.blue : Colors.grey,
        ),
      ],
    );
  }

  void showInfoDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('How to use'),
        content: const Text(
          '1. Enter timeout duration in milliseconds\n'
              '2. Click Start Recognition\n'
              '3. Speak clearly into your microphone\n'
              '4. Wait for results or timeout\n'
              '5. Try again with different timeout values',
        ),
        actions: [
          TextButton(
            child: const Text('OK'),
            onPressed: () => Navigator.pop(context),
          ),
        ],
      ),
    );
  }
}