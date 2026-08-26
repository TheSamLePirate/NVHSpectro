package com.example.nvhspectro.data

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

object WavAudioWriter {

    /**
     * Écrit un tableau d'échantillons PCM 16-bit Mono dans un fichier WAV standard,
     * au sample rate fourni par l'appelant [audit C1 — jamais de valeur par défaut].
     */
    fun writePcmToWav(pcmData: ShortArray, outputFile: File, sampleRate: Int) {
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            writePcmToStream(pcmData, out, sampleRate)
        }
    }

    /** Stream variant for MediaStore targets [plan 1.7]. */
    fun writePcmToStream(pcmData: ShortArray, out: java.io.OutputStream, sampleRate: Int) {
        val totalAudioLen = pcmData.size * 2L // 2 bytes per 16-bit sample
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = sampleRate * channels * 2

        run {
            val header = ByteArray(44)

            // RIFF/WAVE header
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()

            // fmt chunk
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            header[16] = 16 // 16 for PCM
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1 // Audio format 1 = PCM
            header[21] = 0
            header[22] = channels.toByte()
            header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = (channels * 2).toByte() // block align
            header[33] = 0
            header[34] = 16 // bits per sample
            header[35] = 0

            // data chunk
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

            out.write(header, 0, 44)

            // Ecriture des échantillons PCM
            val buffer = ByteArray(pcmData.size * 2)
            for (i in pcmData.indices) {
                val value = pcmData[i].toInt()
                buffer[i * 2] = (value and 0x00FF).toByte()
                buffer[i * 2 + 1] = ((value shr 8) and 0x00FF).toByte()
            }
            out.write(buffer)
        }
    }
}
