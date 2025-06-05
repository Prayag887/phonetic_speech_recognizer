package com.prayag.phonetic_speech_recognizer

class Soundex {
    private val soundexMapping = mapOf(
        'B' to '1', 'F' to '1', 'P' to '1', 'V' to '1', 'W' to '1',  // W ~ V in Punjabi
        'C' to '2', 'G' to '2', 'J' to '2', 'K' to '2', 'Q' to '2', 'S' to '2', 'X' to '2', 'Z' to '2',
        'D' to '3', 'T' to '3',
        'L' to '4',
        'M' to '5', 'N' to '5',
        'R' to '6'
    )

    private fun normalizeAccent(word: String): String {
        return word.lowercase()
            .replace("ph", "f")
            .replace("v", "b")
            .replace("z", "j")
            .replace("th", "t")
            .replace("w", "v")
            .replace("sh", "s")
            .replace("kh", "k")
            .replace("gh", "g")
            .replace("ch", "c")
    }

    fun encode(word: String): String {
        if (word.isEmpty()) return ""

        val normalized = normalizeAccent(word)
        val cleaned = normalized.uppercase().filter { it.isLetter() }
        if (cleaned.isEmpty()) return ""

        val result = StringBuilder()
        result.append(cleaned[0])
        var prevCode = soundexMapping[cleaned[0]]

        for (i in 1 until cleaned.length) {
            val char = cleaned[i]
            val code = soundexMapping[char]

            if (code != null && code != prevCode) {
                result.append(code)
                if (result.length == 4) break
            }

            if (code != null) {
                prevCode = code
            }
        }

        return result.toString().padEnd(4, '0').take(4)
    }
}