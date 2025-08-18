package com.prayag.phonetic_speech_recognizer

import java.io.File
import java.io.FileOutputStream
import java.io.DataOutputStream
import java.io.BufferedOutputStream
import java.io.RandomAccessFile

class Utils {
    fun writeWavHeader(
        out: DataOutputStream,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        // RIFF header placeholder
        out.writeBytes("RIFF")
        out.writeInt(0) // file size placeholder
        out.writeBytes("WAVE")

        // fmt chunk
        out.writeBytes("fmt ")
        out.writeInt(Integer.reverseBytes(16)) // Subchunk1Size for PCM
        out.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt()) // PCM format
        out.writeShort(java.lang.Short.reverseBytes(channels.toShort()).toInt())
        out.writeInt(Integer.reverseBytes(sampleRate))
        val byteRate = sampleRate * channels * bitsPerSample / 8
        out.writeInt(Integer.reverseBytes(byteRate))
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        out.writeShort(java.lang.Short.reverseBytes(blockAlign).toInt())
        out.writeShort(java.lang.Short.reverseBytes(bitsPerSample.toShort()).toInt())

        // data chunk
        out.writeBytes("data")
        out.writeInt(0) // data size placeholder
    }

    fun updateWavHeader(file: File) {
        val raf = RandomAccessFile(file, "rw")
        try {
            val fileSize = raf.length().toInt()
            raf.seek(4)
            raf.write(intToLittleEndian(fileSize - 8)) // file size - 8

            raf.seek(40)
            raf.write(intToLittleEndian(fileSize - 44)) // data size
        } finally {
            raf.close()
        }
    }

    fun intToLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }
}