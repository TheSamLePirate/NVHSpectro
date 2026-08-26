package com.example.nvhspectro

import com.example.nvhspectro.data.EmergenceReportEntry
import com.example.nvhspectro.data.KinematicsConfig
import com.example.nvhspectro.data.LoadedWavData
import com.example.nvhspectro.data.TrackedHarmonicTag
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class DisplayMode(val label: String) {
    ABSOLUTE("Absolue (dBFS)"),
    TTNR("TTNR (Emergence)")
}

enum class AudioSourceMode {
    LIVE,
    WAV_ANALYZER,
    VIDEO
}

/**
 * [plan 3.3, audit A1/L7] The shared measurement-session state machine —
 * the ONE holder of everything the three ViewModels (live, analyzer, report)
 * measure and display together: source mode, spectral histories, telemetry,
 * order tags/report, kinematics and display settings.
 *
 * Mode/config transitions run through here so the L7 contract has a single
 * enforcement point: registered resettables (the live DSP engines, the
 * analyzer's per-frame tag map) are wiped synchronously on every transition —
 * no EMA or tag built under a previous config survives into the next one.
 *
 * Pure Kotlin: owners register hooks for their Android-side effects
 * (mic enable, player release) instead of the session touching them.
 */
class MeasurementSession(scope: CoroutineScope) {

    // ------------------------------------------------------------------ mode
    private val _audioSourceMode = MutableStateFlow(AudioSourceMode.LIVE)
    val audioSourceMode: StateFlow<AudioSourceMode> = _audioSourceMode.asStateFlow()

    private val modeTransitionHooks = CopyOnWriteArrayList<(AudioSourceMode) -> Unit>()
    private val analysisResettables = CopyOnWriteArrayList<() -> Unit>()

    /** Called synchronously (registration order) on every [setAudioSourceMode]. */
    fun registerModeTransitionHook(hook: (AudioSourceMode) -> Unit): () -> Unit {
        modeTransitionHooks.add(hook)
        return { modeTransitionHooks.remove(hook) }
    }

    /** Wiped by [resetAnalysisState] — engines and per-stream caches register here [L7]. */
    fun registerAnalysisResettable(resettable: () -> Unit): () -> Unit {
        analysisResettables.add(resettable)
        return { analysisResettables.remove(resettable) }
    }

    fun setAudioSourceMode(mode: AudioSourceMode) {
        if (_audioSourceMode.value == mode) return
        _audioSourceMode.value = mode
        // Owners first (capture/GPS gating, player release), then state wipe —
        // same order as the historical monolith.
        modeTransitionHooks.forEach { it(mode) }
        resetAnalysisState()
        _analysisNotice.value = null
        _loadedWavData.value = null
        clearStreams()
    }

    /**
     * Analyzer-only escape hatch: the video/WAV load paths historically set
     * the mode WITHOUT the full transition wipe (they clear selectively
     * around the load). Never use for user-driven mode switches.
     */
    fun forceMode(mode: AudioSourceMode) {
        _audioSourceMode.value = mode
    }

    /** [L7] No ghost EMA/tags across any source or kinematics transition. */
    fun resetAnalysisState() {
        analysisResettables.forEach { it() }
        _latestTTNRSpectrum.value = FloatArray(0)
        _trackedHarmonicTags.value = emptyList()
    }

    fun clearEmergenceReport() {
        resetAnalysisState()
        _emergenceReportEntries.value = emptyList()
    }

    /** Clear the rolling spectral/telemetry streams (mode change, new load, FFT change). */
    fun clearStreams() {
        _fftHistoryAbsolute.value = emptyList()
        _fftHistoryTTNR.value = emptyList()
        _telemetryHistory.value = emptyList()
        _telemetryState.value = TelemetryData()
    }

    // --------------------------------------------------------------- display
    private val _displayMode = MutableStateFlow(DisplayMode.ABSOLUTE)
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
    }

    private val _isFrozen = MutableStateFlow(false)
    val isFrozen: StateFlow<Boolean> = _isFrozen.asStateFlow()

    fun toggleFreeze() {
        _isFrozen.value = !_isFrozen.value
    }

    // ------------------------------------------------------------- histories
    private val _fftHistoryAbsolute = MutableStateFlow<List<FloatArray>>(emptyList())
    val fftHistoryAbsolute: StateFlow<List<FloatArray>> = _fftHistoryAbsolute.asStateFlow()

    private val _fftHistoryTTNR = MutableStateFlow<List<FloatArray>>(emptyList())
    val fftHistoryTTNR: StateFlow<List<FloatArray>> = _fftHistoryTTNR.asStateFlow()

    val fftHistory: StateFlow<List<FloatArray>> =
        combine(_displayMode, _fftHistoryAbsolute, _fftHistoryTTNR) { mode, absList, ttnrList ->
            if (mode == DisplayMode.TTNR) ttnrList else absList
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _latestTTNRSpectrum = MutableStateFlow(FloatArray(0))
    val latestTTNRSpectrum: StateFlow<FloatArray> = _latestTTNRSpectrum.asStateFlow()

    fun setLatestTtnrSpectrum(spectrum: FloatArray) {
        _latestTTNRSpectrum.value = spectrum
    }

    /**
     * Live-mode append. [U9/U10 root fix, plan 3.4] Histories are CANONICAL
     * CHRONOLOGICAL — newest LAST — in every mode; only the draw layer knows
     * the live view scrolls right-to-left. Applies the 150 ms retro-unmask
     * patch to the k most recent TTNR rows and ring-trims to [maxHistory].
     */
    fun appendLiveFrame(
        magnitudes: FloatArray,
        ttnrSpectrum: FloatArray,
        retroUnmaskBins: List<Int>,
        retroRawRows: List<FloatArray>,
        maxHistory: Int
    ) {
        _latestTTNRSpectrum.value = ttnrSpectrum

        val curAbs = _fftHistoryAbsolute.value.toMutableList()
        curAbs.add(magnitudes)
        if (curAbs.size > maxHistory) curAbs.removeAt(0)
        _fftHistoryAbsolute.value = curAbs

        val curTtnr = _fftHistoryTTNR.value.toMutableList()
        curTtnr.add(ttnrSpectrum)
        if (retroUnmaskBins.isNotEmpty() && curTtnr.size >= LiveAnalysisEngine.RETRO_FRAMES) {
            // retroRawRows[k-1] is the raw spectrum k frames ago.
            for (k in 1..retroRawRows.size) {
                val idx = curTtnr.lastIndex - k
                if (idx >= 0) {
                    val pastRow = curTtnr[idx].clone()
                    val pastRaw = retroRawRows[k - 1]
                    for (binIdx in retroUnmaskBins) {
                        pastRow[binIdx] = pastRaw[binIdx]
                    }
                    curTtnr[idx] = pastRow
                }
            }
        }
        if (curTtnr.size > maxHistory) curTtnr.removeAt(0)
        _fftHistoryTTNR.value = curTtnr
    }

    /** Analyzer-mode replace: the full-file sweep result. */
    fun setWavAnalysis(absList: List<FloatArray>, ttnrList: List<FloatArray>) {
        _fftHistoryAbsolute.value = absList
        _fftHistoryTTNR.value = ttnrList
    }

    // ------------------------------------------------------------- telemetry
    private val _telemetryState = MutableStateFlow(TelemetryData())
    val telemetryState: StateFlow<TelemetryData> = _telemetryState.asStateFlow()

    fun setTelemetryState(data: TelemetryData) {
        _telemetryState.value = data
    }

    private val _telemetryHistory = MutableStateFlow<List<TelemetryData>>(emptyList())
    val telemetryHistory: StateFlow<List<TelemetryData>> = _telemetryHistory.asStateFlow()

    fun setTelemetryHistory(history: List<TelemetryData>) {
        _telemetryHistory.value = history
    }

    /** Chronological, like the spectral histories [plan 3.4]. */
    fun appendLiveTelemetry(data: TelemetryData, maxHistory: Int) {
        _telemetryState.value = data
        val cur = _telemetryHistory.value.toMutableList()
        cur.add(data)
        if (cur.size > maxHistory) cur.removeAt(0)
        _telemetryHistory.value = cur
    }

    // -------------------------------------------------------- order tracking
    private val _trackedHarmonicTags = MutableStateFlow<List<TrackedHarmonicTag>>(emptyList())
    val trackedHarmonicTags: StateFlow<List<TrackedHarmonicTag>> = _trackedHarmonicTags.asStateFlow()

    fun setTrackedHarmonicTags(tags: List<TrackedHarmonicTag>) {
        _trackedHarmonicTags.value = tags
    }

    private val _emergenceReportEntries = MutableStateFlow<List<EmergenceReportEntry>>(emptyList())
    val emergenceReportEntries: StateFlow<List<EmergenceReportEntry>> = _emergenceReportEntries.asStateFlow()

    fun setEmergenceReportEntries(entries: List<EmergenceReportEntry>) {
        _emergenceReportEntries.value = entries
    }

    // ------------------------------------------------------------ kinematics
    private val _kinematicsConfig = MutableStateFlow(KinematicsConfig())
    val kinematicsConfig: StateFlow<KinematicsConfig> = _kinematicsConfig.asStateFlow()

    /** Raw setter — callers own the [resetAnalysisState] + re-sweep choreography. */
    fun setKinematicsConfig(config: KinematicsConfig) {
        _kinematicsConfig.value = config
    }

    // ---------------------------------------------------------- loaded media
    private val _loadedWavData = MutableStateFlow<LoadedWavData?>(null)
    val loadedWavData: StateFlow<LoadedWavData?> = _loadedWavData.asStateFlow()

    fun setLoadedWavData(data: LoadedWavData?) {
        _loadedWavData.value = data
    }

    /** [C1] The rate every axis/order computation must use for the CURRENT source. */
    val analysisSampleRate: Int
        get() = if (_audioSourceMode.value == AudioSourceMode.LIVE) {
            AudioConfig.LIVE_SAMPLE_RATE_HZ
        } else {
            _loadedWavData.value?.sampleRate ?: AudioConfig.LIVE_SAMPLE_RATE_HZ
        }

    // -------------------------------------------------------------- settings
    private val _minDb = MutableStateFlow(-120.0)
    val minDb: StateFlow<Double> = _minDb.asStateFlow()

    private val _maxDb = MutableStateFlow(0.0)
    val maxDb: StateFlow<Double> = _maxDb.asStateFlow()

    private val _fftSize = MutableStateFlow(AudioConfig.DEFAULT_FFT_SIZE)
    val fftSize: StateFlow<Int> = _fftSize.asStateFlow()

    private val _minFreq = MutableStateFlow(0)
    val minFreq: StateFlow<Int> = _minFreq.asStateFlow()

    private val _maxFreq = MutableStateFlow(10000)
    val maxFreq: StateFlow<Int> = _maxFreq.asStateFlow()

    private val _timeWindowSec = MutableStateFlow(5.0)
    val timeWindowSec: StateFlow<Double> = _timeWindowSec.asStateFlow()

    /** Live scroll depth in frames for the current FFT size / time window. */
    val historySize: Int
        get() {
            val dt = (_fftSize.value / 2.0) / AudioConfig.LIVE_SAMPLE_RATE_HZ
            return (_timeWindowSec.value / dt).toInt().coerceAtLeast(10)
        }

    /** [C14] The dynamic range stays valid: min is clamped ≥ 5 dB below max. */
    fun updateDisplaySettings(newMinDb: Double, newMaxDb: Double, newMinFreq: Int, newMaxFreq: Int, newTimeWindowSec: Double) {
        _maxDb.value = newMaxDb
        _minDb.value = minOf(newMinDb, newMaxDb - 5.0)
        _minFreq.value = newMinFreq.coerceAtLeast(0)
        _maxFreq.value = newMaxFreq
        _timeWindowSec.value = newTimeWindowSec
    }

    /** Raw setter — the live owner guards C13 and restarts capture around it. */
    fun setFftSize(size: Int) {
        _fftSize.value = size
    }

    // -------------------------------------------------- emergence detector UI
    private val _isDetectorEnabled = MutableStateFlow(true)
    val isDetectorEnabled: StateFlow<Boolean> = _isDetectorEnabled.asStateFlow()

    private val _emergenceThresholdDb = MutableStateFlow(2.5)
    val emergenceThresholdDb: StateFlow<Double> = _emergenceThresholdDb.asStateFlow()

    private val _magnitudeGateDbFS = MutableStateFlow(-90.0)
    val magnitudeGateDbFS: StateFlow<Double> = _magnitudeGateDbFS.asStateFlow()

    fun updateDetectorSettings(enabled: Boolean, thresholdDb: Double, magnitudeGateDb: Double) {
        _isDetectorEnabled.value = enabled
        _emergenceThresholdDb.value = thresholdDb
        _magnitudeGateDbFS.value = magnitudeGateDb
    }

    // ---------------------------------------------------------------- notice
    // [C2/C3] The single user-facing message channel: import rejections,
    // truncation, capture errors, save results. Cleared on the next load.
    private val _analysisNotice = MutableStateFlow<String?>(null)
    val analysisNotice: StateFlow<String?> = _analysisNotice.asStateFlow()

    fun postNotice(message: String?) {
        _analysisNotice.value = message
    }

    fun dismissNotice() {
        _analysisNotice.value = null
    }
}
