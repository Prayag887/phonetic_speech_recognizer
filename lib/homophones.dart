
class Homophones {
  static final Map<String, List<String>> homophones = {
    'accept': ['except'],
    'affect': ['effect'],
    'aisle': ['isle', "I'll"],
    'aloud': ['allowed'],
    'allowed': ['aloud'],
    'ate': ['eight', 'hate'],
    'bare': ['bear'],
    'bean': ['been'],
    'been': ['bin'],
    'blew': ['blue'],
    'blue': ['blew'],
    'brake': ['break'],
    'break': ['brake'],
    'buy': ['by', 'bye'],
    'by': ['buy', 'bye'],
    'bye': ['buy', 'by'],
    'cell': ['sell'],
    'cent': ['scent', 'sent'],
    'dear': ['deer'],
    'days': ['daze'],
    'deer': ['dear'],
    'die': ['dye'],
    'dye': ['die'],
    'eight': ['ate'],
    'effect': ['affect'],
    'except': ['accept'],
    'fair': ['fare'],
    'fare': ['fair'],
    'fir': ['fur'],
    'flour': ['flower'],
    'flower': ['flour'],
    'for': ['fore', 'four'],
    'fore': ['for', 'four'],
    'four': ['for', 'fore'],
    'fur': ['fir'],
    'hear': ['here'],
    'here': ['hear'],
    'hole': ['whole'],
    'hour': ['our'],
    'idle': ['idol'],
    'idol': ['idle'],
    'I\'ll': ['aisle', 'isle'],
    'isle': ['aisle', 'I\'ll'],
    'knew': ['new'],
    'knows': ['nose'],
    'mail': ['male'],
    'maid': ['made'],
    'male': ['mail'],
    'made': ['maid'],
    'meat': ['meet'],
    'meet': ['meat'],
    'morning': ['mourning'],
    'mourning': ['morning'],
    'new': ['knew'],
    'nose': ['knows'],
    'our': ['hour'],
    'pair': ['pare', 'pear'],
    'pare': ['pair', 'pear'],
    'peace': ['piece'],
    'pear': ['pair', 'pare'],
    'piece': ['peace'],
    'principal': ['principle'],
    'principle': ['principal'],
    'rain': ['rein', 'reign'],
    'raise': ['rays'],
    'rays': ['raise'],
    'recogniser': ['recognizer'],
    'recognizer': ['recogniser'],
    'rein': ['rain', 'reign'],
    'reign': ['rain', 'rein'],
    'right': ['rite', 'write'],
    'rite': ['right', 'write'],
    'roll': ['role'],
    'role': ['roll'],
    'scene': ['seen'],
    'scent': ['cent', 'sent'],
    'sea': ['see'],
    'see': ['sea'],
    'sell': ['cell'],
    'sent': ['cent', 'scent'],
    'sita': ['cheetah'],
    'cheetah': ['sita'],
    'spirit': ['speech', 'speed'],
    'speech': ['spirit', 'speed', 'spit'],
    'some': ['sum'],
    'source': ['shores'],
    'shores': ['source'],
    'steel': ['steal'],
    'steal': ['steel'],
    'suite': ['sweet'],
    'sum': ['some'],
    'sweet': ['suite'],
    'tail': ['tale'],
    'tale': ['tail'],
    'their': ['there', "they're"],
    'there': ['their', "they're"],
    "they're": ['their', 'there'],
    'threw': ['through', 'thru'],
    'through': ['threw', 'thru'],
    'thru': ['threw', 'through'],
    'to': ['too', 'two'],
    'too': ['to', 'two'],
    'two': ['to', 'too'],
    'wait': ['weight'],
    'way': ['weigh'],
    'wear': ['ware', 'where'],
    'weather': ['whether'],
    'weigh': ['way'],
    'weight': ['wait'],
    'whether': ['weather'],
    'where': ['wear', 'ware'],
    'whole': ['hole'],
    'wood': ['would'],
    'won': ['one'],
    'would': ['wood'],
    'write': ['right', 'rite'],
    'you\'re': ['your'],
    'your': ['you\'re'],

    'began': ['begin'],
    'begin': ['began'],

    'I': ['a'],
    'a': ['I'],

    'lives': ['leaves'],
    'leaves': ['lives'],
    'come': ['came', 'become'],
    'become': ['come', 'came'],

    'fewa lake': ['favorite', 'fever', 'few'],
    'favorite': ['fewa lake', 'fever', 'few'],

    'baglung': ['baglong', 'pagaloon', 'bag'],
    'nawaraj': ['nawaz', 'navraj', 'navbharat'],
    'gorkha': ['gorka', 'gurkha'],

    'enquired': ['and quiet', 'and quite', 'and quit'],
    'and quiet': ['enquired', 'and quite', 'and quit'],
    'and quite': ['and quiet', 'enquired', 'and quit'],
    'and quit': ['and quiet', 'enquired', 'enquired'],

    'parts' : ['paths'],
    'paths' : ['parts'],
    'hate' : ['ate', 'eight'],

    'clean' : ['green'],
    'green' : ['clean'],

    'walk' : ['walked'],
    'walked' : ['walk'],
    'watched' : ['watch'],
    'watch' : ['watched'],


    'hill': ['little'],
    'little': ['hill'],

    'goal': ['core'],
    'core': ['goal'],

    'accurately': ['security'],
    'security': ['accurately'],

    'saw': ['sub'],
    'sub': ['saw'],

    'quote': ['coat', 'court'],
    'coat': ['quote', 'court'],
    'court': ['coat', 'quote'],
  };

  static bool _arePhoneticallySimilar(String a, String b) {
    final Map<String, String> similarMap = {
      'j': 'g',
      'g': 'j',
      'b': 'd',
      'd': 'b',
      'm': 'n',
      'n': 'm',
      'v': 'f',
      'f': 'v',
      's': 'z',
      'z': 's',
    };

    // Normalize to lowercase
    a = a.trim().toLowerCase();
    b = b.trim().toLowerCase();

    // Exact match
    if (a == b) return true;

    // If single letters and interchangeable
    if (a.length == 1 && b.length == 1 && similarMap[a] == b) return true;

    // Optionally: Use Metaphone for longer strings
    // final String metaphoneA = Metaphone.encode(a);
    // final String metaphoneB = Metaphone.encode(b);
    // return metaphoneA == metaphoneB;

    return false;
  }
}