# phonetic_speech_recognizer
## Works for android projects only, iOS support will be provided later

A new Flutter plugin project.

## Getting Started

## For Sentences:

### If you are saying a sentence or words for the speech recognizer to recognize, phonetic type needs to be wordsOrSentence. sentence parameter is optional, but if you are doing something like recognizing the sentence or matching it with something that will be displayed in the screen then it will give you more accurate result.
    final result = await PhoneticSpeechRecognizer.recognize(
        languageCode: "en-US",
        type: PhoneticType.wordsOrSentence,
        timeout: _timeoutDuration,
        sentence: randomSentence
    );


## For Alphabets

### If you are trying to acheive the accuracy to the level of alphabets, then no need to sent the language code or sentence.
### This will work best for english alphabets.
    final result = await PhoneticSpeechRecognizer.recognize(
        type: PhoneticType.alphabet,
        timeout: _timeoutDuration,
    );

## For Korean alphabets

### If you are trying to get the korean alphabets, then no need to sent the language code or sentence.
### This will work best for korean alphabets.
    final result = await PhoneticSpeechRecognizer.recognize(
        type: PhoneticType.koreanAlphabet,
        timeout: _timeoutDuration,
    );


## For Numbers

### If you are trying to get the numbers, then no need to sent the language code or sentence.
    final result = await PhoneticSpeechRecognizer.recognize(
        type: PhoneticType.number,
        timeout: _timeoutDuration,
    );


## For Japanese Alphabets

### If you are trying to get the numbers, then no need to sent the language code or sentence.
    final result = await PhoneticSpeechRecognizer.recognize(
        type: PhoneticType.katakanaJapanese,
        timeout: _timeoutDuration,
    );

    final result = await PhoneticSpeechRecognizer.recognize(
        type: PhoneticType.hiraganaJapanese,
        timeout: _timeoutDuration,
    );


## Get the status of microphone (if its listening or not)
    PhoneticSpeechRecognizer.isListening();

## For all language support
### If you are trying to get the different language not given above, then you can get that particular language support with this
    final result = await PhoneticSpeechRecognizer.recognize(
        type: PhoneticType.allLanguageSupport,
        languageCode = "ja-JP", //send different language code here as needed
        timeout: _timeoutDuration,
    );