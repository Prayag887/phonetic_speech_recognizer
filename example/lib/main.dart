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
  String _recognizedText = "Recognition not started";

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Initialize platform
  Future<void> initPlatformState() async {
    String? platformVersion;
    try {
      platformVersion = await PhoneticSpeechRecognizer.getPlatformVersion();
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    // Update state only if the widget is still mounted
    if (!mounted) return;

  }

  // Call this to recognize alphabet speech
  Future<void> _recognizeAlphabet() async {
    final result = await PhoneticSpeechRecognizer.recognize(
      type: PhoneticType.alphabet,
    );
    setState(() {
      _recognizedText = result ?? "Recognition failed";
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Phonetic Speech Recognizer App'),
        ),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: <Widget>[
              ElevatedButton(
                onPressed: _recognizeAlphabet,
                child: const Text('Recognize Alphabet'),
              ),
              const SizedBox(height: 20),
              Text('Recognized Text: $_recognizedText'),
            ],
          ),
        ),
      ),
    );
  }
}