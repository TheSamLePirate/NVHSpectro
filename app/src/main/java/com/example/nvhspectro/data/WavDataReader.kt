package com.example.nvhspectro.data

import android.content.Context
import android.net.Uri
import com.example.nvhspectro.GpsStatus
import com.example.nvhspectro.TelemetryData
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class LoadedWavData(
    val pcmSamples: ShortArray,
    val sampleRate: Int = 44100,
    val durationMs: Long,
    val telemetryList: List<TelemetryData> = emptyList()
)

object WavDataReader {

    fun readWavFile(file: File, jsonFile: File? = null): LoadedWavData? {
        if (!file.exists()) return null
        return try {
            file.inputStream().use { stream ->
                parseWavStream(stream, file.length(), jsonFile?.readText())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun readWavFromUri(context: Context, uri: Uri): LoadedWavData? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                parseWavStream(stream, -1, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseWavStream(stream: InputStream, fileLen: Long, jsonText: String?): LoadedWavData? {
        val header = ByteArray(44)
        var readHeader = 0
        while (readHeader < 44) {
            val r = stream.read(header, readHeader, 44 - readHeader)
            if (r <= 0) break
            readHeader += r
        }
        if (readHeader < 44) return null

        // Check 'RIFF' and 'WAVE'
        if (header[0] != 'R'.code.toByte() || header[8] != 'W'.code.toByte()) {
            return null
        }

        val sampleRate = ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int.coerceAtLeast(8000)
        val channels = ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt().coerceAtLeast(1)
        val bitsPerSample = ByteBuffer.wrap(header, 34, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt().coerceAtLeast(8)

        val rawBytes = stream.readBytes()
        val totalShorts = rawBytes.size / 2
        val pcm = ShortArray(totalShorts)

        val bb = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until totalShorts) {
            pcm[i] = bb.short
        }

        val durationMs = (totalShorts.toLong() * 1000L) / sampleRate

        val telemetryList = mutableListOf<TelemetryData>()
        if (!jsonText.isNull_or_blank()) {
            try {
                val json = JSONObject(jsonText!!)
                if (json.has("telemetryData")) {
                    val arr = json.getJSONArray("telemetryData")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val speedKmh = obj.optDouble("speedKmh", 0.0).toFloat()
                        val accG = obj.optDouble("accelerationG", 0.0).toFloat()
                        val lat = obj.optDouble("lat", 0.0)
                        val lng = obj.optDouble("lng", 0.0)
                        val statusStr = obj.optString("gpsStatus", "GOOD")
                        val gpsStatus = when (statusStr) {
                            "POOR" -> GpsStatus.POOR
                            "NONE" -> GpsStatus.NONE
                            else -> GpsStatus.GOOD
                        }
                        telemetryList.add(
                            TelemetryData(
                                speedKmh = speedKmh,
                                accelerationG = accG,
                                latitude = lat,
                                longitude = lng,
                                gpsStatus = gpsStatus
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return LoadedWavData(
            pcmSamples = pcm,
            sampleRate = sampleRate,
            durationMs = durationMs,
            telemetryList = telemetryList
        )
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
