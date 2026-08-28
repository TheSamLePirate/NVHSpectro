package com.example.nvhspectro.data

import android.content.Context
import com.example.nvhspectro.R

/**
 * Turns a typed [WavReadError] into the sentence an operator reads [§12, plan 4.4].
 *
 * The reader stays free of user-facing text — it reports what happened — and every message
 * lives in `strings.xml`, so the import failures a field user actually hits can be localised
 * (and reviewed) without touching the RIFF walker.
 */
fun WavReadError.messageIn(
    context: Context,
    detail: String,
): String =
    when (this) {
        WavReadError.FILE_NOT_FOUND -> context.getString(R.string.wav_err_file_not_found, detail)
        WavReadError.UNREADABLE -> context.getString(R.string.wav_err_unreadable, detail)
        WavReadError.INACCESSIBLE -> context.getString(R.string.wav_err_inaccessible)
        WavReadError.TOO_SHORT -> context.getString(R.string.wav_err_too_short)
        WavReadError.NOT_RIFF -> context.getString(R.string.wav_err_not_riff)
        WavReadError.FMT_TRUNCATED -> context.getString(R.string.wav_err_fmt_truncated)
        WavReadError.FMT_UNREADABLE -> context.getString(R.string.wav_err_fmt_unreadable)
        WavReadError.FMT_MISSING -> context.getString(R.string.wav_err_fmt_missing)
        WavReadError.DATA_MISSING -> context.getString(R.string.wav_err_data_missing)
        WavReadError.FORMAT_UNSUPPORTED -> context.getString(R.string.wav_err_format_unsupported, detail)
        WavReadError.BITS_UNSUPPORTED -> context.getString(R.string.wav_err_bits_unsupported, detail)
        WavReadError.CHANNELS_UNSUPPORTED -> context.getString(R.string.wav_err_channels_unsupported, detail)
        WavReadError.SAMPLE_RATE_INVALID -> context.getString(R.string.wav_err_sample_rate_invalid, detail)
        WavReadError.NO_DECODABLE_DATA -> context.getString(R.string.wav_err_no_decodable_data)
    }
