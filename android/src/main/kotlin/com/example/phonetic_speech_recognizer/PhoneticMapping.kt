package com.example.phonetic_speech_recognizer

object PhoneticMapping {
    // Hiragana to Nepali and English mapping
    val phoneticHiraganaToNepaliAndEnglishMapping = mapOf(
        "あ" to listOf("a", "अ", "आ"),
        "い" to listOf("e", "इ", "ई"),
        "う" to listOf("u", "उ", "ऊ"),
        "え" to listOf("a", "ए", "एई"),
        "お" to listOf("o", "ओ", "औ", "ho"),

        "か" to listOf("ka", "का", "क"),
        "き" to listOf("ki", "कि", "की"),
        "く" to listOf("ku", "कु", "कू"),
        "け" to listOf("ke", "के", "के"),
        "こ" to listOf("ko", "को", "कौ"),

        "さ" to listOf("sa", "सा", "स", "शाह"),
        "し" to listOf("shi", "शि", "शी", "c",  "C", "see", "SEE", "सी"),
        "す" to listOf("su", "सु", "सू", "shu"),
        "せ" to listOf("se", "से", "से"),
        "そ" to listOf("so", "सो", "सो"),

        "た" to listOf("ta", "ता", "त"),
        "ち" to listOf("chi", "चि", "ची", "CHE"),
        "つ" to listOf("tsu", "तु", "तू", "चु", "छु"),
        "て" to listOf("te", "ते", "ते", "त्यही"),
        "と" to listOf("to", "तो", "तो", "त्यो"),

        "な" to listOf("na", "ना", "न"),
        "に" to listOf("ni", "नि", "नी"),
        "ぬ" to listOf("nu", "नु", "नू"),
        "ね" to listOf("ne", "ने", "ने"),
        "の" to listOf("no", "नो", "नो"),

        "は" to listOf("ha", "हा", "ह"),
        "ひ" to listOf("hi", "हि", "ही", "यी"),
        "ふ" to listOf("fu", "फु", "फू", "पु"),
        "へ" to listOf("he", "हे", "हे"),
        "ほ" to listOf("ho", "हो", "हो"),

        "ま" to listOf("ma", "मा", "म"),
        "み" to listOf("mi", "मी", "मी", "me"),
        "む" to listOf("mu", "मु", "मू"),
        "め" to listOf("main", "मे", "मे"),
        "も" to listOf("mo", "मो", "मो"),

        "や" to listOf("ya", "या", "य"),
        "ゆ" to listOf("yu", "यु", "यू", "you", "u"),
        "よ" to listOf("yo", "यो", "यो"),

        "ら" to listOf("ra", "रा", "र"),
        "り" to listOf("ri", "री", "री"),
        "る" to listOf("ru", "रु", "रू"),
        "れ" to listOf("re", "रे", "रे","हरे", "RAY"),
        "ろ" to listOf("ro", "रो", "रो", "ROW"),

        "わ" to listOf("wa", "वा", "व"),
        "を" to listOf("wo", "वो", "वो", "BOW", "भो"),
        "ん" to listOf("n", "न्", "ं", "AND")
    )

    // Katakana to Nepali and English mapping
    val phoneticKatakanaToNepaliAndEnglishMapping = mapOf(
        "ア" to listOf("a", "अ", "आ"),
        "イ" to listOf("e", "इ", "ई"),
        "ウ" to listOf("u", "उ", "ऊ"),
        "エ" to listOf("ae", "ए", "एई", "a"),
        "オ" to listOf("o", "ओ", "औ"),

        "カ" to listOf("ka", "का", "क"),
        "キ" to listOf("ki", "कि", "की"),
        "ク" to listOf("ku", "कु", "कू"),
        "ケ" to listOf("ke", "के", "के"),
        "コ" to listOf("ko", "को", "कौ"),

        "サ" to listOf("sa", "सा", "स", "शाह"),
        "シ" to listOf("shi", "शि", "शी", "c",  "C", "see", "SEE", "सी"),
        "ス" to listOf("su", "सु", "सू"),
        "セ" to listOf("se", "से", "से"),
        "ソ" to listOf("so", "सो", "सो"),

        "タ" to listOf("ta", "ता", "त"),
        "チ" to listOf("chi", "चि", "ची", "CHE"),
        "ツ" to listOf("tsu", "तु", "तू", "छु", "JOHN", "त्सु", "चु"),
        "テ" to listOf("te", "ते", "ते", "त्यही"),
        "ト" to listOf("to", "तो", "तो", "त्यो"),

        "ナ" to listOf("na", "ना", "न"),
        "ニ" to listOf("ni", "नि", "नी"),
        "ヌ" to listOf("nu", "नु", "नू"),
        "ネ" to listOf("ne", "ने", "ने"),
        "ノ" to listOf("no", "नो", "नो"),

        "ハ" to listOf("ha", "हा", "ह"),
        "ヒ" to listOf("hi", "हि", "ही", "यी"),
        "フ" to listOf("fu", "फु", "फू", "पु"),
        "ヘ" to listOf("he", "हे", "हे"),
        "ホ" to listOf("ho", "हो", "हो"),

        "マ" to listOf("ma", "मा", "म"),
        "ミ" to listOf("mi", "मी", "मी", "me"),
        "ム" to listOf("mu", "मु", "मू"),
        "メ" to listOf("मे", "मे", "main"),
        "モ" to listOf("mo", "मो", "मो"),

        "ヤ" to listOf("ya", "या", "य"),
        "ユ" to listOf("yu", "यु", "यू", "YOU", "u"),
        "ヨ" to listOf("yo", "यो", "यो"),

        "ラ" to listOf("ra", "रा", "र"),
        "リ" to listOf("ri", "री", "री"),
        "ル" to listOf("ru", "रु", "रू"),
        "レ" to listOf("re", "रे", "रे", "हरे", "RAY"),
        "ロ" to listOf("ro", "रो", "रो", "ROW"),

        "ワ" to listOf("wa", "वा", "व"),
        "ヲ" to listOf("wo", "वो", "वो", "BOW", "भो"),
        "ン" to listOf("n", "ं", "ं", "AND")
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