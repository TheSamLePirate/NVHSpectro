package com.example.nvhspectro

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nvhspectro.data.AudioFilter
import com.example.nvhspectro.data.FilterChain
import com.example.nvhspectro.data.FilterSpec
import com.example.nvhspectro.data.LoadedWavData
import com.example.nvhspectro.data.RecordingStore
import com.example.nvhspectro.data.TrackedHarmonicTag
import com.example.nvhspectro.data.VideoAudioExtractor
import com.example.nvhspectro.data.WavAudioWriter
import com.example.nvhspectro.data.WavDataReader
import com.example.nvhspectro.data.WavReadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [plan 3.3] The analyzer third of the historical MainViewModel: WAV/video
 * loading, the full-file sweep (computed in :core's WavAnalysis), playback
 * ownership and the audio filter chain. Shared state lives in [session].
 */
class AnalyzerViewModel(
    application: Application,
    val session: MeasurementSession,
) : AndroidViewModel(application) {
    // [L1/L2/L4] One owner of the MediaPlayer; release guaranteed in onCleared.
    private val playback = PlaybackController()

    /** Position/play-state driver for the loaded source [plan 3.3]. */
    val player =
        WavPlaybackCoordinator(
            scope = viewModelScope,
            playback = playback,
            loadedData = { session.loadedWavData.value },
            onFrameAt = ::processWavFrameAt,
        ).also { p -> playback.onCompletion = { p.onSourceCompleted() } }

    private var processingJob: Job? = null
    private var filterJob: Job? = null
    private var wavPlaybackJob: Job? = null
    private var wavLoadGeneration = 0
    private var wavTagsByFrame = emptyMap<Int, List<TrackedHarmonicTag>>()

    private val unregisterHook: () -> Unit
    private val unregisterResettable: () -> Unit

    init {
        unregisterHook =
            session.registerModeTransitionHook {
                processingJob?.cancel()
                player.stopAndRewind()
                playback.release()
                _loadedWavFileName.value = null
                _loadedVideoUri.value = null
                _loadedYouTubeUrl.value = null
            }
        unregisterResettable =
            session.registerAnalysisResettable {
                wavTagsByFrame = emptyMap()
            }
    }

    // ------------------------------------------------------------- UI state

    private val _loadedWavFileName = MutableStateFlow<String?>(null)
    val loadedWavFileName: StateFlow<String?> = _loadedWavFileName.asStateFlow()

    private val _loadedVideoUri = MutableStateFlow<Uri?>(null)
    val loadedVideoUri: StateFlow<Uri?> = _loadedVideoUri.asStateFlow()

    private val _loadedYouTubeUrl = MutableStateFlow<String?>(null)
    val loadedYouTubeUrl: StateFlow<String?> = _loadedYouTubeUrl.asStateFlow()

    private val _loadedVideoTitle = MutableStateFlow("")
    val loadedVideoTitle: StateFlow<String> = _loadedVideoTitle.asStateFlow()

    private val _isProcessingVideo = MutableStateFlow(false)
    val isProcessingVideo: StateFlow<Boolean> = _isProcessingVideo.asStateFlow()

    private val _processingEstimateMessage = MutableStateFlow<String?>(null)
    val processingEstimateMessage: StateFlow<String?> = _processingEstimateMessage.asStateFlow()

    private val _activeFilters = MutableStateFlow<List<AudioFilter>>(emptyList())
    val activeFilters: StateFlow<List<AudioFilter>> = _activeFilters.asStateFlow()

    /**
     * [GPS-4.4] How the loaded analysis's speeds were produced ("lissée
     * (RTS)" / "brute (interpolée)"); null = no telemetry. Reports print it
     * (plan 4.5 stamps the PDF).
     */
    private val _speedReconstructionStatus = MutableStateFlow<String?>(null)
    val speedReconstructionStatus: StateFlow<String?> = _speedReconstructionStatus.asStateFlow()

    // ------------------------------------------------------------ kinematics

    fun updateKinematicsConfig(config: com.example.nvhspectro.data.KinematicsConfig) {
        session.setKinematicsConfig(config)
        session.resetAnalysisState() // [L7] a new V1000 remaps every order index
        rerunWavTrackingIfLoaded()
    }

    fun updateSelectedTrackedOrder(order: Double) {
        session.setKinematicsConfig(session.kinematicsConfig.value.copy(selectedTrackedOrder = order))
        session.resetAnalysisState() // [L7]
        rerunWavTrackingIfLoaded()
    }

    private fun rerunWavTrackingIfLoaded() {
        if (session.audioSourceMode.value == AudioSourceMode.WAV_ANALYZER && session.loadedWavData.value != null) {
            recalculateOrderTrackingForWav()
            processWavFrameAt(player.positionMs.value)
        }
    }

    // --------------------------------------------------------------- filters

    fun addAudioFilter(filter: AudioFilter) {
        _activeFilters.value = _activeFilters.value + filter
        applyDigitalFilters()
    }

    fun removeAudioFilter(filterId: String) {
        _activeFilters.value = _activeFilters.value.filter { it.id != filterId }
        applyDigitalFilters()
    }

    /** [C10] Filters shape BOTH what the user hears and what the display analyzes. */
    private fun applyDigitalFilters() {
        val originalData = session.loadedWavData.value ?: return
        val filters = _activeFilters.value

        filterJob?.cancel() // [L2] no race on the temp file/player
        if (filters.isEmpty()) {
            filterJob =
                viewModelScope.launch {
                    val currentPos = playback.currentPositionMs
                    val wasPlaying = player.isPlaying.value || playback.isPlaying
                    playback.restoreOriginalSource()
                    playback.seekTo(currentPos)
                    if (wasPlaying) playback.play()
                    processFullWavSpectrogram(originalData) // [C10] display back to raw
                }
            return
        }

        filterJob =
            viewModelScope.launch(Dispatchers.Default) {
                // [L2] Capture position BEFORE the seconds-long filtering, not after.
                val currentPos = playback.currentPositionMs
                val wasPlaying = player.isPlaying.value || playback.isPlaying

                val specs = filters.map { FilterSpec(it.type, it.minFreq, it.maxFreq) }
                val filteredPcm =
                    FilterChain.renderFilteredPcm(
                        originalData.pcmSamples,
                        specs,
                        originalData.sampleRate.toDouble(),
                    ) { ensureActive() }

                val tempFile = File(getApplication<Application>().cacheDir, "filtered_playback.wav")
                WavAudioWriter.writePcmToWav(filteredPcm, tempFile, originalData.sampleRate)

                withContext(Dispatchers.Main) {
                    playback.setFilteredSource(tempFile)
                    playback.seekTo(currentPos)
                    if (wasPlaying) playback.play()
                    // [C10] The display analyzes the same signal the user hears.
                    processFullWavSpectrogram(originalData, analysisData = originalData.copy(pcmSamples = filteredPcm))
                }
            }
    }

    // --------------------------------------------------------------- loading

    fun loadWavFromUri(
        context: Context,
        uri: Uri,
        jsonUri: Uri? = null,
    ) {
        val gen = prepareForWavLoad()
        val name = uri.lastPathSegment?.substringAfterLast("/") ?: "fichier.wav"
        // [C16] Full-file read + parse on IO with the progress overlay.
        viewModelScope.launch(Dispatchers.IO) {
            val jsonText = jsonUri?.let { RecordingStore.readText(context, it) }
            val result = WavDataReader.readWavFromUri(context, uri, jsonText)
            withContext(Dispatchers.Main) {
                if (gen != wavLoadGeneration) return@withContext
                handleWavResult(result, name) { playback.setOriginalSource(context, null, uri) }
            }
        }
    }

    private fun prepareForWavLoad(): Int {
        processingJob?.cancel()
        player.stopAndRewind()
        session.dismissNotice()
        _loadedVideoUri.value = null
        _loadedYouTubeUrl.value = null
        session.clearStreams()
        _isProcessingVideo.value = true
        _processingEstimateMessage.value = "⏳ Chargement du fichier audio..."
        return ++wavLoadGeneration
    }

    /** [C2] Typed import result: success feeds the pipeline, rejection feeds the banner. */
    private suspend fun handleWavResult(
        result: WavReadResult,
        fileName: String,
        prepareSource: suspend () -> Long?,
    ) {
        when (result) {
            is WavReadResult.Success -> {
                val data = result.data
                val mediaDuration = prepareSource()
                // [C3] The playback timeline never exceeds the analyzed PCM range.
                val exactDuration = minOf(mediaDuration ?: data.durationMs, data.durationMs)
                if (result.truncatedToCap) {
                    session.postNotice("⏱️ Analyse limitée aux ${WavDataReader.MAX_DURATION_SEC / 60} premières minutes du fichier")
                }
                val updatedData = data.copy(durationMs = exactDuration)
                session.setLoadedWavData(updatedData)
                _loadedWavFileName.value = fileName
                player.resetPosition()
                processFullWavSpectrogram(updatedData)
                processWavFrameAt(0L)
            }
            is WavReadResult.Unsupported -> {
                _isProcessingVideo.value = false
                _processingEstimateMessage.value = null
                session.postNotice("⚠️ ${result.message}")
            }
            is WavReadResult.Error -> {
                _isProcessingVideo.value = false
                _processingEstimateMessage.value = null
                session.postNotice("❌ ${result.message}")
            }
        }
    }

    fun loadVideoFromUri(
        context: Context,
        uri: Uri,
    ) {
        processingJob?.cancel()
        player.stopAndRewind()
        session.dismissNotice()
        _loadedYouTubeUrl.value = null
        _loadedVideoUri.value = uri
        _loadedVideoTitle.value = uri.lastPathSegment?.substringAfterLast("/") ?: "Vidéo locale"
        session.forceMode(AudioSourceMode.VIDEO)
        session.clearStreams()
        _isProcessingVideo.value = true
        _processingEstimateMessage.value = "⏳ Extraction de l'audio de la vidéo..."

        viewModelScope.launch(Dispatchers.Default) {
            val data = VideoAudioExtractor.extractAudioFromVideoUri(context, uri)
            withContext(Dispatchers.Main) {
                if (data == null) {
                    _isProcessingVideo.value = false
                    _processingEstimateMessage.value = null
                    session.postNotice("❌ Extraction audio impossible depuis cette vidéo")
                } else {
                    val mediaDuration = playback.setOriginalSource(context, null, uri)
                    // [C3] Analyzed PCM bounds the timeline; a longer container means the cap was hit.
                    val exactDuration = minOf(mediaDuration ?: data.durationMs, data.durationMs)
                    if (mediaDuration != null && mediaDuration > data.durationMs + 1500L) {
                        session.postNotice("⏱️ Analyse limitée aux ${WavDataReader.MAX_DURATION_SEC / 60} premières minutes de la vidéo")
                    }
                    val updatedData = data.copy(durationMs = exactDuration)
                    session.setLoadedWavData(updatedData)
                    _loadedWavFileName.value = _loadedVideoTitle.value
                    player.resetPosition()
                    processFullWavSpectrogram(updatedData)
                    processWavFrameAt(0L)
                }
            }
        }
    }

    fun loadVideoFromYouTube(url: String) {
        processingJob?.cancel()
        player.stopAndRewind()
        _loadedVideoUri.value = null
        _loadedYouTubeUrl.value = url
        _loadedVideoTitle.value = "Vidéo YouTube"
        session.forceMode(AudioSourceMode.VIDEO)
        session.clearStreams()
    }

    // -------------------------------------------------------------- analysis

    /** [C10] analysisData carries the (possibly filtered) PCM; [data] stays the original. */
    private fun processFullWavSpectrogram(
        data: LoadedWavData,
        analysisData: LoadedWavData = data,
    ) {
        val durationSec = data.durationMs / 1000.0
        _isProcessingVideo.value = true
        _processingEstimateMessage.value =
            if (durationSec >= 60.0) {
                val min = (durationSec / 60).toInt()
                val sec = (durationSec % 60).toInt()
                val estimatedSec = String.format("%.1f", durationSec * 0.002).replace(",", ".")
                "⏳ Traitement du spectrogramme en cours...\nVidéo (${min}m ${sec}s) | Temps estimé: ~$estimatedSec s"
            } else {
                null
            }

        processingJob?.cancel()
        processingJob =
            viewModelScope.launch(Dispatchers.Default) {
                val spectro =
                    WavAnalysis.computeSpectrogram(
                        analysisData.pcmSamples,
                        data.sampleRate,
                        AudioConfig.WAV_FFT_SIZE,
                    ) { ensureActive() }

                withContext(Dispatchers.Main) {
                    if (spectro == null) {
                        session.setWavAnalysis(emptyList(), emptyList())
                        session.setTelemetryHistory(emptyList())
                    } else {
                        session.setWavAnalysis(spectro.absList, spectro.ttnrList)
                        if (data.telemetryList.isNotEmpty()) {
                            // [GPS-4.4] Deferred replay: RTS smoothing over the
                            // sidecar's raw fixes (legacy sidecars fall back to
                            // the historical interpolation); the status label
                            // is what reports must print.
                            val recon =
                                com.example.nvhspectro.data.SpeedReconstruction.reconstruct(
                                    data.telemetryList,
                                    data.telemetryAudioTimesNanos,
                                )
                            _speedReconstructionStatus.value = recon.statusLabel
                            session.postNotice("🛰️ Vitesse GNSS : ${recon.statusLabel}")
                            session.setLoadedWavData(data.copy(telemetryList = recon.telemetry))
                            session.setTelemetryHistory(recon.telemetry)
                        } else {
                            _speedReconstructionStatus.value = null
                            session.setTelemetryHistory(List(spectro.absList.size) { TelemetryData(gpsStatus = GpsStatus.NONE) })
                        }
                        recalculateOrderTrackingForWav()
                    }
                    _isProcessingVideo.value = false
                    _processingEstimateMessage.value = null
                    processWavFrameAt(0L)
                }
            }
    }

    private fun recalculateOrderTrackingForWav() {
        val config = session.kinematicsConfig.value
        val absHistory = session.fftHistoryAbsolute.value
        val ttnrHistory = session.fftHistoryTTNR.value
        val telemHistory = session.telemetryHistory.value
        val sampleRate = session.loadedWavData.value?.sampleRate ?: AudioConfig.LIVE_SAMPLE_RATE_HZ
        if (absHistory.isEmpty() || telemHistory.isEmpty() || !config.isEnabled) return

        session.clearEmergenceReport()
        val sweep = WavAnalysis.orderSweep(absHistory, ttnrHistory, telemHistory, config, sampleRate)
        session.setTelemetryHistory(sweep.updatedTelemetry)
        session.setLoadedWavData(session.loadedWavData.value?.copy(telemetryList = sweep.updatedTelemetry))
        wavTagsByFrame = sweep.tagsByFrame
        session.setEmergenceReportEntries(sweep.report)
    }

    private fun processWavFrameAt(posMs: Long) {
        val data = session.loadedWavData.value ?: return
        val teleHist = session.telemetryHistory.value
        val cursor =
            WavAnalysis.cursorStateAt(
                posMs = posMs,
                durationMs = data.durationMs,
                spectrogram =
                    WavAnalysis.Spectrogram(
                        session.fftHistoryAbsolute.value,
                        session.fftHistoryTTNR.value,
                        data.sampleRate,
                    ),
                telemetrySource = if (teleHist.isNotEmpty()) teleHist else data.telemetryList,
                config = session.kinematicsConfig.value,
            )
        cursor.ttnrSpectrum?.let { session.setLatestTtnrSpectrum(it) }
        session.setTelemetryState(cursor.telemetry)
        if (session.audioSourceMode.value != AudioSourceMode.LIVE) {
            session.setTrackedHarmonicTags(wavTagsByFrame[cursor.frameIndex] ?: emptyList())
        }
    }

    /** [L1] Release path on ViewModel death. */
    override fun onCleared() {
        unregisterHook()
        unregisterResettable()
        filterJob?.cancel()
        processingJob?.cancel()
        playback.release()
        super.onCleared()
    }
}
