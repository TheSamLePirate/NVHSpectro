package com.example.nvhspectro

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nvhspectro.data.KinematicsConfig
import com.example.nvhspectro.data.OrderSearchPolicy
import com.example.nvhspectro.data.usableForKinematics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * [plan 3.3] The live-capture third of the historical MainViewModel:
 * microphone pipeline (one consumer, DSP on the "nvh-dsp" thread [C5, C6]),
 * GPS speed, the 30 s field recorder, and live display settings. All shared
 * measurement state lives in [session].
 */
class LiveViewModel(
    application: Application,
    val session: MeasurementSession,
) : AndroidViewModel(application) {
    private val audioRepository = AudioRepository(application)

    // [GPS-3.2] Provider/permission state changes surface as user notices;
    // full tracking stays OFF until the GPS-5 A/B campaign proves it [GPS-3.4].
    private val speedProvider = SpeedProvider(application, onNotice = { msg -> session.postNotice(msg) })
    private val captureEngine = CaptureEngine(audioRepository) { msg -> session.postNotice(msg) }

    // [C6, L3] All live-DSP state lives in the engines, confined to nvh-dsp.
    @Volatile
    private var liveEngine = LiveAnalysisEngine(AudioConfig.DEFAULT_FFT_SIZE, AudioConfig.LIVE_SAMPLE_RATE_HZ)
    private val orderEngine = OrderTrackingEngine()

    private val analysisDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "nvh-dsp") }.asCoroutineDispatcher()

    private val unregisterHook: () -> Unit
    private val unregisterResettable: () -> Unit

    init {
        // [C7] Mic and GPS run only in LIVE mode; [L7] engines reset on transitions.
        unregisterHook =
            session.registerModeTransitionHook { mode ->
                captureEngine.setEnabled(mode == AudioSourceMode.LIVE)
                if (mode == AudioSourceMode.LIVE) speedProvider.start() else speedProvider.stop()
            }
        unregisterResettable =
            session.registerAnalysisResettable {
                liveEngine.reset()
                orderEngine.reset()
            }
        // [S1, plan 3.6] The engine follows session.fftSize wherever it is set
        // from — the settings dialog or the persisted-value restore at startup.
        viewModelScope.launch {
            session.fftSize.collect { size ->
                if (liveEngine.fftSize != size) {
                    liveEngine = LiveAnalysisEngine(size, AudioConfig.LIVE_SAMPLE_RATE_HZ)
                    captureEngine.setFftSize(size)
                }
            }
        }
        startLivePipeline()
        speedProvider.start()
    }

    /** ONE consumer for the app's lifetime; enable/fftSize changes flow through CaptureEngine. */
    private fun startLivePipeline() {
        viewModelScope.launch(analysisDispatcher) {
            var frameCount = 0L
            captureEngine.frames().collect { frame ->
                // [plan 2.6] Debug integrity log (~every 6 s): produced==consumed
                // proves the single consumer loses nothing; the thread name
                // proves DSP is off main.
                if (BuildConfig.DEBUG && ++frameCount % 256 == 0L) {
                    Log.d(
                        "LivePipeline",
                        "produced=${captureEngine.framesProduced.get()} " +
                            "consumed=${captureEngine.framesConsumed.get()} " +
                            "restarts=${captureEngine.captureRestarts.get()} " +
                            "thread=${Thread.currentThread().name}",
                    )
                }
                if (session.audioSourceMode.value == AudioSourceMode.LIVE) {
                    processLiveFrame(frame)
                }
            }
        }
        // ~1 Hz GPS-card refresh; per-frame values come from currentTelemetry().
        viewModelScope.launch {
            speedProvider.telemetry.collect { data ->
                if (session.audioSourceMode.value == AudioSourceMode.LIVE) {
                    val current = session.telemetryState.value
                    session.setTelemetryState(
                        data.copy(
                            ttnrDb = current.ttnrDb,
                            trackedOrderDbFS = current.trackedOrderDbFS,
                            trackedOrderEmergenceDb = current.trackedOrderEmergenceDb,
                        ),
                    )
                }
            }
        }
    }

    /** Runs on the dedicated DSP thread [C6]. */
    private fun processLiveFrame(frame: CapturedAudioFrame) {
        val audioBuffer = frame.pcm
        val kConfig = session.kinematicsConfig.value
        // [GPS-03] The speed estimate is evaluated at the CAPTURE time of this
        // window's center sample — a backlogged DSP queue can no longer pair a
        // spectrum with a speed newer than the analyzed sound.
        val telemetryNow = speedProvider.telemetryAt(frame.centerTimeNanos)
        val telemetryForCalc =
            if (kConfig.isEnabled) {
                telemetryNow
            } else {
                telemetryNow.copy(theoreticalSpeedKmh = telemetryNow.speedKmh)
            }

        if (_isAudioRecording.value) {
            val stepSize = audioBuffer.size / 2
            recordedPcmList.add(audioBuffer.copyOfRange(audioBuffer.size - stepSize, audioBuffer.size))
            recordedTelemetryList.add(telemetryForCalc)
            // [GPS-4.3] The paired audio BOOTTIME goes into the v3 sidecar.
            recordedFrameTimesNanos.add(frame.centerTimeNanos)
        }
        if (session.isFrozen.value) return

        val maxHist = session.historySize
        val result = liveEngine.processFrame(audioBuffer)
        val magnitudes = result.magnitudes
        val ttnrSpectrum = result.ttnrSpectrum
        session.appendLiveFrame(magnitudes, ttnrSpectrum, result.retroUnmaskBins, result.retroRawRows, maxHist)

        // Selected-order tracking (active only above 1 km/h).
        val speedKmh = if (kConfig.isEnabled) telemetryForCalc.theoreticalSpeedKmh else telemetryForCalc.speedKmh
        // [GPS-09, GPS-01] An INVALID estimate (no fix, or beyond the
        // prediction horizon) must never drive RPM/H1/orders — tracking is
        // suspended instead of coasting on a frozen speed.
        val speedUsable = telemetryForCalc.speedValidity.usableForKinematics
        val liveDf = (AudioConfig.LIVE_SAMPLE_RATE_HZ / 2.0) / ttnrSpectrum.size
        val tracked =
            if (kConfig.isEnabled && speedUsable && speedKmh > 1.0f) {
                trackedOrderReadout(kConfig, telemetryForCalc, speedKmh, magnitudes, ttnrSpectrum)
            } else {
                TrackedOrderReadout()
            }

        // Telemetry stays 1-to-1 with the audio display.
        val telemWithTtnr =
            telemetryForCalc.copy(
                ttnrDb = ttnrSpectrum.maxOrNull() ?: 0f,
                trackedOrderDbFS = tracked.dbFS,
                trackedOrderEmergenceDb = tracked.emergenceDb,
                trackedOrderIdentifiable = tracked.identifiable,
            )
        session.appendLiveTelemetry(telemWithTtnr, maxHist)

        // Harmonic detection / emergence report [A2, plan 3.2] — the same
        // engine code as the WAV sweep, on the live-owned instance.
        if (kConfig.isEnabled && speedUsable && speedKmh > 1.0f) {
            runHarmonicDetection(kConfig, speedKmh, magnitudes, ttnrSpectrum, liveDf)
        }
    }

    private class TrackedOrderReadout(
        val dbFS: Double = NO_SIGNAL_DBFS,
        val emergenceDb: Double = 0.0,
        val identifiable: Boolean = true,
    )

    /**
     * [GPS-10, GPS-4.2] Tracked-order readout behind the σ-driven search
     * window: wide enough to contain the true line, bounded so it cannot pick
     * a neighbouring order's — beyond the bound the readout SUSPENDS.
     */
    private fun trackedOrderReadout(
        kConfig: KinematicsConfig,
        telemetry: TelemetryData,
        speedKmh: Float,
        magnitudes: FloatArray,
        ttnrSpectrum: FloatArray,
    ): TrackedOrderReadout {
        val h1FreqHz = kConfig.calculateH1FreqHz(speedKmh)
        val liveDf = (AudioConfig.LIVE_SAMPLE_RATE_HZ / 2.0) / ttnrSpectrum.size
        val sigmaF =
            telemetry.theoreticalSpeedSigmaKmh?.let {
                OrderSearchPolicy.sigmaOrderFreqHz(
                    kConfig.selectedTrackedOrder,
                    it.toDouble(),
                    kConfig.getEffectiveV1000(),
                )
            }
        val window =
            OrderSearchPolicy.windowFor(
                sigmaFreqHz = sigmaF,
                h1FreqHz = h1FreqHz,
                dfHz = liveDf,
                legacyRadiusBins = OrderTrackingEngine.TRACKED_SEARCH_RADIUS_FRAME_BINS,
            )
        return when {
            h1FreqHz < 0.5 -> TrackedOrderReadout()
            !window.identifiable -> TrackedOrderReadout(identifiable = false)
            else -> {
                val levels =
                    OrderTrackingEngine.searchTrackedOrder(
                        magnitudes,
                        ttnrSpectrum,
                        kConfig.selectedTrackedOrder * h1FreqHz,
                        liveDf,
                        window.radiusBins,
                    )
                TrackedOrderReadout(levels.dbFS, levels.emergenceDb, identifiable = true)
            }
        }
    }

    /** The per-frame detection step, gated by [processLiveFrame] on speed validity [GPS-09]. */
    private fun runHarmonicDetection(
        kConfig: KinematicsConfig,
        speedKmh: Float,
        magnitudes: FloatArray,
        ttnrSpectrum: FloatArray,
        liveDf: Double,
    ) {
        val h1FreqHz = kConfig.calculateH1FreqHz(speedKmh)
        if (h1FreqHz < 0.5) return
        val reportList = session.emergenceReportEntries.value.toMutableList()
        session.setTrackedHarmonicTags(
            orderEngine.step(
                OrderTrackingEngine.Frame(
                    ttnrRow = ttnrSpectrum,
                    absRow = magnitudes,
                    df = liveDf,
                    speedKmh = speedKmh,
                    rpm = kConfig.calculateRpm(speedKmh),
                    h1FreqHz = h1FreqHz,
                ),
                nowMs = System.currentTimeMillis(),
                holdMs = (kConfig.holdTimeSec * 1000.0).toLong().coerceAtLeast(1000L),
                targetOrders = kConfig.parsedTargetOrders(),
                activeTags = session.trackedHarmonicTags.value,
                report = reportList,
            ),
        )
        session.setEmergenceReportEntries(reportList)
    }

    // ------------------------------------------------------ display settings

    fun updateSettings(
        newMinDb: Double,
        newMaxDb: Double,
        newFftSize: Int,
        newMinFreq: Int,
        newMaxFreq: Int,
        newTimeWindow: Double,
    ) {
        session.updateDisplaySettings(newMinDb, newMaxDb, newMinFreq, newMaxFreq, newTimeWindow)
        if (session.fftSize.value != newFftSize) {
            // [C13] FFT size is fixed at WAV_FFT_SIZE outside LIVE; applying the
            // live setting there wiped the loaded spectrogram with no re-render.
            if (session.audioSourceMode.value != AudioSourceMode.LIVE) return
            // [C5] The fftSize collector swaps the engine; flatMapLatest restarts capture.
            session.setFftSize(newFftSize)
            session.clearStreams()
        }
    }

    fun updateDetectorSettings(
        enabled: Boolean,
        thresholdDb: Double,
        magnitudeGateDb: Double,
    ) = session.updateDetectorSettings(enabled, thresholdDb, magnitudeGateDb)

    // ---------------------------------------------------- display preferences

    private val _selectedMetric = MutableStateFlow(com.example.nvhspectro.ui.TelemetryMetric.SPEED)
    val selectedMetric: StateFlow<com.example.nvhspectro.ui.TelemetryMetric> = _selectedMetric.asStateFlow()

    fun selectMetric(metric: com.example.nvhspectro.ui.TelemetryMetric) {
        _selectedMetric.value = metric
    }

    private val _showH1Overlay = MutableStateFlow(false)
    val showH1Overlay: StateFlow<Boolean> = _showH1Overlay.asStateFlow()

    fun toggleH1Overlay() {
        _showH1Overlay.value = !_showH1Overlay.value
    }

    private val _projectedOrder = MutableStateFlow(1.0)
    val projectedOrder: StateFlow<Double> = _projectedOrder.asStateFlow()

    fun setProjectedOrder(order: Double) {
        _projectedOrder.value = order
    }

    // ------------------------------------------- field recorder (max 30 s)

    private val _isAudioRecording = MutableStateFlow(false)
    val isAudioRecording: StateFlow<Boolean> = _isAudioRecording.asStateFlow()

    private val _recordingElapsedSec = MutableStateFlow(0)
    val recordingElapsedSec: StateFlow<Int> = _recordingElapsedSec.asStateFlow()

    private val _showSaveRecordingDialog = MutableStateFlow(false)
    val showSaveRecordingDialog: StateFlow<Boolean> = _showSaveRecordingDialog.asStateFlow()

    private val recordedPcmList = java.util.Collections.synchronizedList(mutableListOf<ShortArray>())
    private val recordedTelemetryList = java.util.Collections.synchronizedList(mutableListOf<TelemetryData>())
    private val recordedFrameTimesNanos = java.util.Collections.synchronizedList(mutableListOf<Long>())
    private var audioRecordingTimerJob: Job? = null

    fun toggleAudioRecording() {
        if (_isAudioRecording.value) stopAudioRecording() else startAudioRecording()
    }

    private fun startAudioRecording() {
        if (_isAudioRecording.value) return
        recordedPcmList.clear()
        recordedTelemetryList.clear()
        recordedFrameTimesNanos.clear()
        _recordingElapsedSec.value = 0
        _isAudioRecording.value = true

        audioRecordingTimerJob?.cancel()
        audioRecordingTimerJob =
            viewModelScope.launch {
                while (_isAudioRecording.value && _recordingElapsedSec.value < MAX_RECORDING_SEC) {
                    delay(1000L)
                    if (_isAudioRecording.value) {
                        _recordingElapsedSec.value += 1
                    }
                }
                if (_isAudioRecording.value && _recordingElapsedSec.value >= MAX_RECORDING_SEC) {
                    stopAudioRecording()
                }
            }
    }

    private fun stopAudioRecording() {
        if (!_isAudioRecording.value) return
        _isAudioRecording.value = false
        audioRecordingTimerJob?.cancel()
        audioRecordingTimerJob = null
        if (recordedPcmList.isNotEmpty()) {
            _showSaveRecordingDialog.value = true
        }
    }

    fun cancelSaveAudioRecording() {
        recordedPcmList.clear()
        recordedTelemetryList.clear()
        recordedFrameTimesNanos.clear()
        _showSaveRecordingDialog.value = false
    }

    fun saveAudioRecording(userCustomName: String) {
        val rawName = userCustomName.trim()
        val cleanName = if (rawName.isEmpty()) "Essai" else rawName.take(20).replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
        // [S3] Millisecond suffix: two saves in the same second no longer collide.
        val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH'h'mm'm'ss's'SSS", Locale.US).format(Date())
        val baseName = "${cleanName}_$timeStamp"
        _showSaveRecordingDialog.value = false

        // [C4] MediaStore on IO — the old direct write ran on main and lost
        // data silently under scoped storage.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fullPcm: ShortArray
                synchronized(recordedPcmList) {
                    val totalSamples = recordedPcmList.sumOf { it.size }
                    fullPcm = ShortArray(totalSamples)
                    var offset = 0
                    for (block in recordedPcmList) {
                        System.arraycopy(block, 0, fullPcm, offset, block.size)
                        offset += block.size
                    }
                }
                com.example.nvhspectro.data.RecordingStore.saveRecording(
                    context = getApplication(),
                    baseName = baseName,
                    pcm = fullPcm,
                    sampleRate = AudioConfig.LIVE_SAMPLE_RATE_HZ,
                    telemetryJson = buildTelemetryJson(baseName),
                )
                withContext(Dispatchers.Main) {
                    recordedPcmList.clear()
                    recordedTelemetryList.clear()
                    recordedFrameTimesNanos.clear()
                    session.postNotice("✅ Enregistrement sauvegardé : $baseName")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // PCM is kept so the user can retry the save.
                    _showSaveRecordingDialog.value = true
                    session.postNotice("❌ Sauvegarde impossible : ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    /**
     * [S2, GPS-4.3] Schema v3: per-sample estimated speed + σ + validity +
     * paired audio BOOTTIME, estimator identity, capture-time speed status.
     */
    private fun buildTelemetryJson(baseName: String): String {
        val samples = synchronized(recordedTelemetryList) { recordedTelemetryList.toList() }
        val audioTimes = synchronized(recordedFrameTimesNanos) { recordedFrameTimesNanos.toList() }
        return com.example.nvhspectro.data.TelemetryCodec.encodeV3(
            com.example.nvhspectro.data.TelemetryCodec.EncodeRequest(
                folderName = baseName,
                durationSec = _recordingElapsedSec.value,
                sampleRate = AudioConfig.LIVE_SAMPLE_RATE_HZ,
                captureSource = audioRepository.captureSourceLabel,
                appVersion = BuildConfig.VERSION_NAME,
                speedEstimator = speedProvider.estimatorDescription,
            ),
            samples = samples,
            audioTimesNanos = audioTimes,
        )
    }

    /** [L1] Every owned resource has a release path on ViewModel death. */
    override fun onCleared() {
        unregisterHook()
        unregisterResettable()
        captureEngine.setEnabled(false)
        speedProvider.shutdown()
        audioRepository.stopAudioCapture()
        analysisDispatcher.close()
        super.onCleared()
    }

    companion object {
        const val MAX_RECORDING_SEC = 30
        private const val NO_SIGNAL_DBFS = -120.0
    }
}
