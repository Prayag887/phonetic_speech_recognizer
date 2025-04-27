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
    '일(1) - il (hana)', '이(2) - i (dul)', '삼(3) - sam (set)', '사(4) - sa (net)', '오(5) - o (daseot)', '육(6) - yuk (yeoseot)', '칠(7) - chil (ilgop)', '팔(8) - pal (yeodeol)', '구(9) - gu (ahop)', '십(10) - sip (yeol)',
    '십일(11) - sipil (yeolhana)', '십이(12) - sibi (yeoldul)', '십삼(13) - sipsam (yeolset)', '십사(14) - sipsa (yeolnet)', '십오(15) - sipo (yeoldaseot)', '십육(16) - sipyuk (yeolyeoseot)', '십칠(17) - sipchil (yeolilgop)', '십팔(18) - sippal (yeolyeodeol)', '십구(19) - sipgu (yeolahop)', '이십(20) - iship (seumul)',
    '이십일(21) - ishipil (seumulhana)', '이십이(22) - ishipi (seumuldul)', '이십삼(23) - ishipsam (seumulset)', '이십사(24) - ishipsa (seumulnet)', '이십오(25) - ishipo (seumuldaseot)', '이십육(26) - ishipyuk (seumulyeoseot)', '이십칠(27) - ishipchil (seumulilgop)', '이십팔(28) - ishippal (seumulyeodeol)', '이십구(29) - ishipgu (seumulahop)', '삼십(30) - samsip (seoreun)',
    '삼십일(31) - samsipil (seoreunhana)', '삼십이(32) - samsipi (seoreundul)', '삼십삼(33) - samsipsam (seoreunset)', '삼십사(34) - samsipsa (seoreunnet)', '삼십오(35) - samsipo (seoreundaseot)', '삼십육(36) - samsipyuk (seoreunyeoseot)', '삼십칠(37) - samsipchil (seoreunilgop)', '삼십팔(38) - samsippal (seoreunyeodeol)', '삼십구(39) - samsipgu (seoreunahop)', '사십(40) - sasip (maheun)',
    '사십일(41) - sasipil (maheunhana)', '사십이(42) - sasipi (maheundul)', '사십삼(43) - sasipsam (maheunset)', '사십사(44) - sasipsa (maheunnet)', '사십오(45) - sasipo (maheundaseot)', '사십육(46) - sasipyuk (maheunyeoseot)', '사십칠(47) - sasipchil (maheunilgop)', '사십팔(48) - sasippal (maheunyeodeol)', '사십구(49) - sasipgu (maheunahop)', '오십(50) - osip (swin)',
    '오십일(51) - osipil (swinhana)', '오십이(52) - osipi (swindul)', '오십삼(53) - osipsam (swinset)', '오십사(54) - osipsa (swinnet)', '오십오(55) - osipo (swindaseot)', '오십육(56) - osipyuk (swinyeoseot)', '오십칠(57) - osipchil (swinilgop)', '오십팔(58) - osippal (swinyeodeol)', '오십구(59) - osipgu (swinahop)', '육십(60) - yuksip (yesun)',
    '육십일(61) - yuksipil (yesunhana)', '육십이(62) - yuksipi (yesundul)', '육십삼(63) - yuksipsam (yesunset)', '육십사(64) - yuksipsa (yesunnet)', '육십오(65) - yuksipo (yesundaseot)', '육십육(66) - yuksipyuk (yesunyeoseot)', '육십칠(67) - yuksipchil (yesunilgop)', '육십팔(68) - yuksippal (yesunyeodeol)', '육십구(69) - yuksipgu (yesunahop)', '칠십(70) - chilsip (ilgop)',
    '칠십일(71) - chilsipil (ilgophana)', '칠십이(72) - chilsipi (ilgopdul)', '칠십삼(73) - chilsipsam (ilgopset)', '칠십사(74) - chilsipsa (ilgopnet)', '칠십오(75) - chilsipo (ilgopdaseot)', '칠십육(76) - chilsipyuk (ilgopyeoseot)', '칠십칠(77) - chilsipchil (ilgopilgop)', '칠십팔(78) - chilsippal (ilgopyeodeol)', '칠십구(79) - chilsipgu (ilgopahop)', '팔십(80) - palsip (yeodeun)',
    '팔십일(81) - palsipil (yeodeunhana)', '팔십이(82) - palsipi (yeodeundul)', '팔십삼(83) - palsipsam (yeodeunset)', '팔십사(84) - palsipsa (yeodeunnet)', '팔십오(85) - palsipo (yeodeundaseot)', '팔십육(86) - palsipyuk (yeodeunyeoseot)', '팔십칠(87) - palsipchil (yeodeunilgop)', '팔십팔(88) - palsippal (yeodeunyeodeol)', '팔십구(89) - palsipgu (yeodeunahop)', '구십(90) - gusip (ahoon)',
    '구십일(91) - gusipil (ahoonhana)', '구십이(92) - gusipi (ahoondul)', '구십삼(93) - gusipsam (ahoonset)', '구십사(94) - gusipsa (ahoonnet)', '구십오(95) - gusipo (ahoondaseot)', '구십육(96) - gusipyuk (ahoonyeoseot)', '구십칠(97) - gusipchil (ahoonilgop)', '구십팔(98) - gusippal (ahoonyeodeol)', '구십구(99) - gusipgu (ahoonahop)', '백(100) - baek',
  ];


static final List<String> koreanWords = ['입 (इप्)', '태양 (थेयाङ)', '배 (बे)', '오븐 (ओबुन)', '밥 (बाप्)', '밤 (बाम्)',
  '손 (सोन्)', '콩 (खोङ्)', '집 (चिप्)', '벽 (ब्योक्)', '벌 (बोल्)',
  '그릇 (गुरुत्)', '못 (मोत्)', '볼트 (बोल्टु)', '삽 (साप्)', '밧줄 (बाच्चुल्)',
  '너트 (नटु)', '닭 (ताक्)'];



  static final Random _random = Random();
  static int _counter = 0;

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
  // Generate a random Korean words
  static String generateRandomKoreanWords() {
    return koreanWords[_random.nextInt(koreanWords.length)];
  }

  static String generateSerialKoreanNumber() {
    String number = koreanNumbers[_counter];
    _counter = (_counter + 1) % koreanNumbers.length; // Increment and reset at 100
    return number;
  }
}
