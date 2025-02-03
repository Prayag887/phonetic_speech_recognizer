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
}
