package com.prayag.phonetic_speech_recognizer

// Double Metaphone implementation - much more accurate than Soundex
class DoubleMetaphone {

    fun encode(word: String): DoubleMetaphoneResult {
        if (word.isEmpty()) return DoubleMetaphoneResult("", "")

        val normalized = normalizeForSpeechRecognition(word.uppercase())
        val primary = StringBuilder()
        val alternate = StringBuilder()

        var current = 0
        val length = normalized.length

        // Handle initial silent letters
        current = skipInitialSilent(normalized, current)

        while (current < length && primary.length < 4) {
            when (normalized[current]) {
                'A', 'E', 'I', 'O', 'U', 'Y' -> {
                    if (current == 0) {
                        primary.append('A')
                        alternate.append('A')
                    }
                    current++
                }
                'B' -> {
                    primary.append('P')
                    alternate.append('P')
                    current = if (current + 1 < length && normalized[current + 1] == 'B') current + 2 else current + 1
                }
                'C' -> {
                    val result = handleC(normalized, current, primary, alternate)
                    current = result
                }
                'D' -> {
                    val result = handleD(normalized, current, primary, alternate)
                    current = result
                }
                'F' -> {
                    primary.append('F')
                    alternate.append('F')
                    current = if (current + 1 < length && normalized[current + 1] == 'F') current + 2 else current + 1
                }
                'G' -> {
                    val result = handleG(normalized, current, primary, alternate)
                    current = result
                }
                'H' -> {
                    val result = handleH(normalized, current, primary, alternate)
                    current = result
                }
                'J' -> {
                    primary.append('J')
                    alternate.append('J')
                    current = if (current + 1 < length && normalized[current + 1] == 'J') current + 2 else current + 1
                }
                'K' -> {
                    primary.append('K')
                    alternate.append('K')
                    current = if (current + 1 < length && normalized[current + 1] == 'K') current + 2 else current + 1
                }
                'L' -> {
                    primary.append('L')
                    alternate.append('L')
                    current = if (current + 1 < length && normalized[current + 1] == 'L') current + 2 else current + 1
                }
                'M' -> {
                    primary.append('M')
                    alternate.append('M')
                    current = if (current + 1 < length && normalized[current + 1] == 'M') current + 2 else current + 1
                }
                'N' -> {
                    primary.append('N')
                    alternate.append('N')
                    current = if (current + 1 < length && normalized[current + 1] == 'N') current + 2 else current + 1
                }
                'P' -> {
                    val result = handleP(normalized, current, primary, alternate)
                    current = result
                }
                'Q' -> {
                    primary.append('K')
                    alternate.append('K')
                    current = if (current + 1 < length && normalized[current + 1] == 'U') current + 2 else current + 1
                }
                'R' -> {
                    primary.append('R')
                    alternate.append('R')
                    current = if (current + 1 < length && normalized[current + 1] == 'R') current + 2 else current + 1
                }
                'S' -> {
                    val result = handleS(normalized, current, primary, alternate)
                    current = result
                }
                'T' -> {
                    val result = handleT(normalized, current, primary, alternate)
                    current = result
                }
                'V' -> {
                    primary.append('F')
                    alternate.append('F')
                    current = if (current + 1 < length && normalized[current + 1] == 'V') current + 2 else current + 1
                }
                'W' -> {
                    val result = handleW(normalized, current, primary, alternate)
                    current = result
                }
                'X' -> {
                    val result = handleX(normalized, current, primary, alternate)
                    current = result
                }
                'Z' -> {
                    primary.append('S')
                    alternate.append('S')
                    current = if (current + 1 < length && normalized[current + 1] == 'Z') current + 2 else current + 1
                }
                else -> current++
            }
        }

        return DoubleMetaphoneResult(
            primary.toString().padEnd(4, '0').take(4),
            alternate.toString().padEnd(4, '0').take(4)
        )
    }

    private fun normalizeForSpeechRecognition(word: String): String {
        return word.uppercase()
            // Common speech recognition confusions
            .replace("PH", "F")
            .replace("GH", "F")
            .replace("CK", "K")
            .replace("QU", "KW")
            // Handle treasure -> toys are confusion
            .replace("TREASURE", "TOYSARE")
            .replace("TREASUR", "TOYSAR")
            // Other common confusions
            .replace("TION", "SHON")
            .replace("SION", "SHON")
            .filter { it.isLetter() }
    }

    private fun skipInitialSilent(word: String, start: Int): Int {
        var current = start
        // Skip initial silent letters
        if (current < word.length - 1) {
            when {
                word.startsWith("GN") || word.startsWith("KN") ||
                        word.startsWith("PN") || word.startsWith("WR") ||
                        word.startsWith("PS") -> current = 1
            }
        }
        return current
    }

    // Simplified handlers - you can expand these based on your specific needs
    private fun handleC(word: String, pos: Int, primary: StringBuilder, alternate: StringBuilder): Int {
        return when {
            pos + 1 < word.length && word[pos + 1] == 'H' -> {
                primary.append("K")
                alternate.append("K")
                pos + 2
            }
            pos + 1 < word.length && (word[pos + 1] == 'E' || word[pos + 1] == 'I' || word[pos + 1] == 'Y') -> {
                primary.append("S")
                alternate.append("S")
                pos + 1
            }
            else -> {
                primary.append("K")
                alternate.append("K")
                pos + 1
            }
        }
    }

    private fun handleD(word: String, pos: Int, primary: StringBuilder, alternate: StringBuilder): Int {
        return when {
            pos + 2 < word.length && word.substring(pos, pos + 2) == "DG" -> {
                primary.append("J")
                alternate.append("J")
                pos + 2
            }
            else -> {
                primary.append("T")
                alternate.append("T")
                pos + 1
            }
        }
    }

    private fun handleG(word: String, pos: Int, primary: StringBuilder, alternate: StringBuilder): Int {
        return when {
            pos + 1 < word.length && word[pos + 1] == 'H' -> {
                primary.append("K")
                alternate.append("K")
                pos + 2
            }
            pos + 1 < word.length && (word[pos + 1] == 'E' || word[pos + 1] == 'I' || word[pos + 1] == 'Y') -> {
                primary.append("J")
                alternate.append("J")
                pos + 1
            }
            else -> {
                primary.append("K")
                alternate.append("K")
                pos + 1
            }
        }
    }

    private fun handleH(word: String, pos: Int, primary: StringBuilder, alternate: StringBuilder): Int {
        return if (pos == 0 || isVowel(word[pos - 1]) && pos + 1 < word.length && isVowel(word[pos + 1])) {
            primary.append("H")
            alternate.append("H")
            pos + 1
        } else {
            pos + 1
        }
    }

    private fun handleP(word: String, pos: Int, primary: StringBuilder, alternate: StringBuilder): Int {
        return if (pos + 1 < word.length && word[pos + 1] == 'H') {
            primary.append("F")
            alternate.append("F")
            pos + 2
        } else {
            primary.append("P")
            alternate.append("P")
            pos + 1
        }
    }

    private fun handleS(word: String, pos: Int, primary: StringBuilder, alternate: StringBuilder): Int {
        return when {
            pos + 1 < word.length && word[pos + 1] == 'H' -> {
                primary.append("S")
                alternate.append("S")
                pos + 2
            }
            else -> {
                primary.append("S")
                alternate.append("S")
                pos + 1
            }
        }
    }

    private fun handleT(word: String, pos: Int, primary: StringBuilder, alternate: StringBuilder): Int {
        return when {
            pos + 2 < word.length && word.substring(pos, pos + 3) == "TH" -> {
                primary.append("0") // Theta sound
                alternate.append("T")
                pos + 2
            }
            pos + 3 < word.length && (word.substring(pos, pos + 4) == "TION" || word.substring(pos, pos + 4) == "TIAL") -> {
                primary.append("S")
                alternate.append("S")
                pos + 3
            }
            else -> {
                primary.append("T")
                alternate.append("T")
                pos + 1
            }
        }
    }

    private fun handleW(word: String, pos: Int, primary: StringBuilder, alternate: StringBuilder): Int {
        return if (pos + 1 < word.length && isVowel(word[pos + 1])) {
            primary.append("W")
            alternate.append("W")
            pos + 1
        } else {
            pos + 1
        }
    }

    private fun handleX(word: String, pos: Int, primary: StringBuilder, alternate: StringBuilder): Int {
        return if (pos == 0) {
            primary.append("S")
            alternate.append("S")
            pos + 1
        } else {
            primary.append("KS")
            alternate.append("KS")
            pos + 1
        }
    }

    private fun isVowel(char: Char): Boolean {
        return char in "AEIOUY"
    }
}

data class DoubleMetaphoneResult(
    val primary: String,
    val alternate: String
)