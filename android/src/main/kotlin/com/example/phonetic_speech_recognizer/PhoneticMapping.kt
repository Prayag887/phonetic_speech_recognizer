package com.example.phonetic_speech_recognizer

object PhoneticMapping {
    // Japanese alphabet mapping
    val phoneticJapaneseAlphabetMapping = mapOf(
        "あ, ア" to listOf("अ", "आ"),
        "い, イ" to listOf("e", "इ", "ई"),
        "う, ウ" to listOf("u", "उ", "ऊ"),
        "え, エ" to listOf("a", "ए", "एई"),
        "お, オ" to listOf("o", "ओ", "औ", "ho"),

        "か, カ" to listOf("ka", "का", "क"),
        "き, キ" to listOf("ki", "कि", "की"),
        "く, ク" to listOf("ku", "कु", "कू", "koo"),
        "け, ケ" to listOf("ke", "के", "के","k"),
        "こ, コ" to listOf("ko", "को", "कौ"),

        "さ, サ" to listOf("sa", "सा", "स", "शाह"),
        "し, シ" to listOf("shi", "शि", "शी", "c",  "C", "see", "SEE", "सी"),
        "す, ス" to listOf("su", "सु", "सू", "shu", "सुप"),
        "せ, セ" to listOf("se", "से", "से", "say"),
        "そ, ソ" to listOf("so", "सो", "सो", "show", "sow"),

        "た, タ" to listOf("taa", "ता", "त"),
        "ち, チ" to listOf("chi", "चि", "ची", "CHE"),
//        "つ, ツ" to listOf("tsu", "तु", "तू", "चु", "छु"),
        "て, テ" to listOf("TA", "ta","te", "ते", "ते", "त्यही", "teah", "TEAH"),
        "と, ト" to listOf("to", "तो", "तो", "त्यो"),

        "な, ナ" to listOf("na", "ना", "न"),
        "に, ニ" to listOf("ni", "नि", "नी"),
        "ぬ, ヌ, の, ノ" to listOf("nu", "नु", "नू", "no", "नो", "नो"), // nu and no
        "ね, ネ" to listOf("ne", "ने", "ने", "nei"),

        "は, ハ" to listOf("ha", "हा", "ह", "हाँ"),
        "ひ, ヒ" to listOf("hi", "हि", "ही", "यी", "he"),
        "ふ, フ" to listOf("fu", "फु", "फू", "पु", "फ", "swoo", "fuoh"),
        "へ, ヘ" to listOf("he", "हे", "हे"),
        "ほ, ホ, を, ヲ" to listOf("ho", "हो", "हो", "wo", "वो", "वो", "BOW", "भो", "o"),

        "ま, マ" to listOf("ma", "मा", "maa"),
        "み, ミ" to listOf("mi", "मी", "मी", "me"),
        "む, ム" to listOf("mu", "मु", "मू", "म"),
        "め, メ" to listOf("main", "मे", "मे", "may"),
        "も, モ" to listOf("mo", "मो", "मो", "म"),

        "や, ヤ" to listOf("ya", "या", "य"),
        "ゆ, ユ" to listOf("yu", "यु", "यू", "you", "u"),
        "よ, ヨ" to listOf("yo", "यो", "यो"),
        "っ, ッ" to listOf("tsu", "त्सु", "चु", "चू", "छु", "छू", "choo"),

        "ら, ラ" to listOf("ra", "रा", "र"),
        "り, リ" to listOf("ri", "री", "री", "रि", "हरि", "rin"),
        "る, ル" to listOf("ru", "रु", "रू"),
        "れ, レ" to listOf("re", "रे", "रे","हरे", "RAY"),
        "ろ, ロ" to listOf("ro", "रो", "रो", "ROW"),

        "わ, ワ" to listOf("wa", "वा", "व"),
        "ん, ン" to listOf("n", "न्", "ं", "AND", "यान", "yen", "yang"), //mistake

        "が, ガ" to listOf("ga", "गा", "ग", "घा", "घ", "gha"),
        "ぎ, ギ" to listOf("gi", "गि", "गी", "घि", "घी", "ghee", "ghi"),
        "ぐ, グ" to listOf("gu", "गु", "गू", "घु", "घू", "ghu", "goo"),
        "げ, ゲ" to listOf("ge", "गे", "गे", "घे", "ghe", "gay"),
        "ご, ゴ" to listOf("go", "गो", "गो", "घो", "gho"),

        "ざ, ザ" to listOf("za", "जा", "ज", "झा", "झ"),
        "じ, ジ" to listOf("ji", "जि", "जी", "झि", "झी", "G"),
        "ぜ, ゼ" to listOf("ze", "जे", "जे", "झे", "jay", "j"),
        "ぞ, ゾ, ず, ズ" to listOf("zo", "जो", "जो", "झो", "zu", "जु", "जू", "झु", "झू", "zoo", "ju", "joo", "joe"),

        "だ, ダ" to listOf("da", "दा", "द", "the", "धा", "ध", "dha"),
        "ぢ, ヂ" to listOf("ji", "दि", "दी", "धि", "धी", "de", "जि", "जी"),
        "づ, ヅ" to listOf("du", "दु", "दू", "धु", "धू"),
        "で, デ" to listOf("दे", "दे", "धे"),
        "ど, ド" to listOf("do", "दो", "दो", "धो"),

        "ば, バ" to listOf("ba", "बा", "ब", "भा", "भ"),
        "び, ビ" to listOf("bi", "बि", "बी", "भि", "भी", "b", "be", "v"),
        "ぶ, ブ" to listOf("bu", "बु", "बू", "भु", "भू", "भूक", "book", "boo"),
        "べ, ベ" to listOf("be", "बे", "बे", "भे", "bay"),
        "ぼ, ボ" to listOf("bo", "बो", "बो", "भो", "bow"),

        "ぱ, パ" to listOf("pa", "पा", "प", "फा", "फ"),
        "ぴ, ピ" to listOf("pi", "पि", "पी", "फि", "फी", "p", "pee"),
        "ぷ, プ" to listOf("pu", "पु", "पू", "फु", "फू"),
        "ぺ, ペ" to listOf("pe", "पे", "पे", "फे", "pay"),
        "ぽ, ポ" to listOf("po", "पो", "पो", "फो"),
    )


    // Nepali and English mapping
    val phoneticNepaliToEnglishMapping = mapOf(
        "A" to listOf("a", "ay", "ए", "अ", "एए", "हे"),
        "B" to listOf("b", "bee", "be", "बी", "बि", "ब्बी"),
        "C" to listOf("c", "see", "sea", "si", "सी", "सि", "सि।"),
        "D" to listOf("d", "dee", "de", "डी", "ढि", "दि", "डि", "डि।", "ढी", "thie", "the"),
        "E" to listOf("e", "ee", "ie", "eee", "eh", "eeew", "eeeeh", "ई", "इ", "यी", "यि"),
        "F" to listOf("f", "ef", "eff", "एफ", "एफ्फ", "एभ", "apps", "app"),
        "G" to listOf("g", "gee", "je", "gi", "ji", "जी", "जी।", "जि"),
        "H" to listOf(
            "h",
            "aitch",
            "age",
            "एच",
            "ach",
            "aths",
            "hatch",
            "एचच",
            "एच्स",
            "एच।",
            "याच",
            "aic",
            "यच",
            "has"
        ),
        "I" to listOf("i", "eye", "ai", "आई", "आइ"),
        "J" to listOf("j", "jay", "जे", "जेई", "जे।", "jai"),
        "K" to listOf("k", "kay", "ke", "ke ke", "okay", "के", "के के"),
        "L" to listOf("l", "el", "ell", "yell", "एल", "ल", "यल", "yel", "heal", "all"),
        "M" to listOf("m", "em", "am", "एम", "यम"),
        "N" to listOf("n", "and", "en", "an", "एन", "एनन", "यन", "ययन", "yan"),
        "O" to listOf("o", "oh", "ow", "ओ", "हो", "हो।", "ओओ", "ओह", "उ", "ऊ"),
        "P" to listOf("p", "pee", "pe", "पी", "पि"),
        "Q" to listOf("q", "cue", "queue", "qu", "kyon", "kyun", "क्यू", "क्यु"),
        "R" to listOf("r", "are", "ar", "आर", "अर"),
        "S" to listOf("s", "es", "ass", "एस", "यस्", "यस", "ash"),
        "T" to listOf("t", "tee", "tea", "टी", "टि", "ति", "ती"),
        "U" to listOf("u", "you", "yu", "यु", "यू"),
        "V" to listOf("v", "vee", "ve", "भि", "भि।"),
        "W" to listOf(
            "w",
            "double u",
            "double you",
            "dablu",
            "the blue",
            "डब्लु",
            "डब्ल्यू",
            "डब्बलु"
        ),
        "X" to listOf("x", "ex", "ax", "axe", "एक्स", "एक्स्स"),
        "Y" to listOf("y", "why", "wai", "वाई", "य", "वाइ"),
        "Z" to listOf(
            "z",
            "zee",
            "zed",
            "jet",
            "जेड",
            "जेट",
            "जेडड",
            "जेठ",
            "जेत",
            "zet",
            "चेत",
            "jatt"
        ),
    )

    val phoneticNepaliMapping = mapOf(
        "क्ष" to listOf("छय", "क्ष"),
        "ङ" to listOf("ङ", "अन्न", "अं", "अंग")
    )

    // Hindi to English mapping for offline capability
    val phoneticNumbersMapping = mapOf(
        //offline alphabet mapping
        "A" to listOf("a", "ay", "ए", "अ", "एए", "हे"),
        "B" to listOf("b", "bee", "be", "बी", "बि", "ब्बी"),
        "C" to listOf("c", "see", "sea", "si", "सी", "सि", "सि।"),
        "D" to listOf("d", "dee", "de", "डी", "ढि", "दि", "डि", "डि।", "ढी", "thie", "the"),
        "E" to listOf("e", "ee", "ie", "eee", "eh", "eeew", "eeeeh", "ई", "इ", "यी", "यि"),
        "F" to listOf("f", "ef", "eff", "एफ", "एफ्फ", "एभ", "apps", "एप्स"),
        "G" to listOf("g", "gee", "je", "gi", "ji", "जी", "जी।", "जि"),
        "H" to listOf(
            "h",
            "aitch",
            "age",
            "एच",
            "ach",
            "aths",
            "hatch",
            "एचच",
            "एच्स",
            "एच।",
            "याच",
            "aic",
            "यच",
            "has",
            "हा"
        ),
        "I" to listOf("i", "eye", "ai", "आई", "आइ"),
        "J" to listOf("j", "jay", "जे", "जेई", "जे।", "jai", "जय"),
        "K" to listOf("k", "kay", "ke", "ke ke", "okay", "के", "के के"),
        "L" to listOf("l", "el", "ell", "yell", "एल", "ल", "यल", "yel", "heal", "all"),
        "M" to listOf("m", "em", "am", "एम", "यम"),
        "N" to listOf("n", "and", "en", "an", "एन", "एनन", "यन", "ययन", "yan"),
        "O" to listOf("o", "oh", "ow", "ओ", "हो", "हो।", "ओओ", "ओह", "उ", "ऊ"),
        "P" to listOf("p", "pee", "pe", "पी", "पि"),
        "Q" to listOf("q", "cue", "queue", "qu", "kyon", "kyun", "क्यू", "क्यु"),
        "R" to listOf("r", "are", "ar", "आर", "अर"),
        "S" to listOf("s", "es", "ass", "एस", "यस्", "यस", "ash"),
        "T" to listOf("t", "tee", "tea", "टी", "टि", "ति", "ती"),
        "U" to listOf("u", "you", "yu", "यु", "यू"),
        "V" to listOf("v", "vee", "ve", "भि", "भि।"),
        "W" to listOf(
            "w",
            "double u",
            "double you",
            "dablu",
            "the blue",
            "डब्लु",
            "डब्ल्यू",
            "डब्बलु",
            "डब्लू",
            "ब्लू"
        ),
        "X" to listOf("x", "ex", "ax", "axe", "एक्स", "एक्स्स"),
        "Y" to listOf("y", "why", "wai", "वाई", "य", "वाइ"),
        "Z" to listOf(
            "z",
            "zee",
            "zed",
            "jet",
            "जेड",
            "जेट",
            "जेडड",
            "जेठ",
            "जेत",
            "zet",
            "चेत"
        ),

        //numbers mapping
        "1" to listOf("one", "1", "वान", "waan", "wan", "won", "वन"),
        "2" to listOf(
            "two",
            "2",
            "टू",
            "too",
            "tu",
            "तु",
            "to",
            "दो",
            "तू"
        ),
        "3" to listOf("three", "3", "थ्री", "three", "tree", "ट्री"),
        "4" to listOf("four", "4", "फोर", "for", "फो", "पोर", "फोहोर", "पोहोर", "पो"),
        "5" to listOf("five", "5", "फाइव", "five", "फाइभ"),
        "6" to listOf("six", "6", "सिक्स", "six", "shiks", "s**"),
        "7" to listOf("seven", "7", "सेवेन", "seven", "सेभेन", "सेवेन", "saven"),
        "8" to listOf(
            "eight",
            "8",
            "एट",
            "ate",
            "एड",
            "at",
            "a8",
            "hit",
            "hate",
            "हेट",
            "आईटी"
        ),
        "9" to listOf("nine", "9", "नाइन", "nine"),
        "10" to listOf("ten", "10", "टेन", "ten"),
    )

    // Nepali to Korean mapping
    val phoneticKoreanMapping = mapOf(
        "ㄱ" to listOf("ga", "kha", "ग", "का", "ख"),
        "ㄴ" to listOf("na", "न", "ना"),
        "ㄷ" to listOf("द्", "थ्", "da", "the", "डी", "दो", "द"),
        "ㄹ" to listOf("र्", "ल्", "ra", "la", "रा", "हा", "र", "ल"),
        "ㅁ" to listOf("म्", "ma", "मां", "म"),
        "ㅂ" to listOf("ब्", "फ्", "ba", "pha", "ब"),
        "ㅅ" to listOf("स", "sa", "सा", "स"),
        "ㅇ" to listOf("अ", "ङ", "A", "na", "अङ्", "un", "na", "न"),
        "ㅈ" to listOf("ज्", "छ्", "Ja", "chha", "जछ", "ज", "ja", "जा"),
        "ㅊ" to listOf("छ्", "Chha", "छ"),
        "ㅋ" to listOf("ख्", "क्", "Kha", "ka", "ख"),
        "ㅌ" to listOf("थ्", "Tha", "थ"),
        "ㅍ" to listOf("फ्", "Pha", "फ"),
        "ㅎ" to listOf("ह्", "ha", "ह"),
        "ㄲ" to listOf("क्", "ka", "क"),
        "ㄸ" to listOf("ta", "त"),
        "ㅃ" to listOf("प्", "pa", "प"),
        "ㅆ" to listOf("स", "sa", "सा", "स"),
        "ㅉ" to listOf("च्", "cha", "चा", "c", "च"),

//        vowels
        "ㅏ" to listOf("आ", "aa", "a"),
        "ㅑ" to listOf("या", "yaa"),
        "ㅓ" to listOf("अ", "a"),
        "ㅕ" to listOf("य", "ya"),
        "ㅗ" to listOf("ओ", "O", "o"),
        "ㅛ" to listOf("यो", "yo"),
        "ㅜ" to listOf("ऊ", "उ", "u", "wu"),
        "ㅠ" to listOf("यु", "yu", "you", "u"),
        "ㅡ" to listOf("ऊ", "उ", "u", "wu"),
        "ㅣ" to listOf("इ", "ee", "e", "ई"),
        "ㅐ" to listOf("ए", "a", "ये", "ye", "yae", "ऐ", "ae", "ei", "i", "अई", "ये"),
        "ㅔ" to listOf("ए", "a", "ये", "ye", "yae", "ऐ", "ae", "ei", "i", "अई", "ये"),
        "ㅒ" to listOf("ए", "a", "ये", "ye", "yae", "ऐ", "ae", "ei", "i", "अई", "ये"),
        "ㅖ" to listOf("ए", "a", "ये", "ye", "yae", "ऐ", "ae", "ei", "i", "अई", "ये"),
        "ㅟ" to listOf("वी", "wi", "wee", "उइ", "उई", "wi", "eui", "ui"),
        "ㅢ" to listOf("वी", "wi", "wee", "उइ", "उई", "wi", "eui", "ui"), // duita wi
        "ㅘ" to listOf("वा", "waa"),
        "ㅝ" to listOf("व", "wa"),
        "ㅚ" to listOf("वे", "we", "oe", "way", "वै", "we", "way", "week", "wei", "wai", "huawei"),
        "ㅙ" to listOf("वे", "we", "oe", "way", "वै", "we", "way", "week", "wei", "wai", "huawei"),
        "ㅞ" to listOf("वे", "we", "oe", "way", "वै", "we", "way", "week", "wei", "wai", "huawei")
    )
}