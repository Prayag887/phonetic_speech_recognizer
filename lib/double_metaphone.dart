import 'dart:io';
import 'package:flutter/foundation.dart';

class DoubleMetaphone {
  static const List<String> _vowels = ['A', 'E', 'I', 'O', 'U', 'Y'];
  static const List<String> _frontVowels = ['E', 'I', 'Y'];
  static const List<String> _variousN = ['N', 'M'];

  static List<String> encode(String word) {
    if (word.isEmpty) return ['', ''];

    String input = word.toUpperCase().trim();
    if (input.length == 1) return [input, ''];

    String primary = '';
    String secondary = '';
    int current = 0;
    int length = input.length;
    int last = length - 1;

    if (_isOneOf(input, 0, ['GN', 'KN', 'PN', 'WR', 'PS'])) {
      current = 1;
    }

    if (input[0] == 'X') {
      primary += 'S';
      secondary += 'S';
      current = 1;
    }

    while (current < length) {
      if (current >= length) break;

      switch (input[current]) {
        case 'A':
        case 'E':
        case 'I':
        case 'O':
        case 'U':
        case 'Y':
          if (current == 0) {
            primary += 'A';
            secondary += 'A';
          }
          current++;
          break;

        case 'B':
          primary += 'P';
          secondary += 'P';
          if (current + 1 < length && _safeCharAt(input, current + 1) == 'B') {
            current += 2;
          } else {
            current++;
          }
          break;

        case 'C':
          if (current > 1 && !_isVowel(input[current - 2]) &&
              _isOneOf(input, current - 1, ['ACH']) &&
              (current + 2 >= length || _safeCharAt(input, current + 2) != 'I') &&
              ((current + 2 >= length || _safeCharAt(input, current + 2) != 'E') || _isOneOf(input, current - 2, ['BACHER', 'MACHER']))) {
            primary += 'K';
            secondary += 'K';
            current += 2;
            break;
          }

          if (current == 0 && _isOneOf(input, current, ['CAESAR'])) {
            primary += 'S';
            secondary += 'S';
            current += 2;
            break;
          }

          if (_isOneOf(input, current, ['CHIA'])) {
            primary += 'K';
            secondary += 'K';
            current += 2;
            break;
          }

          if (_isOneOf(input, current, ['CH'])) {
            if (current > 0 && _isOneOf(input, current, ['CHAE'])) {
              primary += 'K';
              secondary += 'X';
              current += 2;
              break;
            }

            if (current == 0 && ((current + 1 < length && _frontVowels.contains(_safeCharAt(input, current + 1))) ||
                _isOneOf(input, current, ['CHORE', 'CHUTE', 'CHOIR']))) {
              primary += 'X';
              secondary += 'X';
            } else {
              if (current > 0) {
                if (_isOneOf(input, 0, ['MC'])) {
                  primary += 'X';
                  secondary += 'X';
                } else {
                  primary += 'X';
                  secondary += 'K';
                }
              } else {
                primary += 'X';
                secondary += 'X';
              }
            }
            current += 2;
            break;
          }

          if (_isOneOf(input, current, ['CZ']) && !_isOneOf(input, current - 2, ['WICZ'])) {
            primary += 'S';
            secondary += 'X';
            current += 2;
            break;
          }

          if (_isOneOf(input, current + 1, ['CIA'])) {
            primary += 'X';
            secondary += 'X';
            current += 3;
            break;
          }

          if (_isOneOf(input, current, ['CC']) && !(current == 1 && input[0] == 'M')) {
            if (current + 2 < length && _isOneOf(input, current + 2, ['I', 'E', 'H']) &&
                !_isOneOf(input, current + 2, ['HU'])) {
              if ((current == 1 && _safeCharAt(input, current - 1) == 'A') ||
                  _isOneOf(input, current - 1, ['UCCEE', 'UCCES'])) {
                primary += 'KS';
                secondary += 'KS';
              } else {
                primary += 'X';
                secondary += 'X';
              }
              current += 3;
              break;
            } else {
              primary += 'K';
              secondary += 'K';
              current += 2;
              break;
            }
          }

          if (_isOneOf(input, current, ['CK', 'CG', 'CQ'])) {
            primary += 'K';
            secondary += 'K';
            current += 2;
            break;
          }

          if (_isOneOf(input, current, ['CI', 'CE', 'CY'])) {
            if (_isOneOf(input, current, ['CIO', 'CIE', 'CIA'])) {
              primary += 'S';
              secondary += 'X';
            } else {
              primary += 'S';
              secondary += 'S';
            }
            current += 2;
            break;
          }

          primary += 'K';
          secondary += 'K';
          if (_isOneOf(input, current + 1, [' C', ' Q', ' G'])) {
            current += 3;
          } else if (_isOneOf(input, current + 1, ['C', 'K', 'Q']) &&
              !_isOneOf(input, current + 1, ['CE', 'CI'])) {
            current += 2;
          } else {
            current++;
          }
          break;

        case 'D':
          if (_isOneOf(input, current, ['DG'])) {
            if (current + 2 < length && _frontVowels.contains(_safeCharAt(input, current + 2))) {
              primary += 'J';
              secondary += 'J';
              current += 3;
            } else {
              primary += 'TK';
              secondary += 'TK';
              current += 2;
            }
            break;
          }

          if (_isOneOf(input, current, ['DT', 'DD'])) {
            primary += 'T';
            secondary += 'T';
            current += 2;
            break;
          }

          primary += 'T';
          secondary += 'T';
          current++;
          break;

        case 'F':
          if (current + 1 < length && _safeCharAt(input, current + 1) == 'F') {
            current += 2;
          } else {
            current++;
          }
          primary += 'F';
          secondary += 'F';
          break;

        case 'G':
          if (current + 1 < length && _safeCharAt(input, current + 1) == 'H') {
            if (current > 0 && !_isVowel(_safeCharAt(input, current - 1))) {
              primary += 'K';
              secondary += 'K';
              current += 2;
              break;
            }

            if (current < 3) {
              if (current == 0) {
                if (current + 2 < length && _safeCharAt(input, current + 2) == 'I') {
                  primary += 'J';
                  secondary += 'J';
                } else {
                  primary += 'K';
                  secondary += 'K';
                }
                current += 2;
                break;
              }
            }

            if ((current > 1 && _isOneOf(input, current - 2, ['B', 'H', 'D'])) ||
                (current > 2 && _isOneOf(input, current - 3, ['B', 'H', 'D'])) ||
                (current > 3 && _isOneOf(input, current - 4, ['B', 'H']))) {
              current += 2;
              break;
            } else {
              if (current > 2 && _safeCharAt(input, current - 1) == 'U' &&
                  _isOneOf(input, current - 3, ['C', 'G', 'L', 'R', 'T'])) {
                primary += 'F';
                secondary += 'F';
              } else if (current > 0 && _safeCharAt(input, current - 1) != 'I') {
                primary += 'K';
                secondary += 'K';
              }
              current += 2;
              break;
            }
          }

          if (current + 1 < length && _safeCharAt(input, current + 1) == 'N') {
            if (current == 1 && _isVowel(input[0]) && !_isSlavoGermanic(input)) {
              primary += 'KN';
              secondary += 'N';
            } else if (!_isOneOf(input, current + 2, ['EY']) &&
                _safeCharAt(input, current + 1) != 'Y' && !_isSlavoGermanic(input)) {
              primary += 'N';
              secondary += 'KN';
            } else {
              primary += 'KN';
              secondary += 'KN';
            }
            current += 2;
            break;
          }

          if (_isOneOf(input, current + 1, ['LI']) && !_isSlavoGermanic(input)) {
            primary += 'KL';
            secondary += 'L';
            current += 2;
            break;
          }

          if (current == 0 && (_safeCharAt(input, current + 1) == 'Y' ||
              _isOneOf(input, current + 1, ['ES', 'EP', 'EB', 'EL', 'EY', 'IB', 'IL', 'IN', 'IE', 'EI', 'ER']))) {
            primary += 'K';
            secondary += 'J';
            current += 2;
            break;
          }

          if ((_isOneOf(input, current + 1, ['ER']) || _safeCharAt(input, current + 1) == 'Y') &&
              !_isOneOf(input, 0, ['DANGER', 'RANGER', 'MANGER']) &&
              !_isOneOf(input, current - 1, ['E', 'I']) &&
              !_isOneOf(input, current - 1, ['RGY', 'OGY'])) {
            primary += 'K';
            secondary += 'J';
            current += 2;
            break;
          }

          if (current + 1 < length && _frontVowels.contains(_safeCharAt(input, current + 1)) ||
              _isOneOf(input, current + 1, ['ET', 'EG', 'ER'])) {
            if (_isOneOf(input, 0, ['VAN ', 'VON ']) || _isOneOf(input, 0, ['SCH']) ||
                _isOneOf(input, current + 1, ['ET'])) {
              primary += 'K';
              secondary += 'K';
            } else if (_isOneOf(input, current + 1, ['IER'])) {
              primary += 'J';
              secondary += 'J';
            } else {
              primary += 'J';
              secondary += 'K';
            }
            current += 2;
            break;
          }

          if (current + 1 < length && _safeCharAt(input, current + 1)== 'G') {
            current += 2;
          } else {
            current++;
          }
          primary += 'K';
          secondary += 'K';
          break;

        case 'H':
          if ((current == 0 || _isVowel(_safeCharAt(input, current - 1))) && _isVowel(_safeCharAt(input, current + 1))) {
            primary += 'H';
            secondary += 'H';
            current += 2;
          } else {
            current++;
          }
          break;

        case 'J':
          if (_isOneOf(input, current, ['JOSE']) || _isOneOf(input, 0, ['SAN '])) {
            if ((current == 0 && current + 4 < length && input[current + 4] == ' ') ||
                _isOneOf(input, 0, ['SAN '])) {
              primary += 'H';
              secondary += 'H';
            } else {
              primary += 'J';
              secondary += 'H';
            }
            current++;
            break;
          }

          if (current == 0 && !_isOneOf(input, current, ['JOSE'])) {
            primary += 'J';
            secondary += 'A';
          } else if (_isVowel(_safeCharAt(input, current - 1)) && !_isSlavoGermanic(input) &&
              (_safeCharAt(input, current + 1)== 'A' || _safeCharAt(input, current + 1) == 'O')) {
            primary += 'J';
            secondary += 'H';
          } else if (current == last) {
            primary += 'J';
            secondary += '';
          } else if (!_isOneOf(input, current + 1, ['L', 'T', 'K', 'S', 'N', 'M', 'B', 'Z']) &&
              !_isOneOf(input, current - 1, ['S', 'K', 'L'])) {
            primary += 'J';
            secondary += 'J';
          }

          if (current + 1 < length && _safeCharAt(input, current + 1) == 'J') {
            current += 2;
          } else {
            current++;
          }
          break;

        case 'K':
          if (current + 1 < length && _safeCharAt(input, current + 1) == 'K') {
            current += 2;
          } else {
            current++;
          }
          primary += 'K';
          secondary += 'K';
          break;

        case 'L':
          if (current + 1 < length && _safeCharAt(input, current + 1) == 'L') {
            if ((current == length - 3 && _isOneOf(input, current - 1, ['ILLO', 'ILLA', 'ALLE'])) ||
                ((_isOneOf(input, last - 1, ['AS', 'OS']) || _isOneOf(input, last, ['A', 'O'])) &&
                    _isOneOf(input, current - 1, ['ALLE']))) {
              primary += 'L';
              secondary += '';
              current += 2;
              break;
            }
            current += 2;
          } else {
            current++;
          }
          primary += 'L';
          secondary += 'L';
          break;

        case 'M':
          if ((_isOneOf(input, current - 1, ['UMB']) &&
              ((current + 1 == last) || _isOneOf(input, current + 2, ['ER']))) ||
              (current + 1 < length && _safeCharAt(input, current + 1) == 'M')) {
            current += 2;
          } else {
            current++;
          }
          primary += 'M';
          secondary += 'M';
          break;

        case 'N':
          if (current + 1 < length && _safeCharAt(input, current + 1) == 'N') {
            current += 2;
          } else {
            current++;
          }
          primary += 'N';
          secondary += 'N';
          break;

        case 'P':
          if (current + 1 < length && _safeCharAt(input, current + 1) == 'H') {
            primary += 'F';
            secondary += 'F';
            current += 2;
            break;
          }

          if (_isOneOf(input, current + 1, ['P', 'B'])) {
            current += 2;
          } else {
            current++;
          }
          primary += 'P';
          secondary += 'P';
          break;

        case 'Q':
          if (current + 1 < length && _safeCharAt(input, current + 1) == 'Q') {
            current += 2;
          } else {
            current++;
          }
          primary += 'K';
          secondary += 'K';
          break;

        case 'R':
          if (current == last && !_isSlavoGermanic(input) &&
              _isOneOf(input, current - 2, ['IE']) &&
              !_isOneOf(input, current - 4, ['ME', 'MA'])) {
            primary += '';
            secondary += 'R';
          } else {
            primary += 'R';
            secondary += 'R';
          }

          if (current + 1 < length && _safeCharAt(input, current + 1) == 'R') {
            current += 2;
          } else {
            current++;
          }
          break;

        case 'S':
          if (_isOneOf(input, current - 1, ['ISL', 'YSL'])) {
            current++;
            break;
          }

          if (current == 0 && _isOneOf(input, current, ['SUGAR'])) {
            primary += 'X';
            secondary += 'S';
            current++;
            break;
          }

          if (_isOneOf(input, current, ['SH'])) {
            if (_isOneOf(input, current + 1, ['HEIM', 'HOEK', 'HOLM', 'HOLZ'])) {
              primary += 'S';
              secondary += 'S';
            } else {
              primary += 'X';
              secondary += 'X';
            }
            current += 2;
            break;
          }

          if (_isOneOf(input, current, ['SIO', 'SIA']) || _isOneOf(input, current, ['SIAN'])) {
            if (!_isSlavoGermanic(input)) {
              primary += 'S';
              secondary += 'X';
            } else {
              primary += 'S';
              secondary += 'S';
            }
            current += 3;
            break;
          }

          if ((current == 0 && _isOneOf(input, current + 1, ['M', 'N', 'L', 'W'])) ||
              _isOneOf(input, current + 1, ['Z'])) {
            primary += 'S';
            secondary += 'X';
            if (_isOneOf(input, current + 1, ['Z'])) {
              current += 2;
            } else {
              current++;
            }
            break;
          }

          if (_isOneOf(input, current, ['SC'])) {
            if (current + 2 < length && _safeCharAt(input, current + 2) == 'H') {
              if (_isOneOf(input, current + 3, ['OO', 'ER', 'EN', 'UY', 'ED', 'EM'])) {
                if (_isOneOf(input, current + 3, ['ER', 'EN'])) {
                  primary += 'X';
                  secondary += 'SK';
                } else {
                  primary += 'SK';
                  secondary += 'SK';
                }
                current += 3;
                break;
              } else {
                if (current == 0 && !_isVowel(input[3]) && input[3] != 'W') {
                  primary += 'X';
                  secondary += 'S';
                } else {
                  primary += 'X';
                  secondary += 'X';
                }
                current += 3;
                break;
              }
            }

            if (current + 2 < length && _frontVowels.contains(_safeCharAt(input, current + 2))) {
              primary += 'S';
              secondary += 'S';
              current += 3;
              break;
            }

            primary += 'SK';
            secondary += 'SK';
            current += 3;
            break;
          }

          if (current == last && _isOneOf(input, current - 2, ['AI', 'OI'])) {
            primary += '';
            secondary += 'S';
          } else {
            primary += 'S';
            secondary += 'S';
          }

          if (_isOneOf(input, current + 1, ['S', 'Z'])) {
            current += 2;
          } else {
            current++;
          }
          break;

        case 'T':
          if (_isOneOf(input, current, ['TION'])) {
            primary += 'X';
            secondary += 'X';
            current += 3;
            break;
          }

          if (_isOneOf(input, current, ['TIA', 'TCH'])) {
            primary += 'X';
            secondary += 'X';
            current += 3;
            break;
          }

          if (_isOneOf(input, current, ['TH']) || _isOneOf(input, current, ['TTH'])) {
            if (_isOneOf(input, current + 2, ['OM', 'AM']) ||
                _isOneOf(input, 0, ['VAN ', 'VON ']) || _isOneOf(input, 0, ['SCH'])) {
              primary += 'T';
              secondary += 'T';
            } else {
              primary += '0';
              secondary += 'T';
            }
            current += 2;
            break;
          }

          if (_isOneOf(input, current + 1, ['T', 'D'])) {
            current += 2;
          } else {
            current++;
          }
          primary += 'T';
          secondary += 'T';
          break;

        case 'V':
          if (current + 1 < length && _safeCharAt(input, current + 1) == 'V') {
            current += 2;
          } else {
            current++;
          }
          primary += 'F';
          secondary += 'F';
          break;

        case 'W':
          if (_isOneOf(input, current, ['WR'])) {
            primary += 'R';
            secondary += 'R';
            current += 2;
            break;
          }

          if (current == 0 && (_isVowel(_safeCharAt(input, current + 1)) || _isOneOf(input, current, ['WH']))) {
            if (_isVowel(_safeCharAt(input, current + 1))) {
              primary += 'A';
              secondary += 'F';
            } else {
              primary += 'A';
              secondary += 'A';
            }
          }

          if ((current == last && _isVowel(_safeCharAt(input, current - 1))) ||
              _isOneOf(input, current - 1, ['EWSKI', 'EWSKY', 'OWSKI', 'OWSKY']) ||
              _isOneOf(input, 0, ['SCH'])) {
            primary += '';
            secondary += 'F';
            current++;
            break;
          }

          if (_isOneOf(input, current, ['WICZ', 'WITZ'])) {
            primary += 'TS';
            secondary += 'FX';
            current += 4;
            break;
          }

          current++;
          break;

        case 'X':
          if (!(current == last && (_isOneOf(input, current - 3, ['IAU', 'EAU']) ||
              _isOneOf(input, current - 2, ['AU', 'OU'])))) {
            primary += 'KS';
            secondary += 'KS';
          }

          if (_isOneOf(input, current + 1, ['C', 'X'])) {
            current += 2;
          } else {
            current++;
          }
          break;

        case 'Z':
          if (current + 1 < length && _safeCharAt(input, current + 1) == 'H') {
            primary += 'J';
            secondary += 'J';
            current += 2;
            break;
          } else if (_isOneOf(input, current + 1, ['ZO', 'ZI', 'ZA']) ||
              (_isSlavoGermanic(input) && (current > 0 && _safeCharAt(input, current - 1) != 'T'))) {
            primary += 'S';
            secondary += 'TS';
          } else {
            primary += 'S';
            secondary += 'S';
          }

          if (current + 1 < length && _safeCharAt(input, current + 1) == 'Z') {
            current += 2;
          } else {
            current++;
          }
          break;

        default:
          current++;
          break;
      }
    }

    return [primary.length > 4 ? primary.substring(0, 4) : primary,
      secondary.length > 4 ? secondary.substring(0, 4) : secondary];
  }

  static bool _isVowel(String char) {
    return _vowels.contains(char);
  }

  static bool _isSlavoGermanic(String input) {
    return input.contains('W') || input.contains('K') || input.contains('CZ') || input.contains('WITZ');
  }

  static bool _isOneOf(String input, int start, List<String> patterns) {
    if (start < 0 || start >= input.length) {
      return false;
    }

    for (String pattern in patterns) {
      if (start + pattern.length <= input.length) {
        if (input.substring(start, start + pattern.length) == pattern) {
          return true;
        }
      }
    }
    return false;
  }

  static String _safeCharAt(String input, int index) {
    return (index >= 0 && index < input.length) ? input[index] : '';
  }

  static Future<List<String>> encodeSingle(String word) async {
    return await compute(_processSingleWord, word);
  }

  static Future<List<List<String>>> encodeMultiple(List<String> words) async {
    if (words.isEmpty) return [];

    if (words.length < 4) {
      return words.map((word) => encode(word)).toList();
    }

    final numProcessors = Platform.numberOfProcessors;
    final chunkSize = (words.length / numProcessors).ceil().clamp(1, words.length);
    final chunks = <List<String>>[];

    for (int i = 0; i < words.length; i += chunkSize) {
      final end = (i + chunkSize < words.length) ? i + chunkSize : words.length;
      chunks.add(words.sublist(i, end));
    }

    final futures = chunks.map((chunk) => compute(_processChunk, chunk));
    final results = await Future.wait(futures);

    return results.expand((list) => list).toList();
  }

  static Future<List<List<String>>> encodeParallel(List<String> words) async {
    if (words.isEmpty) return [];

    final futures = words.map((word) => compute(_processSingleWord, word));
    final results = await Future.wait(futures);

    return results;
  }

  static Future<List<List<String>>> encodeBatch(List<String> words, {int batchSize = 100}) async {
    if (words.isEmpty) return [];

    final batches = <List<String>>[];
    for (int i = 0; i < words.length; i += batchSize) {
      final end = (i + batchSize < words.length) ? i + batchSize : words.length;
      batches.add(words.sublist(i, end));
    }

    final futures = batches.map((batch) => compute(_processChunk, batch));
    final results = await Future.wait(futures);

    return results.expand((list) => list).toList();
  }
}

List<List<String>> _processChunk(List<String> words) {
  return words.map((word) => DoubleMetaphone.encode(word)).toList();
}

List<String> _processSingleWord(String word) {
  return DoubleMetaphone.encode(word);
}