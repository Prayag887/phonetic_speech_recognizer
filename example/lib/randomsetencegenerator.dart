import 'dart:math';

class RandomSentenceGenerator {
  // Word lists
  static final List<String> nouns = [
    'dog', 'cat', 'man', 'woman', 'bird', 'house', 'car', 'tree', 'computer', 'phone'
  ];

  static final List<String> verbs = [
    'eats', 'runs', 'jumps', 'plays', 'drives', 'sees', 'talks', 'thinks', 'sings', 'reads'
  ];

  static final List<String> adjectives = [
    'big', 'small', 'quick', 'lazy', 'beautiful', 'loud', 'happy', 'angry', 'quiet', 'slow'
  ];

  static final List<String> adverbs = [
    'quickly', 'slowly', 'happily', 'angrily', 'loudly', 'gracefully', 'carefully', 'easily'
  ];

  static final List<String> koreanNumbers = [
    '일(1) - il', '이(2) - e', '삼(3) - saam', '사(4) - saa', '오(5) - o', '육(6) - yuk', '칠(7) - chill', '팔(8) - paal', '구(9) - gu', '십(10) - sip',
    '십일(11) - sipil', '십이(12) - sippil', '십삼(13) - sipsam', '십사(14) - sipsa', '십오(15) - sipo', '십육(16) - sipyuk', '십칠(17) - sipchil', '십팔(18) - sippal', '십구(19) - sipgu', '이십(20) - iship',
    '이십일(21) - ishipil', '이십이(22) - ishipi', '이십삼(23) - ishipsam', '이십사(24) - ishipsa', '이십오(25) - ishipo', '이십육(26) - ishipyuk', '이십칠(27) - ishipchil', '이십팔(28) - ishippal', '이십구(29) - ishipgu', '삼십(30) - samsip',
    '삼십일(31) - samsipil', '삼십이(32) - samsipi', '삼십삼(33) - samsipsam', '삼십사(34) - samsipsa', '삼십오(35) - samsipo', '삼십육(36) - samsipyuk', '삼십칠(37) - samsipchil', '삼십팔(38) - samsippal', '삼십구(39) - samsipgu', '사십(40) - sasip',
    '사십일(41) - sasipil', '사십이(42) - sasipi', '사십삼(43) - sasipsam', '사십사(44) - sasipsa', '사십오(45) - sasipo', '사십육(46) - sasipyuk', '사십칠(47) - sasipchil', '사십팔(48) - sasippal', '사십구(49) - sasipgu', '오십(50) - osip',
    '오십일(51) - osipil', '오십이(52) - osipi', '오십삼(53) - osipsam', '오십사(54) - osipsa', '오십오(55) - osipo', '오십육(56) - osipyuk', '오십칠(57) - osipchil', '오십팔(58) - osippal', '오십구(59) - osipgu', '육십(60) - yuksip',
    '육십일(61) - yuksipil', '육십이(62) - yuksipi', '육십삼(63) - yuksipsam', '육십사(64) - yuksipsa', '육십오(65) - yuksipo', '육십육(66) - yuksipyuk', '육십칠(67) - yuksipchil', '육십팔(68) - yuksippal', '육십구(69) - yuksipgu', '칠십(70) - chilsip',
    '칠십일(71) - chilsipil', '칠십이(72) - chilsipi', '칠십삼(73) - chilsipsam', '칠십사(74) - chilsipsa', '칠십오(75) - chilsipo', '칠십육(76) - chilsipyuk', '칠십칠(77) - chilsipchil', '칠십팔(78) - chilsippal', '칠십구(79) - chilsipgu', '팔십(80) - palsip',
    '팔십일(81) - palsipil', '팔십이(82) - palsipi', '팔십삼(83) - palsipsam', '팔십사(84) - palsipsa', '팔십오(85) - palsipo', '팔십육(86) - palsipyuk', '팔십칠(87) - palsipchil', '팔십팔(88) - palsippal', '팔십구(89) - palsipgu', '구십(90) - gusip',
    '구십일(91) - gusipil', '구십이(92) - gusipi', '구십삼(93) - gusipsam', '구십사(94) - gusipsa', '구십오(95) - gusipo', '구십육(96) - gusipyuk', '구십칠(97) - gusipchil', '구십팔(98) - gusippal', '구십구(99) - gusipgu', '백(100) - baek'
  ];


  static final Random _random = Random();

  // Function to generate a random sentence
  static String generateSentence() {
    String noun1 = nouns[_random.nextInt(nouns.length)];
    String verb = verbs[_random.nextInt(verbs.length)];
    String adjective = adjectives[_random.nextInt(adjectives.length)];
    String adverb = adverbs[_random.nextInt(adverbs.length)];

    // Construct a simple sentence
    return "The $adjective $noun1 $verb $adverb.";
  }


  // Generate a random Korean number
  static String generateRandomKoreanNumber() {
    return koreanNumbers[_random.nextInt(koreanNumbers.length)];
  }
}
