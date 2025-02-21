package com.example.phonetic_speech_recognizer

object PhoneticMapping {
    // Japanese alphabet mapping
    val phoneticJapaneseAlphabetMapping = mapOf(
        "あ, ア, あ, ア" to listOf("अ", "आ"),
        "い, イ, い, イ" to listOf("e", "इ", "ई"),
        "う, ウ, う, ウ" to listOf("u", "उ", "ऊ"),
        "え, エ, え, エ" to listOf("a", "ए", "एई"),
        "お, オ, お, オ" to listOf("o", "ओ", "औ", "ho"),

        "か, カ, か, カ" to listOf("ka", "का", "क"),
        "き, キ, き, キ" to listOf("ki", "कि", "की"),
        "く, ク, く, ク" to listOf("ku", "कु", "कू", "koo"),
        "け, ケ, け, ケ" to listOf("ke", "के", "के","k"),
        "こ, コ, こ, コ" to listOf("ko", "को", "कौ"),

        "さ, サ, さ, サ" to listOf("sa", "सा", "स", "शाह"),
        "し, シ, し, シ" to listOf("shi", "शि", "शी", "c",  "C", "see", "SEE", "सी"),
        "す, ス, す, ス" to listOf("su", "सु", "सू", "shu", "सुप"),
        "せ, セ, せ, セ" to listOf("se", "से", "से", "say"),
        "そ, ソ, そ, ソ" to listOf("so", "सो", "सो", "show", "sow"),

        "た, タ, た, タ" to listOf("taa", "ता", "त"),
        "ち, チ, ち, チ" to listOf("chi", "चि", "ची", "CHE"),
        "て, テ, て, テ" to listOf("TA", "ta","te", "ते", "ते", "त्यही", "teah", "TEAH"),
        "と, ト, と, ト" to listOf("to", "तो", "तो", "त्यो"),

        "な, ナ, な, ナ" to listOf("na", "ना", "न"),
        "に, ニ, に, ニ" to listOf("ni", "नि", "नी"),
        "ぬ, ヌ, の, ノ" to listOf("nu", "नु", "नू", "no", "नो", "नो"), // nu and no
        "ね, ネ, ね, ネ" to listOf("ne", "ने", "ने", "nei"),

        "は, ハ, は, ハ" to listOf("ha", "हा", "ह", "हाँ"),
        "ひ, ヒ, ひ, ヒ" to listOf("hi", "हि", "ही", "यी", "he"),
        "ふ, フ, ふ, フ" to listOf("fu", "फु", "फू", "पु", "फ", "swoo", "fuoh"),
        "へ, ヘ, へ, ヘ" to listOf("he", "हे", "हे"),
        "ほ, ホ, を, ヲ" to listOf("ho", "हो", "हो", "wo", "वो", "वो", "BOW", "भो", "o"),

        "ま, マ, ま, マ" to listOf("ma", "मा", "maa"),
        "み, ミ, み, ミ" to listOf("mi", "मी", "मि", "me"),
        "む, ム, む, ム" to listOf("mu", "मु", "मू", "म"),
        "め, メ, め, メ" to listOf("main", "मे", "may"),
        "も, モ, も, モ" to listOf("mo", "मो", "म"),

        "や, ヤ, や, ヤ" to listOf("ya", "या", "य"),
        "ゆ, ユ, ゆ, ユ" to listOf("yu", "यु", "यू", "you", "u"),
        "よ, ヨ, よ, ヨ" to listOf("yo", "यो", "यो"),
        "っ, ッ, つ, ツ" to listOf("tsu", "त्सु", "चु", "चू", "छु", "छू", "choo"),

        "ら, ラ, ら, ラ" to listOf("ra", "रा", "र"),
        "り, リ, り, リ" to listOf("ri", "री", "री", "रि", "हरि", "rin"),
        "る, ル, る, ル" to listOf("ru", "रु", "रू"),
        "れ, レ, れ, レ" to listOf("re", "रे", "रे","हरे", "RAY"),
        "ろ, ロ, ろ, ロ" to listOf("ro", "रो", "रो", "ROW"),

        "わ, ワ, わ, ワ" to listOf("wa", "वा", "व"),
        "ん, ン, ん, ン" to listOf("n", "न्", "ं", "AND", "यान", "yen", "yang"), //mistake

        "が, ガ, が, ガ" to listOf("ga", "गा", "ग", "घा", "घ", "gha"),
        "ぎ, ギ, ぎ, ギ" to listOf("gi", "गि", "गी", "घि", "घी", "ghee", "ghi"),
        "ぐ, グ, ぐ, グ" to listOf("gu", "गु", "गू", "घु", "घू", "ghu", "goo"),
        "げ, ゲ, げ, ゲ" to listOf("ge", "गे", "गे", "घे", "ghe", "gay"),
        "ご, ゴ, ご, ゴ" to listOf("go", "गो", "गो", "घो", "gho"),

        "ざ, ザ, ざ, ザ" to listOf("za", "जा", "ज", "झा", "झ"),
        "じ, ジ, じ, ジ" to listOf("ji", "जि", "जी", "झि", "झी", "G"),
        "ぜ, ゼ, ぜ, ゼ" to listOf("ze", "जे", "जे", "झे", "jay", "j"),
        "ぞ, ゾ, ず, ズ" to listOf("zo", "जो", "जो", "झो", "zu", "जु", "जू", "झु", "झू", "zoo", "ju", "joo", "joe"),

        "だ, ダ, だ, ダ" to listOf("da", "दा", "द", "the", "धा", "ध", "dha"),
        "ぢ, ヂ, ぢ, ヂ" to listOf("ji", "दि", "दी", "धि", "धी", "de", "जि", "जी"),
        "づ, ヅ, づ, ヅ" to listOf("du", "दु", "दू", "धु", "धू"),
        "で, デ, で, デ" to listOf("दे", "दे", "धे"),
        "ど, ド, ど, ド" to listOf("do", "दो", "दो", "धो"),

        "ば, バ, ば, バ" to listOf("ba", "बा", "ब", "भा", "भ"),
        "び, ビ, び, ビ" to listOf("bi", "बि", "बी", "भि", "भी", "b", "be", "v"),
        "ぶ, ブ, ぶ, ブ" to listOf("bu", "बु", "बू", "भु", "भू", "भूक", "book", "boo"),
        "べ, ベ, べ, ベ" to listOf("be", "बे", "बे", "भे", "bay"),
        "ぼ, ボ, ぼ, ボ" to listOf("bo", "बो", "बो", "भो", "bow"),

        "ぱ, パ, ぱ, パ" to listOf("pa", "पा", "प", "फा", "फ"),
        "ぴ, ピ, ぴ, ピ" to listOf("pi", "पि", "पी", "फि", "फी", "p", "pee"),
        "ぷ, プ, ぷ, プ" to listOf("pu", "पु", "पू", "फु", "फू"),
        "ぺ, ペ, ぺ, ペ" to listOf("pe", "पे", "पे", "फे", "pay"),
        "ぽ, ポ, ぽ, ポ" to listOf("po", "पो", "पो", "फो"),

        //new alphabets
        "きゃ, キャ, きゃ, キャ" to listOf("kya", "क्या", "क्य"),
        "きゅ, キュ, きゅ, キュ" to listOf("kyu", "क्यु", "क्यू"),
        "きょ, キョ, きょ, キョ" to listOf("kyo", "क्यो", "क्यो"),
        "しゅ, シュ, しゅ, シュ" to listOf("shu", "शु", "शू"),
        "しょ, ショ, しょ, ショ" to listOf("sho", "शो", "शो"),
        "ちゃ, チャ, ちゃ, チャ" to listOf("cha", "चा", "च"),
        "ちゅ, チュ, ちゅ, チュ" to listOf("chu", "चु", "चू"),
        "ちょ, チョ, ちょ, チョ" to listOf("cho", "चो", "चो"),
        "にゃ, ニャ, にゃ, ニャ" to listOf("nya", "न्या", "न्य"),
        "にゅ, ニュ, にゅ, ニュ" to listOf("nyu", "न्यु", "न्यू"),
        "にょ, ニョ, にょ, ニョ" to listOf("nyo", "न्यो", "न्यो"),
        "ひゃ, ヒャ, ひゃ, ヒャ" to listOf("hya", "ह्या", "ह्य"),
        "ひゅ, ヒュ, ひゅ, ヒュ" to listOf("hyu", "ह्यु", "ह्यू"),
        "ひょ, ヒョ, ひょ, ヒョ" to listOf("hyo", "ह्यो", "ह्यो"),
        "みゃ, ミャ, みゃ, ミャ" to listOf("mya", "म्या", "म्य"),
        "みゅ, ミュ, みゅ, ミュ" to listOf("myu", "म्यु", "म्यू"),
        "みょ, ミョ, みょ, ミョ" to listOf("myo", "म्यो", "म्यो"),
        "りゃ, リャ, りゃ, リャ" to listOf("rya", "र्या", "र्य"),
        "りゅ, リュ, りゅ, リュ" to listOf("ryu", "र्यु", "र्यू"),
        "りょ, リョ, りょ, リョ" to listOf("ryo", "र्यो", "र्यो"),
        "ぎゃ, ギャ, ぎゃ, ギャ" to listOf("gya", "ग्या", "ग्य"),
        "ぎゅ, ギュ, ぎゅ, ギュ" to listOf("gyu", "ग्यु", "ग्यू"),
        "ぎょ, ギョ, ぎょ, ギョ" to listOf("gyo", "ग्यो", "ग्यो"),
        "じゃ, ジャ, じゃ, ジャ" to listOf("ja", "जा", "ज"),
        "じゅ, ジュ, じゅ, ジュ" to listOf("ju", "जु", "जू"),
        "じょ, ジョ, じょ, ジョ" to listOf("jo", "जो", "जो"),
        "びゃ, ビャ, びゃ, ビャ" to listOf("bya", "ब्या", "ब्य"),
        "びゅ, ビュ, びゅ, ビュ" to listOf("byu", "ब्यु", "ब्यू"),
        "びょ, ビョ, びょ, ビョ" to listOf("byo", "ब्यो", "ब्यो"),
        "ぴゃ, ピャ, ぴゃ, ピャ" to listOf("pya", "प्या", "प्य"),
        "ぴゅ, ピュ, ぴゅ, ピュ" to listOf("pyu", "प्यु", "प्यू"),
        "ぴょ, ピョ, ぴょ, ピョ" to listOf("pyo", "प्यो", "प्यो")
    )

    // Nepali and English mapping
    val phoneticNepaliToEnglishMapping = mapOf(
        "A" to listOf("a",
            "aa",
            "ay",
            "ay ay",
            "हे",
            "ए",
            "अ",
            "अ अ",
            "अअ",
            "एए",
            "ए ए",
            "हेहे",
            "हे हे",
            "aa aa",
            "ay ay",
            "अ अ"
        ),

        "B" to listOf(
            "b",
            "bb",
            "bee",
            "bee bee",
            "be",
            "be be",
            "बी",
            "बीबी",
            "बी बी",
            "बि",
            "बिबि",
            "बि बि",
            "ब्बी",
            "ब्बी ब्बी",
            "bb bb",
            "bee bee"
        ),

        "C" to listOf(
            "c",
            "see",
            "sea",
            "si",
            "सी",
            "सीसी",
            "सी सी",
            "सि",
            "सिसि",
            "सि सि",
            "सि।",
            "see see",
            "sea sea",
            "si si"
        ),

        "D" to listOf(
            "d",
            "dee",
            "de",
            "डी",
            "डीडी",
            "डी डी",
            "ढि",
            "ढिढि",
            "ढि ढि",
            "दि",
            "दि दि",
            "दिदि",
            "डि",
            "डि डि",
            "डिडि",
            "डि।",
            "ढी",
            "ढी ढी",
            "ढीढी",
            "thie thie",
            "the the",
            "dee dee",
            "de de"
        ),

        "E" to listOf(
            "e",
            "ee",
            "ie",
            "eee",
            "eh",
            "eeew",
            "eeeeh",
            "ईई",
            "ई",
            "ई ई",
            "इ",
            "इइ",
            "इ इ",
            "यी",
            "यी यी",
            "यीयी",
            "यि",
            "यि यि",
            "यियि",
            "ee ee",
            "ie ie",
            "eee eee"
        ),

        "F" to listOf(
            "f",
            "ef",
            "eff",
            "एफ",
            "एफ एफ",
            "एफ्फ",
            "एफ्फ एफ्फ",
            "एभ",
            "एभ एभ",
            "apps",
            "app",
            "eff eff",
            "app app"
        ),

        "G" to listOf(
            "g",
            "gee",
            "je",
            "gi",
            "ji",
            "जी",
            "जी जी",
            "जी।",
            "जी। ",
            "जी। ",
            "जि",
            "जि जि",
            "jee jee",
            "gi gi",
            "ji ji"
        ),

        "H" to listOf(
            "h",
            "h h",
            "aitch",
            "aitch aitch",
            "age",
            "age age",
            "एच",
            "एच एच",
            "ach",
            "aths",
            "hatch",
            "एचच",
            "एचच एचच",
            "एच्स",
            "एच्स एच्स",
            "एच।",
            "एच। एच।",
            "याच",
            "याच याच",
            "aic",
            "यच",
            "यच यच",
            "has",
            "h h",
            "aitch aitch",
            "age age",
            "hatch hatch"
        ),

        "I" to listOf(
            "i",
            "i i",
            "eye",
            "ai",
            "आई",
            "आई आई",
            "आइ",
            "आइ आइ",
            "i i",
            "eye eye",
            "ai ai"
        ),

        "J" to listOf(
            "j",
            "j j",
            "jay",
            "जे",
            "जे जे",
            "जेई",
            "जेई जेई",
            "जे।",
            "जे। जे।",
            "jai",
            "jay jay",
            "jai jai"
        ),

        "K" to listOf(
            "k",
            "kay",
            "ke",
            "ke ke",
            "okay",
            "के",
            "के के",
            "kay kay",
            "ke ke"
        ),

        "L" to listOf(
            "l",
            "el",
            "ell",
            "yell",
            "एल",
            "एल एल",
            "ल",
            "यल",
            "यल यल",
            "yel",
            "yel yel",
            "heal",
            "heal heal",
            "all",
            "ell ell",
            "yell yell"
        ),

        "M" to listOf(
            "m",
            "em",
            "am",
            "एम",
            "एम एम",
            "एमएम",
            "यम",
            "यमयम",
            "यम यम",
            "em em",
            "am am"
        ),

        "N" to listOf(
            "n",
            "n n",
            "and",
            "and and",
            "en",
            "en en",
            "an",
            "an an",
            "एन",
            "एन एन",
            "एनन",
            "एनन एनन",
            "यन यन",
            "ययन ययन",
            "yan yan",
            "en en",
            "an an"
        ),

        "O" to listOf(
            "o",
            "o o",
            "oh",
            "ow",
            "ओ",
            "ओ ओ",
            "हो",
            "हो हो",
            "हो।",
            "हो। हो।",
            "ओओ",
            "ओ ओ",
            "ओह",
            "ओह ओह",
            "उ",
            "ऊ",
            "oh oh",
            "ow ow"
        ),

        "P" to listOf(
            "p",
            "pee",
            "pe",
            "पी",
            "पी पी",
            "पि",
            "पि पि",
            "pee pee",
            "pe pe"
        ),

        "Q" to listOf(
            "q",
            "q q",
            "cue",
            "cue cue",
            "queue",
            "queue queue",
            "qu",
            "kyon",
            "kyun",
            "क्यू",
            "क्यू क्यू",
            "क्यु",
            "क्यु क्यु",
            "queue queue",
            "kyon kyon"
        ),

        "R" to listOf(
            "r",
            "are",
            "ar",
            "आर",
            "आर आर",
            "अर",
            "अर अर",
            "are are",
            "ar ar"
        ),

        "S" to listOf(
            "yes",
            "s",
            "es",
            "ass",
            "एस",
            "एस एस",
            "यस्",
            "यस् यस्",
            "यस",
            "यस यस",
            "ash",
            "ash ash",
            "as",
            "as as",
            "ask",
            "ask ask",
            "yas",
            "yas yas",
            "ys",
            "ys ys",
            "yash",
            "yash yash",
            "s s",
            "ash ash"
        ),

        "T" to listOf(
            "t",
            "tee",
            "tea",
            "टी",
            "टी टी",
            "टीटी",
            "टि",
            "टि टि",
            "टिटि",
            "ति",
            "ति ति",
            "तिति",
            "ती",
            "तीती",
            "ती ती",
            "tee tee",
            "tea tea"
        ),

        "U" to listOf(
            "u",
            "you",
            "yu",
            "यु",
            "यु यु",
            "युयु",
            "यू",
            "यू यू",
            "यूयू",
            "you you",
            "yu yu"
        ),

        "V" to listOf(
            "v",
            "vee",
            "ve",
            "भि",
            "भि भि",
            "भिभि",
            "भि।भि।",
            "भि। भि।",
            "vee vee",
            "ve ve"
        ),

        "W" to listOf(
            "w",
            "double u",
            "double you",
            "dablu",
            "the blue",
            "डब्लु",
            "डब्लु डब्लु",
            "डब्लुडब्लु",
            "डब्ल्यू",
            "डब्ल्यू डब्ल्यू",
            "डब्ल्यूडब्ल्यू",
            "डब्बलु",
            "डब्बलु डब्बलु",
            "डब्बलुडब्बलु",
            "double u double u",
            "dablu dablu",
            "the blue the blue"
        ),

        "X" to listOf(
            "x",
            "ex",
            "ax",
            "axe",
            "एक्स",
            "एक्स एक्स",
            "एक्सएक्स",
            "एक्स्स",
            "एक्स्स एक्स्स",
            "एक्स्सएक्स्स",
            "ex ex",
            "axe axe"
        ),

        "Y" to listOf(
            "y",
            "why",
            "wai",
            "वाई",
            "य",
            "वाइ",
            "why why",
            "wai wai",
            "वाई वाई",
            "वाईवाई",
            "य य",
            "यय",
            "वाइ वाइ",
            "वाइवाइ",
        ),

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
            "jatt",
            "zee zee",
            "zed zed",
            "jet jet",
            "जेड जेड",
            "जेडजेड",
            "जेट जेट",
            "जेटजेट",
            "जेडड जेडड",
            "जेडडजेडड",
            "जेठ जेठ",
            "जेठजेठ",
            "जेत जेत",
            "जेतजेत",
            "zet zet",
            "चेत चेत",
            "चेतचेत",
            "jatt jatt"
        )
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
        "S" to listOf("yes", "s", "es", "ass", "एस", "यस्", "यस", "ash", "as", "ask", "yas", "ys", "yash"),
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
        "0" to listOf("zero", "jero", "0", "ज़ीरो", "जिरो", "zoro", "zeero", "jiro", "जीरो", "जेरो"),
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
        "6" to listOf("six", "6", "सिक्स", "six", "shiks", "s**", "सेक्स"),
        "7" to listOf("seven", "7", "सेवेन", "seven", "सेभेन", "सेवेन", "saven", "सावन", "सेवन"),
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
        "ㅢ" to listOf("वी", "wi", "wee", "उइ", "उई", "wi", "eui", "ui"),
        "ㅘ" to listOf("वा", "waa"),
        "ㅝ" to listOf("व", "wa"),
        "ㅚ" to listOf("वे", "we", "oe", "way", "वै", "we", "way", "week", "wei", "wai", "huawei"),
        "ㅙ" to listOf("वे", "we", "oe", "way", "वै", "we", "way", "week", "wei", "wai", "huawei"),
        "ㅞ" to listOf("वे", "we", "oe", "way", "वै", "we", "way", "week", "wei", "wai", "huawei")
    )


    // korean objects and numbers mappings
    val phoneticKoreanNumberMapping = mapOf(
        "1" to listOf("इल", "ill", "eel", "is", "하나", "hana", "आना", "हाना", "आना।", "खाना"),
        "2" to listOf("ई", "e", "둘", "dul", "थुल", "ठुल", "ठूल", "full", "फुल", "ful", "फूल", "ठुली", "ठूली", "ठुल", "ठूल", "ठूलो", "ठुलो", "thull"),
        "3" to listOf("साम", "saam", "शान", "sam", "셋", "set", "सेत"),
        "4" to listOf("सा", "saa", "शाह", "넷", "need", "net", "neet", "nate", "नेट", "nate", "need", "nath", "nathe"),
        "5" to listOf("ओ", "o", "हो", "다섯", "daseot", "दा सोत", "दासोत", "दासोत्त"),
        "6" to listOf("युक", "yuk", "युग", "युक्क", "युक्त", "युक", "युग", "युक्", "여섯", "yeoseot", "यो सोत", "यो स्रोत", "यो शोत", "यो सोच", "यो स्वत", "यो शोध", "यो शोत", "यो श्वत"),
        "7" to listOf("चिल", "chill", "chil", "छिल", "일곱", "ilgop", "ugop", "ukope", "ugope",
            "irgop", "युग गोप", "you gop", "युगोप", "you goep", "इल गोप", "इल्ड गोप", "इल gop", "इल gope",
            "इलकोप", "इलको", "इल कोप", "इल्कोप", "ill gop", "igop", "ilgop", "igo", "ill gope",
            "il gop", "ill gop", "il go", "ill go"),
        "8" to listOf("फाल", "faal", "पाल", "fall", "여덟", "yeodeol", "यो दोलन", "यो दोल", "यो दुल", "यो दूल"),
        "9" to listOf("kho", "खु", "khu", "खुo", "ख", "아홉", "ahop", "आहोप", "a होप", "ahoop"),
        "10" to listOf("सिप", "seep", "sip", "सीप", "सिप", "열", "yeol", "योल", "योग", "yol"),
        "11" to listOf("सिपिल", "seep eel", "sipil", "cpu", "열하나", "yeolhana"),
        "16" to listOf("sipyuk", "सिप्युक", "CPUK", "열여섯", "yeolyeoseot"),
        "18" to listOf("sippal", "सिपाल", "seep pal", "sip pal", "सिप फाल", "सीप फाल", "सिपाल", "सिपाल।", "열여덟", "yeolyeodeol"),
        "20" to listOf("isip", "esip", "eseep", "스물", "seumul", "semul", "seamul", "seamull", "सेमुल", "सेमूल", "saymul", "simul", "simuli"),
        "21" to listOf("isipil", "ecpl", "ecl", "easypail", "easypael", "easypair", "esibil", "essibil", "esible", "easivil", "스물하나", "seumulhana", "इसिप इल", "इसीप इल", "saymul hana", "semul hana", "saymul hanna", "semul hanna"),
        "27" to listOf("isipchil", "ई सीप चिल", "यी सिपचिल", "यी सिप चिल", "스물일곱", "seumulilgop"),
        "28" to listOf("ईशिपपल", "इसिपल", "isiple", "यी सिपल", "esiple", "इचिप्पल", "ecle", "esipal", "스물여덟", "seumulyeodeol"),
        "30" to listOf("samsip", "samsheep", "साम सीप", "सामसीप", "सान सीप", "서른", "seoreun"),
        "58" to listOf("osippal", "ओसिपल", "ओशिपाल", "ओसिपाल", "ओसिप पाल", "쉰여덟", "swinyeodeol"),
        "68" to listOf("yuksippal", "युग सिपाल", "युगसीपाल", "युग सीपाल", "यो सिपाल", "예순여덟", "yesunyeodeol"),
        "71" to listOf("चिलसिपिल", "cheelsipur", "chilsi pil", "चिल सी पील", "चिल्सी पिल", "चिलसिपल", "일흔하나", "ilheunhana"),
        "76" to listOf("चिलसिप्युक", "चिल्सिप्युक", "चिल्सिप युक", "चिलसीप्युक", "चिलसिप्यक", "childsipuk", "चिलसिप युग", "चिलसिप युक्", "चिलसिप युक्त", "चिलसिप युक", "चिल्सिप युग", "चिलसिप्युक्क", "chelshippuk", "chelsipuk", "चिलसिप्युक्त", "चिलसिपयोग", "चिलसिप युग", "चिल सिप युग", "त्यो सिप युग", "त्यो सीप युग", "त्यो सिप युक", "त्यो सीप युक", "일흔여섯", "ilheunyeoseot"),
        "78" to listOf("चिल्सीपाल", "चिलसिपाल", "चिलसीपाल", "चिल सिपाल", "चिलसिपाल।", "च्युसीपाल", "CHELSIPAL", "चेल्सीपाल", "chelsippal", "चेल्सी पाल", "cusipal", "cusical", "cushippal", "cusic", "일흔여덟", "ilheunyeodeol"),
        "80" to listOf("pulsip", "पालसीप", "पालसिप", "ballship", "पल्सीप", "पल्सिप", "palsip", "पाल्सिप", "여든", "yeodeun"),
        "90" to listOf("gusip", "ग* सीप", "गुसिप", "gossip", "GOO SHIP", "아흔", "aheun"),
        "100" to listOf("baek", "बेक", "bank", "bake", "big", "fake", "pake", "pek", "pak", "pekk", "백", "baek")
    )

    val phoneticKoreanObjectsMapping = mapOf(
        "" to listOf("")
    )

}