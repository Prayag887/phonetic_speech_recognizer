//package com.example.phonetic_speech_recognizer
//
//import android.util.Log
//import java.util.Locale
//
//class Mapper {
//    val speakLoud: String = "Please speak clearly and loudly in a silent environment."
//
//    fun mapNumber(text: String, mapping: Map<String, List<String>>): String {
//        if (text.isBlank()) {
//            return speakLoud
//        }
//        Log.d("TAG", "numbers: ------------- $text")
//        val normalizedText = text.lowercase(Locale.ROOT)
//        val reversedMapping = mutableMapOf<String, MutableList<String>>()
//        mapping.forEach { (key, values) ->
//            values.forEach { pronunciation ->
//                val normalizedPronunciation = pronunciation.lowercase(Locale.ROOT)
//                reversedMapping.getOrPut(normalizedPronunciation) { mutableListOf() }.add(key)
//            }
//        }
//        val matchedKeys = reversedMapping[normalizedText]?.distinct() ?: listOf(text.uppercase(
//            Locale.ROOT))
//        return matchedKeys.joinToString(", ")
//    }
//
//    fun mapText(text: String, mapping: Map<String, List<String>>): String {
//        Log.d("TAG", "texts: ------------- $text")
//        val normalizedText = text.lowercase(Locale.ROOT)
//        val matchedKeys = mapping.entries
//            .filter { (_, pronunciations) ->
//                pronunciations.any { it.lowercase(Locale.ROOT) == normalizedText }
//            }
//            .map { it.key }
//            .distinct()
//
//        return if (matchedKeys.isNotEmpty()) {
//            matchedKeys.joinToString(", ")
//        } else {
//            speakLoud // if null
//        }
//    }
//}