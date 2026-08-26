package com.example.nvhspectro

import android.app.Application
import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import java.io.File
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import com.example.nvhspectro.data.AudioFilter
import com.example.nvhspectro.data.BiQuadFilter
import com.example.nvhspectro.data.WavAudioWriter
import androidx.lifecycle.viewModelScope
import com.example.nvhspectro.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

enum class DisplayMode(val label: String) {
    ABSOLUTE("Absolue (dBFS)"),
    TTNR("TTNR (Emergence)")
}

enum class AudioSourceMode {
    LIVE,
    WAV_ANALYZER,
    VIDEO
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val audioRepository = AudioRepository(application)
    private val speedProvider = SpeedProvider(application)
    private val captureEngine = CaptureEngine(audioRepository) { msg -> _analysisNotice.value = msg }

    // [C6, L3] Tout l'etat DSP live vit dans LiveAnalysisEngine, confine au thread nvh-dsp.
    @Volatile
    private var liveEngine = LiveAnalysisEngine(AudioConfig.DEFAULT_FFT_SIZE, AudioConfig.LIVE_SAMPLE_RATE_HZ)
    private var processingJob: kotlinx.coroutines.Job? = null
    
    // États Kinématiques GMPe & Rapport d'Émergence
    private val _kinematicsConfig = MutableStateFlow(KinematicsConfig())
    val kinematicsConfig: StateFlow<KinematicsConfig> = _kinematicsConfig.asStateFlow()

    private val _activeFilters = MutableStateFlow<List<AudioFilter>>(emptyList())
    val activeFilters: StateFlow<List<AudioFilter>> = _activeFilters.asStateFlow()
    fun addAudioFilter(filter: AudioFilter) {
        _activeFilters.value = _activeFilters.value + filter
        applyDigitalFilters()
    }

    fun removeAudioFilter(filterId: String) {
        _activeFilters.value = _activeFilters.value.filter { it.id != filterId }
        applyDigitalFilters()
    }
    
    /**
     * [C10] Filters now shape BOTH what the user hears and what the display
     * analyzes — the old version reprocessed the UNFILTERED spectrogram while
     * playing filtered audio. _loadedWavData always keeps the original PCM so
     * every filter change re-renders from the raw signal.
     */
    private fun applyDigitalFilters() {
        val originalData = _loadedWavData.value ?: return
        val filters = _activeFilters.value
        val context = getApplication<android.app.Application>()

        // [L2] Rapid filter toggles no longer race on the temp file/player.
        filterJob?.cancel()

        if (filters.isEmpty()) {
            filterJob = viewModelScope.launch {
                val currentPos = playback.currentPositionMs
                val wasPlaying = _isWavPlaying.value || playback.isPlaying
                playback.restoreOriginalSource()
                playback.seekTo(currentPos)
                if (wasPlaying) playback.play()
                processFullWavSpectrogram(originalData) // [C10] display back to raw
            }
            return
        }

        filterJob = viewModelScope.launch(Dispatchers.Default) {
            // [L2] Capture position BEFORE the seconds-long filtering, not after.
            val currentPos = playback.currentPositionMs
            val wasPlaying = _isWavPlaying.value || playback.isPlaying

            val filteredPcm = renderFilteredPcm(originalData, filters, coroutineContext)

            val tempFile = File(context.cacheDir, "filtered_playback.wav")
            WavAudioWriter.writePcmToWav(filteredPcm, tempFile, originalData.sampleRate)

            withContext(Dispatchers.Main) {
                playback.setFilteredSource(tempFile)
                playback.seekTo(currentPos)
                if (wasPlaying) playback.play()
                // [C10] The display analyzes the same signal the user hears.
                processFullWavSpectrogram(
                    originalData,
                    analysisData = originalData.copy(pcmSamples = filteredPcm)
                )
            }
        }
    }

    /** Runs the biquad chain over the whole PCM. Cancellation-cooperative. */
    private fun renderFilteredPcm(
        data: LoadedWavData,
        filters: List<AudioFilter>,
        context: kotlin.coroutines.CoroutineContext
    ): ShortArray {
        val pcm = data.pcmSamples
        val filteredPcm = ShortArray(pcm.size)
        val sr = data.sampleRate.toDouble()
        // Q d'un Butterworth 8e ordre (valide pour les sections LP/HP uniquement)
        val qFactors = listOf(0.509795579, 0.601344887, 0.899976223, 2.562915448)
        val biquads = filters.flatMap { filter ->
            when (filter.type) {
                // [D4] Un vrai passe-bande Butterworth 8e ordre = HP(minFreq)
                // 8e ordre cascade avec LP(maxFreq) 8e ordre. L'ancienne
                // cascade de 4 sections band-pass identiques n'etait pas
                // Butterworth (leur formule ignore q).
                FilterType.BAND_PASS ->
                    qFactors.map { q ->
                        BiQuadFilter(FilterType.HIGH_PASS, filter.minFreq.toDouble(), filter.maxFreq.toDouble(), sr, q)
                    } + qFactors.map { q ->
                        BiQuadFilter(FilterType.LOW_PASS, filter.minFreq.toDouble(), filter.maxFreq.toDouble(), sr, q)
                    }
                // [D4] Le coupe-bande reste une cascade de 4 notchs identiques
                // (q inutilise par la formule) : cela approfondit/elargit la
                // rejection — assume, et documente honnetement.
                else ->
                    qFactors.map { q ->
                        BiQuadFilter(filter.type, filter.minFreq.toDouble(), filter.maxFreq.toDouble(), sr, q)
                    }
            }
        }
        for (i in pcm.indices) {
            if (i and 0xFFFF == 0) context.ensureActive() // [L2] cancellable mid-render
            var sample = pcm[i].toDouble()
            for (bq in biquads) {
                sample = bq.processSample(sample)
            }
            filteredPcm[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return filteredPcm
    }

    private val _trackedHarmonicTags = MutableStateFlow<List<TrackedHarmonicTag>>(emptyList())
    val trackedHarmonicTags: StateFlow<List<TrackedHarmonicTag>> = _trackedHarmonicTags.asStateFlow()

    private val _emergenceReportEntries = MutableStateFlow<List<EmergenceReportEntry>>(emptyList())
    val emergenceReportEntries: StateFlow<List<EmergenceReportEntry>> = _emergenceReportEntries.asStateFlow()

    private var wavTagsByFrame = emptyMap<Int, List<TrackedHarmonicTag>>()

    fun updateKinematicsConfig(config: KinematicsConfig) {
        _kinematicsConfig.value = config
        resetAnalysisState() // [L7] a new V1000 remaps every order index
        if (_audioSourceMode.value == AudioSourceMode.WAV_ANALYZER && _loadedWavData.value != null) {
            recalculateOrderTrackingForWav()
            processWavFrameAt(_wavPlaybackPositionMs.value)
        }
    }

    fun updateSelectedTrackedOrder(order: Double) {
        val currentConfig = _kinematicsConfig.value
        _kinematicsConfig.value = currentConfig.copy(selectedTrackedOrder = order)
        resetAnalysisState() // [L7]
        if (_audioSourceMode.value == AudioSourceMode.WAV_ANALYZER && _loadedWavData.value != null) {
            recalculateOrderTrackingForWav()
            processWavFrameAt(_wavPlaybackPositionMs.value)
        }
    }

    fun clearEmergenceReport() {
        liveEngine.reset()
        _emergenceReportEntries.value = emptyList()
        _trackedHarmonicTags.value = emptyList()
    }

    /**
     * [L7] Full analysis-state wipe on every source/config transition — the
     * audit's mode-transition table is the contract: no EMA built under a
     * previous config may survive into the next one (ghost tags).
     */
    private fun resetAnalysisState() {
        liveEngine.reset()
        wavTagsByFrame = emptyMap()
        _latestTTNRSpectrum.value = DoubleArray(0)
        _trackedHarmonicTags.value = emptyList()
    }
    
    // Etats de l'UI
    private val _isReportModeActive = MutableStateFlow(false)
    val isReportModeActive: StateFlow<Boolean> = _isReportModeActive.asStateFlow()

    private val _manualTrackedOrders = MutableStateFlow<List<SmartTrackedOrder>>(emptyList())
    val manualTrackedOrders: StateFlow<List<SmartTrackedOrder>> = _manualTrackedOrders.asStateFlow()
    
    private val _selectedValidatedOrder = MutableStateFlow<SmartTrackedOrder?>(null)
    val selectedValidatedOrder: StateFlow<SmartTrackedOrder?> = _selectedValidatedOrder.asStateFlow()

    private val _isBrillanceModeEnabled = MutableStateFlow(false)
    val isBrillanceModeEnabled: StateFlow<Boolean> = _isBrillanceModeEnabled.asStateFlow()

    fun toggleBrillanceMode() {
        _isBrillanceModeEnabled.value = !_isBrillanceModeEnabled.value
    }


    private val _currentUserPoints = MutableStateFlow<List<ManualOrderAnchor>>(emptyList())
    
    private val _reportFftHistory = MutableStateFlow<List<DoubleArray>>(emptyList())
    val reportFftHistory: StateFlow<List<DoubleArray>> = _reportFftHistory.asStateFlow()

    private val _reportFftHistoryAbsolute = MutableStateFlow<List<DoubleArray>>(emptyList())
    val reportFftHistoryAbsolute: StateFlow<List<DoubleArray>> = _reportFftHistoryAbsolute.asStateFlow()

    private val _reportFftHistoryTTNR = MutableStateFlow<List<DoubleArray>>(emptyList())
    val reportFftHistoryTTNR: StateFlow<List<DoubleArray>> = _reportFftHistoryTTNR.asStateFlow()

    private val _isDrawingMode = MutableStateFlow(false)
    val isDrawingMode: StateFlow<Boolean> = _isDrawingMode.asStateFlow()

    fun toggleDrawingMode() {
        _isDrawingMode.value = !_isDrawingMode.value
    }

    val currentUserPoints: StateFlow<List<ManualOrderAnchor>> = _currentUserPoints.asStateFlow()

    private val _currentSmartPath = MutableStateFlow<List<ManualOrderAnchor>>(emptyList())
    val currentSmartPath: StateFlow<List<ManualOrderAnchor>> = _currentSmartPath.asStateFlow()

    private val _telemetryState = MutableStateFlow(TelemetryData())
    val telemetryState: StateFlow<TelemetryData> = _telemetryState.asStateFlow()
    
    private val _displayMode = MutableStateFlow(DisplayMode.ABSOLUTE)
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    fun toggleDisplayMode() {
        _displayMode.value = if (_displayMode.value == DisplayMode.ABSOLUTE) DisplayMode.TTNR else DisplayMode.ABSOLUTE
    }

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
    }

    private val _fftHistoryAbsolute = MutableStateFlow<List<DoubleArray>>(emptyList())
    val fftHistoryAbsolute: StateFlow<List<DoubleArray>> = _fftHistoryAbsolute.asStateFlow()

    private val _fftHistoryTTNR = MutableStateFlow<List<DoubleArray>>(emptyList())
    val fftHistoryTTNR: StateFlow<List<DoubleArray>> = _fftHistoryTTNR.asStateFlow()

    val fftHistory: StateFlow<List<DoubleArray>> = combine(_displayMode, _fftHistoryAbsolute, _fftHistoryTTNR) { mode, absList, ttnrList ->
        if (mode == DisplayMode.TTNR) ttnrList else absList
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    private val _latestTTNRSpectrum = MutableStateFlow<DoubleArray>(DoubleArray(0))
    val latestTTNRSpectrum: StateFlow<DoubleArray> = _latestTTNRSpectrum.asStateFlow()
    
    private val _telemetryHistory = MutableStateFlow<List<TelemetryData>>(emptyList())
    val telemetryHistory: StateFlow<List<TelemetryData>> = _telemetryHistory.asStateFlow()

    private val _selectedMetric = MutableStateFlow(com.example.nvhspectro.ui.TelemetryMetric.SPEED)
    val selectedMetric: StateFlow<com.example.nvhspectro.ui.TelemetryMetric> = _selectedMetric.asStateFlow()

    fun selectMetric(metric: com.example.nvhspectro.ui.TelemetryMetric) {
        _selectedMetric.value = metric
    }

    // Mode Source Audio (Live vs Analyseur WAV)
    private val _audioSourceMode = MutableStateFlow(AudioSourceMode.LIVE)
    val audioSourceMode: StateFlow<AudioSourceMode> = _audioSourceMode.asStateFlow()

    private val _showAudioModeMenu = MutableStateFlow(false)
    val showAudioModeMenu: StateFlow<Boolean> = _showAudioModeMenu.asStateFlow()

    private val _showWavSelectionDialog = MutableStateFlow(false)
    val showWavSelectionDialog: StateFlow<Boolean> = _showWavSelectionDialog.asStateFlow()

    private val _loadedWavData = MutableStateFlow<LoadedWavData?>(null)
    val loadedWavData: StateFlow<LoadedWavData?> = _loadedWavData.asStateFlow()

    // Bandeau d'information analyse [C2/C3]: rejets de fichiers non supportés,
    // erreurs de lecture, troncature 5 min. Effacé au chargement suivant.
    private val _analysisNotice = MutableStateFlow<String?>(null)
    val analysisNotice: StateFlow<String?> = _analysisNotice.asStateFlow()

    fun dismissAnalysisNotice() {
        _analysisNotice.value = null
    }

    private val _loadedWavFileName = MutableStateFlow<String?>(null)
    val loadedWavFileName: StateFlow<String?> = _loadedWavFileName.asStateFlow()

    private val _wavPlaybackPositionMs = MutableStateFlow(0L)
    val wavPlaybackPositionMs: StateFlow<Long> = _wavPlaybackPositionMs.asStateFlow()

    private val _isWavPlaying = MutableStateFlow(false)
    val isWavPlaying: StateFlow<Boolean> = _isWavPlaying.asStateFlow()

    private var wavPlaybackJob: Job? = null

    fun toggleAudioModeMenu() {
        _showAudioModeMenu.value = !_showAudioModeMenu.value
    }

    fun setAudioSourceMode(mode: AudioSourceMode) {
        if (_audioSourceMode.value == mode) return
        _audioSourceMode.value = mode

        // [C7] Micro et GPS actifs uniquement en mode LIVE.
        captureEngine.setEnabled(mode == AudioSourceMode.LIVE && _isRecording.value)
        if (mode == AudioSourceMode.LIVE) speedProvider.start() else speedProvider.stop()

        processingJob?.cancel()

        stopWavPlayback()
        playback.release()
        resetAnalysisState() // [L7] no ghost EMA/tags across mode transitions
        _analysisNotice.value = null
        _loadedWavData.value = null
        _loadedWavFileName.value = null
        _loadedVideoUri.value = null
        _loadedYouTubeUrl.value = null
        _fftHistoryAbsolute.value = emptyList()
        _fftHistoryTTNR.value = emptyList()
        _telemetryHistory.value = emptyList()
        _telemetryState.value = TelemetryData()
    }

    fun openWavSelectionDialog() {
        _showWavSelectionDialog.value = true
    }

    fun closeWavSelectionDialog() {
        _showWavSelectionDialog.value = false
    }

    // [L1/L2/L4] Un seul proprietaire du MediaPlayer : prepare async, sources
    // originale/filtree explicites, release garanti dans onCleared.
    private val playback = PlaybackController().also { pc ->
        pc.onCompletion = {
            _isWavPlaying.value = false
            _loadedWavData.value?.let { d -> _wavPlaybackPositionMs.value = d.durationMs }
        }
    }
    private var filterJob: Job? = null

    // [C16] Guards against a stale load completing after a newer one started.
    private var wavLoadGeneration = 0

    fun loadWavFromUri(context: android.content.Context, uri: android.net.Uri, jsonUri: android.net.Uri? = null) {
        val gen = prepareForWavLoad()
        val name = uri.lastPathSegment?.substringAfterLast("/") ?: "fichier.wav"
        // [C16] Full-file read + parse ran on the main thread (ANR on long files);
        // now on IO with the existing progress overlay.
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
        _showWavSelectionDialog.value = false
        processingJob?.cancel()
        stopWavPlayback()
        _analysisNotice.value = null
        _loadedVideoUri.value = null
        _loadedYouTubeUrl.value = null
        _fftHistoryAbsolute.value = emptyList()
        _fftHistoryTTNR.value = emptyList()
        _telemetryHistory.value = emptyList()
        _telemetryState.value = TelemetryData()
        _isProcessingVideo.value = true
        _processingEstimateMessage.value = "⏳ Chargement du fichier audio..."
        return ++wavLoadGeneration
    }

    /** [C2] Typed import result: success feeds the pipeline, rejection feeds the notice banner. */
    private suspend fun handleWavResult(result: WavReadResult, fileName: String, prepareSource: suspend () -> Long?) {
        when (result) {
            is WavReadResult.Success -> {
                val data = result.data
                val mediaDuration = prepareSource()
                // [C3] The playback timeline must never exceed the analyzed PCM range —
                // the old max-of-both desynchronized playhead and spectrum for >5-min files.
                val exactDuration = minOf(mediaDuration ?: data.durationMs, data.durationMs)
                if (result.truncatedToCap) {
                    _analysisNotice.value =
                        "⏱️ Analyse limitée aux ${WavDataReader.MAX_DURATION_SEC / 60} premières minutes du fichier"
                }
                val updatedData = data.copy(durationMs = exactDuration)

                _loadedWavData.value = updatedData
                _loadedWavFileName.value = fileName
                _wavPlaybackPositionMs.value = 0L
                processFullWavSpectrogram(updatedData)
                processWavFrameAt(0L)
            }
            is WavReadResult.Unsupported -> {
                _isProcessingVideo.value = false
                _processingEstimateMessage.value = null
                _analysisNotice.value = "⚠️ ${result.message}"
            }
            is WavReadResult.Error -> {
                _isProcessingVideo.value = false
                _processingEstimateMessage.value = null
                _analysisNotice.value = "❌ ${result.message}"
            }
        }
    }

    // Mode Vidéo
    private val _loadedVideoUri = MutableStateFlow<android.net.Uri?>(null)
    val loadedVideoUri: StateFlow<android.net.Uri?> = _loadedVideoUri.asStateFlow()

    private val _loadedYouTubeUrl = MutableStateFlow<String?>(null)
    val loadedYouTubeUrl: StateFlow<String?> = _loadedYouTubeUrl.asStateFlow()

    private val _loadedVideoTitle = MutableStateFlow<String>("")
    val loadedVideoTitle: StateFlow<String> = _loadedVideoTitle.asStateFlow()

    private val _showVideoSelectionDialog = MutableStateFlow(false)
    val showVideoSelectionDialog: StateFlow<Boolean> = _showVideoSelectionDialog.asStateFlow()

    fun openVideoSelectionDialog() {
        _showVideoSelectionDialog.value = true
    }

    fun dismissVideoSelectionDialog() {
        _showVideoSelectionDialog.value = false
    }

    fun loadVideoFromUri(context: android.content.Context, uri: android.net.Uri) {
        _showVideoSelectionDialog.value = false
        processingJob?.cancel()
        stopWavPlayback()
        _analysisNotice.value = null
        _loadedYouTubeUrl.value = null
        _loadedVideoUri.value = uri
        _loadedVideoTitle.value = uri.lastPathSegment?.substringAfterLast("/") ?: "Vidéo locale"
        _audioSourceMode.value = AudioSourceMode.VIDEO
        _fftHistoryAbsolute.value = emptyList()
        _fftHistoryTTNR.value = emptyList()
        _telemetryHistory.value = emptyList()
        _telemetryState.value = TelemetryData()
        _isProcessingVideo.value = true
        _processingEstimateMessage.value = "⏳ Extraction de l'audio de la vidéo..."

        viewModelScope.launch(Dispatchers.Default) {
            val data = com.example.nvhspectro.data.VideoAudioExtractor.extractAudioFromVideoUri(context, uri)
            if (data == null) {
                withContext(Dispatchers.Main) {
                    _isProcessingVideo.value = false
                    _processingEstimateMessage.value = null
                    _analysisNotice.value = "❌ Extraction audio impossible depuis cette vidéo"
                }
            }
            if (data != null) {
                withContext(Dispatchers.Main) {
                    val mediaDuration = playback.setOriginalSource(context, null, uri)
                    // [C3] Analyzed PCM bounds the timeline; a longer container means the cap was hit.
                    val exactDuration = minOf(mediaDuration ?: data.durationMs, data.durationMs)
                    if (mediaDuration != null && mediaDuration > data.durationMs + 1500L) {
                        _analysisNotice.value =
                            "⏱️ Analyse limitée aux ${WavDataReader.MAX_DURATION_SEC / 60} premières minutes de la vidéo"
                    }
                    val updatedData = data.copy(durationMs = exactDuration)
                    _loadedWavData.value = updatedData
                    _loadedWavFileName.value = _loadedVideoTitle.value
                    _wavPlaybackPositionMs.value = 0L
                    processFullWavSpectrogram(updatedData)
                    processWavFrameAt(0L)
                }
            }
        }
    }

    fun loadVideoFromYouTube(url: String) {
        _showVideoSelectionDialog.value = false
        processingJob?.cancel()
        stopWavPlayback()
        _loadedVideoUri.value = null
        _loadedYouTubeUrl.value = url
        _loadedVideoTitle.value = "Vidéo YouTube"
        _audioSourceMode.value = AudioSourceMode.VIDEO
        _fftHistoryAbsolute.value = emptyList()
        _fftHistoryTTNR.value = emptyList()
        _telemetryHistory.value = emptyList()
        _telemetryState.value = TelemetryData()
    }

    // Mode Vidéo & Traitement
    private val _isProcessingVideo = MutableStateFlow(false)
    val isProcessingVideo: StateFlow<Boolean> = _isProcessingVideo.asStateFlow()

    private val _processingEstimateMessage = MutableStateFlow<String?>(null)
    val processingEstimateMessage: StateFlow<String?> = _processingEstimateMessage.asStateFlow()

    private fun recalculateOrderTrackingForWav() {
        val config = _kinematicsConfig.value
        val absHistory = _fftHistoryAbsolute.value
        val ttnrHistory = _fftHistoryTTNR.value
        val telemHistory = _telemetryHistory.value
        val sampleRate = _loadedWavData.value?.sampleRate ?: AudioConfig.LIVE_SAMPLE_RATE_HZ
        
        if (absHistory.isEmpty() || telemHistory.isEmpty() || !config.isEnabled) return
        
        val targetOrder = config.selectedTrackedOrder
        
        val updatedHistory = telemHistory.mapIndexed { i, telem ->
            val theoSpeed = telem.theoreticalSpeedKmh
            val absIdx = TimelineMapper.mapIndex(i, telemHistory.size, absHistory.size)
            val absArr = absHistory[absIdx]
            val ttnrArr = ttnrHistory[absIdx]
            
            var bestAbs = -120.0
            var bestTtnr = 0.0
            
            if (theoSpeed > 1.0f) {
                val targetFreq = config.calculateH1FreqHz(theoSpeed) * targetOrder.toFloat()
                if (targetFreq > 0f && targetFreq < sampleRate / 2) {
                    val nyquist = sampleRate / 2.0
                    val targetBin = ((targetFreq / nyquist) * absArr.size).toInt()
                    
                    val searchRadius = 3
                    val startBin = (targetBin - searchRadius).coerceAtLeast(0)
                    val endBin = (targetBin + searchRadius).coerceAtMost(absArr.size - 1)
                    
                    for (b in startBin..endBin) {
                        if (absArr[b] > bestAbs) {
                            bestAbs = absArr[b]
                        }
                        if (ttnrArr[b] > bestTtnr) {
                            bestTtnr = ttnrArr[b]
                        }
                    }
                }
            }
            telem.copy(trackedOrderDbFS = bestAbs, trackedOrderEmergenceDb = bestTtnr)
        }
        
        _telemetryHistory.value = updatedHistory
        _loadedWavData.value = _loadedWavData.value?.copy(telemetryList = updatedHistory)

        // --- FULL SWEEP FOR TAGS & REPORT (EMA ORDER SPECTRUM) ---
        clearEmergenceReport()
        val threshDb = 2.0 // Fixed low threshold for EMA
        val targetOrders = config.parsedTargetOrders()
        val isWhitelistActive = targetOrders.isNotEmpty()
        val maxHoldMs = (config.holdTimeSec * 1000.0).toLong().coerceAtLeast(1000L)

        val stepDurationMs = (AudioConfig.WAV_FFT_SIZE / 2).toDouble() / sampleRate * 1000.0
        
        var currentTags = listOf<TrackedHarmonicTag>()
        val currentReportList = mutableListOf<EmergenceReportEntry>()
        val newWavTagsByFrame = mutableMapOf<Int, List<TrackedHarmonicTag>>()
        
        val localEmaSpectrum = FloatArray(1000) { 0f }
        val alpha = 0.10f
        val binCount = if (absHistory.isNotEmpty()) absHistory[0].size else 1
        // [C1] Was hard-coded to the live capture rate while the correct sampleRate was
        // read above — the order sweep was wrong for any non-44.1k file.
        val nyquist = sampleRate / 2.0
        val df = nyquist / binCount

        for (frameIdx in absHistory.indices) {
            val nowMs = (frameIdx * stepDurationMs).toLong()
            val telemIdx = TimelineMapper.mapIndex(frameIdx, absHistory.size, updatedHistory.size)
            val speedKmh = updatedHistory[telemIdx].theoreticalSpeedKmh
            val currentRpm = config.calculateRpm(speedKmh)
            
            val newDetectedTags = mutableListOf<TrackedHarmonicTag>()

            if (speedKmh > 1.0f && currentRpm > 100.0) {
                val h1FreqHz = currentRpm / 60.0
                val currentFrameSpectrum = FloatArray(1000) { 0f }
                val ttnrRow = ttnrHistory[frameIdx]
                val absRow = absHistory[frameIdx]
                
                for (i in 0 until binCount) {
                    val ttnrVal = ttnrRow[i]
                    if (ttnrVal > 0) {
                        val freqHz = i * df
                        val order = freqHz / h1FreqHz
                        val orderIndex = (order * 10.0).toInt()
                        if (orderIndex in 0..999) {
                            currentFrameSpectrum[orderIndex] = maxOf(currentFrameSpectrum[orderIndex], ttnrVal.toFloat())
                        }
                    }
                }
                
                for (j in 0..999) {
                    localEmaSpectrum[j] = localEmaSpectrum[j] * (1 - alpha) + currentFrameSpectrum[j] * alpha
                }
                
                for (j in 0..999) {
                    if (localEmaSpectrum[j] > threshDb) {
                        var isLocalMax = true
                        for (k in maxOf(0, j - 4)..minOf(999, j + 4)) {
                            if (localEmaSpectrum[k] > localEmaSpectrum[j]) {
                                isLocalMax = false
                                break
                            } else if (k < j && localEmaSpectrum[k] == localEmaSpectrum[j]) {
                                isLocalMax = false
                                break
                            }
                        }
                        
                        if (isLocalMax) {
                            val orderValue = j / 10.0
                            var isAllowed = true
                            if (isWhitelistActive) {
                                isAllowed = targetOrders.any { Math.abs(it - orderValue) <= 0.25 }
                            } else {
                                if (localEmaSpectrum[j] < 3.0f) isAllowed = false
                            }
                            
                            if (isAllowed) {
                                val orderName = "Ordre H$orderValue"
                                val freqHz = (orderValue * h1FreqHz).toInt()
                                val binIndex = (freqHz / df).toInt().coerceIn(0, binCount - 1)
                                
                                val tag = TrackedHarmonicTag(
                                    orderName = orderName,
                                    orderValue = orderValue,
                                    freqHz = freqHz,
                                    ttnrDb = localEmaSpectrum[j].toDouble(),
                                    absDbFS = absRow[binIndex],
                                    speedKmh = speedKmh,
                                    rpm = currentRpm,
                                    binIndex = binIndex,
                                    lastSeenTimestampMs = nowMs
                                )
                                newDetectedTags.add(tag)
                                
                                val existingReport = currentReportList.find {
                                    Math.abs(it.orderValue - orderValue) <= 0.2 &&
                                    (speedKmh <= it.maxSpeedKmh + 15f && speedKmh >= it.minSpeedKmh - 15f)
                                }
                                if (existingReport != null) {
                                    existingReport.minSpeedKmh = minOf(existingReport.minSpeedKmh, speedKmh)
                                    existingReport.maxSpeedKmh = maxOf(existingReport.maxSpeedKmh, speedKmh)
                                    existingReport.minRpm = minOf(existingReport.minRpm, currentRpm.toInt())
                                    existingReport.maxRpm = maxOf(existingReport.maxRpm, currentRpm.toInt())
                                    existingReport.minFreqHz = minOf(existingReport.minFreqHz, freqHz)
                                    existingReport.maxFreqHz = maxOf(existingReport.maxFreqHz, freqHz)
                                    existingReport.maxEmergenceDb = maxOf(existingReport.maxEmergenceDb, localEmaSpectrum[j].toDouble())
                                    existingReport.countDetections++
                                    existingReport.lastTimestampMs = nowMs
                                } else {
                                    currentReportList.add(EmergenceReportEntry(
                                        orderName = orderName,
                                        orderValue = orderValue,
                                        minSpeedKmh = speedKmh,
                                        maxSpeedKmh = speedKmh,
                                        minRpm = currentRpm.toInt(),
                                        maxRpm = currentRpm.toInt(),
                                        minFreqHz = freqHz,
                                        maxFreqHz = freqHz,
                                        maxEmergenceDb = localEmaSpectrum[j].toDouble(),
                                        countDetections = 1,
                                        lastTimestampMs = nowMs
                                    ))
                                }
                            }
                        }
                    }
                }
            }
            
            val updatedTagMap = currentTags
                .filter { nowMs - it.lastSeenTimestampMs < maxHoldMs }
                .associateBy { it.orderName }
                .toMutableMap()
            
            for (tag in newDetectedTags) {
                updatedTagMap[tag.orderName] = tag
            }
            
            currentTags = updatedTagMap.values.sortedBy { it.orderValue }
            newWavTagsByFrame[frameIdx] = currentTags
        }
        
        wavTagsByFrame = newWavTagsByFrame
        _emergenceReportEntries.value = currentReportList
    }

    /** [C10] analysisData carries the (possibly filtered) PCM to analyze; [data] stays the original. */
    private fun processFullWavSpectrogram(data: LoadedWavData, analysisData: LoadedWavData = data) {
        val durationSec = data.durationMs / 1000.0
        val isLongVideo = (durationSec >= 60.0)

        _isProcessingVideo.value = true
        if (isLongVideo) {
            val min = (durationSec / 60).toInt()
            val sec = (durationSec % 60).toInt()
            val estimatedSec = String.format("%.1f", durationSec * 0.002).replace(",", ".")
            _processingEstimateMessage.value = "⏳ Traitement du spectrogramme en cours...\nVidéo (${min}m ${sec}s) | Temps estimé: ~${estimatedSec} s"
        } else {
            _processingEstimateMessage.value = null
        }

        processingJob?.cancel()
        processingJob = viewModelScope.launch(Dispatchers.Default) {
            val sampleRate = data.sampleRate
            val fftN = AudioConfig.WAV_FFT_SIZE
            val stepSize = fftN / 2
            val totalSamples = analysisData.pcmSamples.size

            if (totalSamples < fftN) {
                withContext(Dispatchers.Main) {
                    _fftHistoryAbsolute.value = emptyList()
                    _fftHistoryTTNR.value = emptyList()
                    _telemetryHistory.value = emptyList()
                    _isProcessingVideo.value = false
                    _processingEstimateMessage.value = null
                }
                return@launch
            }

            val localFftProcessor = FFTProcessor(fftN, sampleRate)
            val frameCount = ((totalSamples - fftN) / stepSize).coerceAtLeast(1)
            val absList = ArrayList<DoubleArray>(frameCount)
            val ttnrList = ArrayList<DoubleArray>(frameCount)

            val frameBuffer = ShortArray(fftN)
            for (i in 0 until frameCount) {
                val startSample = i * stepSize
                val avail = totalSamples - startSample
                val copyLen = avail.coerceAtMost(fftN)
                if (copyLen > 0) {
                    System.arraycopy(analysisData.pcmSamples, startSample, frameBuffer, 0, copyLen)
                } else {
                    java.util.Arrays.fill(frameBuffer, 0.toShort())
                }
                val magnitudes = localFftProcessor.processFFT(frameBuffer)
                

                val rawTtnr = localFftProcessor.computeTTNR(magnitudes)

                absList.add(magnitudes)
                ttnrList.add(rawTtnr)
            }

            withContext(Dispatchers.Main) {
                _fftHistoryAbsolute.value = absList
                _fftHistoryTTNR.value = ttnrList
                if (data.telemetryList.isNotEmpty()) {
                    val hasTheo = data.telemetryList.any { it.theoreticalSpeedKmh > 0.1f }
                    val finalTelemList = if (!hasTheo && data.telemetryList.size > 1) {
                        val smoothList = data.telemetryList.toMutableList()
                        val corners = mutableListOf<Int>()
                        corners.add(0)
                        for (i in 1 until smoothList.size) {
                            if (smoothList[i].speedKmh != smoothList[i - 1].speedKmh) {
                                corners.add(i)
                            }
                        }
                        if (corners.last() != smoothList.size - 1) {
                            corners.add(smoothList.size - 1)
                        }
                        
                        for (c in 0 until corners.size - 1) {
                            val startIdx = corners[c]
                            val endIdx = corners[c + 1]
                            val startSpeed = smoothList[startIdx].speedKmh
                            val endSpeed = smoothList[endIdx].speedKmh
                            val range = (endIdx - startIdx).toFloat().coerceAtLeast(1f)
                            for (i in startIdx..endIdx) {
                                val fraction = (i - startIdx).toFloat() / range
                                val interp = startSpeed + fraction * (endSpeed - startSpeed)
                                smoothList[i] = smoothList[i].copy(theoreticalSpeedKmh = interp)
                            }
                        }
                        smoothList
                    } else {
                        data.telemetryList
                    }
                    
                    _loadedWavData.value = data.copy(telemetryList = finalTelemList)
                    _telemetryHistory.value = finalTelemList
                } else {
                    _telemetryHistory.value = List(frameCount) { TelemetryData(gpsStatus = GpsStatus.NONE) }
                }
                
                recalculateOrderTrackingForWav()

                _isProcessingVideo.value = false
                _processingEstimateMessage.value = null
                processWavFrameAt(0L)
            }
        }
    }

    fun toggleWavPlayPause() {
        if (_isWavPlaying.value) {
            pauseWavPlayback()
        } else {
            startWavPlayback()
        }
    }

    fun startWavPlayback() {
        val data = _loadedWavData.value ?: return
        if (_isWavPlaying.value) return
        _isWavPlaying.value = true

        if (_wavPlaybackPositionMs.value >= data.durationMs) {
            _wavPlaybackPositionMs.value = 0L
            playback.seekTo(0)
        } else {
            playback.seekTo(_wavPlaybackPositionMs.value.toInt())
        }
        playback.play()

        wavPlaybackJob?.cancel()
        wavPlaybackJob = viewModelScope.launch {
            val sampleRate = data.sampleRate
            val stepSize = AudioConfig.WAV_FFT_SIZE / 2
            val stepMs = ((stepSize.toDouble() / sampleRate.toDouble()) * 1000.0).toLong().coerceAtLeast(15L)

            while (_isWavPlaying.value && _wavPlaybackPositionMs.value < data.durationMs) {
                val currentMpPos = playback.currentPositionMs.toLong()
                _wavPlaybackPositionMs.value = currentMpPos.coerceIn(0L, data.durationMs)
                processWavFrameAt(_wavPlaybackPositionMs.value)
                delay(stepMs)
            }

            if (_wavPlaybackPositionMs.value >= data.durationMs) {
                _isWavPlaying.value = false
                _wavPlaybackPositionMs.value = data.durationMs
                // [C3] For cap-truncated files the media outlives the analyzed range —
                // stop the player at the analyzed end instead of playing on unanalyzed audio.
                playback.pause()
            }
        }
    }

    fun pauseWavPlayback() {
        _isWavPlaying.value = false
        wavPlaybackJob?.cancel()
        wavPlaybackJob = null
        playback.pause()
    }

    fun stopWavPlayback() {
        pauseWavPlayback()
        _wavPlaybackPositionMs.value = 0L
        // [L6] Player stays prepared (restart works without a reload);
        // release happens on source change, mode exit, and onCleared.
        playback.seekTo(0)
    }

    fun seekWavTo(posMs: Long) {
        val data = _loadedWavData.value ?: return
        val clamped = posMs.coerceIn(0L, data.durationMs)
        _wavPlaybackPositionMs.value = clamped
        playback.seekTo(clamped.toInt())
        processWavFrameAt(clamped)
    }

    fun stepWavSeconds(offsetSec: Int) {
        val data = _loadedWavData.value ?: return
        val newPos = (_wavPlaybackPositionMs.value + offsetSec * 1000L).coerceIn(0L, data.durationMs)
        seekWavTo(newPos)
    }

    private fun processWavFrameAt(posMs: Long) {
        val data = _loadedWavData.value ?: return
        val totalMs = data.durationMs.coerceAtLeast(1L)
        val ratio = (posMs.toDouble() / totalMs.toDouble()).coerceIn(0.0, 1.0)

        val absList = _fftHistoryAbsolute.value
        val ttnrList = _fftHistoryTTNR.value
        var currentAbs = DoubleArray(0)
        var currentTtnr = DoubleArray(0)

        var frameIdx = 0
        if (absList.isNotEmpty()) {
            frameIdx = TimelineMapper.timeToIndex(posMs, totalMs, absList.size)
            if (frameIdx in absList.indices) {
                currentAbs = absList[frameIdx]
            }
            if (frameIdx in ttnrList.indices) {
                currentTtnr = ttnrList[frameIdx]
                _latestTTNRSpectrum.value = currentTtnr
            }
        }

        val teleHist = _telemetryHistory.value
        var telem = TelemetryData(gpsStatus = GpsStatus.NONE)
        val sourceList = if (teleHist.isNotEmpty()) teleHist else data.telemetryList

        if (sourceList.isNotEmpty()) {
            val exactIdx = ratio * (sourceList.size - 1)
            val idxBefore = exactIdx.toInt().coerceIn(0, sourceList.size - 1)
            val idxAfter = (idxBefore + 1).coerceIn(0, sourceList.size - 1)
            
            if (idxBefore != idxAfter) {
                val fraction = (exactIdx - idxBefore).toFloat()
                val telemBefore = sourceList[idxBefore]
                val telemAfter = sourceList[idxAfter]
                val interpSpeed = telemBefore.speedKmh + fraction * (telemAfter.speedKmh - telemBefore.speedKmh)
                val interpTheo = telemBefore.theoreticalSpeedKmh + fraction * (telemAfter.theoreticalSpeedKmh - telemBefore.theoreticalSpeedKmh)
                val finalTheo = if (interpTheo > 0.1f) interpTheo else interpSpeed
                telem = telemBefore.copy(theoreticalSpeedKmh = finalTheo)
            } else {
                val rawTelem = sourceList[idxBefore]
                val finalTheo = if (rawTelem.theoreticalSpeedKmh > 0.1f) rawTelem.theoreticalSpeedKmh else rawTelem.speedKmh
                telem = rawTelem.copy(theoreticalSpeedKmh = finalTheo)
            }
        }

        val kConfig = _kinematicsConfig.value
        val speedKmh = if (kConfig.isEnabled) telem.theoreticalSpeedKmh else telem.speedKmh
        if (kConfig.isEnabled && speedKmh > 1.0f && currentAbs.isNotEmpty() && currentTtnr.isNotEmpty()) {
            val h1FreqHz = kConfig.calculateH1FreqHz(speedKmh)
            if (h1FreqHz >= 0.5) {
                val sampleRate = _loadedWavData.value?.sampleRate ?: AudioConfig.LIVE_SAMPLE_RATE_HZ
                val nyquistFreq = sampleRate / 2.0
                val totalBins = currentAbs.size
                val df = nyquistFreq / totalBins
                val targetFreqHz = kConfig.selectedTrackedOrder * h1FreqHz
                val centerBin = Math.round(targetFreqHz / df).toInt().coerceIn(0, totalBins - 1)

                var maxMag = -120.0
                var maxEm = 0.0
                val searchMin = (centerBin - 1).coerceAtLeast(0)
                val searchMax = (centerBin + 1).coerceAtMost(totalBins - 1)

                for (b in searchMin..searchMax) {
                    if (b in currentAbs.indices && currentAbs[b] > maxMag) {
                        maxMag = currentAbs[b]
                    }
                    if (b in currentTtnr.indices && currentTtnr[b] > maxEm) {
                        maxEm = currentTtnr[b]
                    }
                }

                telem = telem.copy(
                    ttnrDb = (currentTtnr.maxOrNull() ?: 0.0).toFloat(),
                    trackedOrderDbFS = maxMag,
                    trackedOrderEmergenceDb = maxEm
                )
            }
        }

        _telemetryState.value = telem
        if (_audioSourceMode.value != AudioSourceMode.LIVE) {
            _trackedHarmonicTags.value = wavTagsByFrame[frameIdx] ?: emptyList()
        }
    }

    // Module Enregistrement Audio (Max 30s) & Télémétrie
    private val _isAudioRecording = MutableStateFlow(false)
    val isAudioRecording: StateFlow<Boolean> = _isAudioRecording.asStateFlow()

    private val _recordingElapsedSec = MutableStateFlow(0)
    val recordingElapsedSec: StateFlow<Int> = _recordingElapsedSec.asStateFlow()

    private val _showSaveRecordingDialog = MutableStateFlow(false)
    val showSaveRecordingDialog: StateFlow<Boolean> = _showSaveRecordingDialog.asStateFlow()

    private val recordedPcmList = java.util.Collections.synchronizedList(mutableListOf<ShortArray>())
    private val recordedTelemetryList = java.util.Collections.synchronizedList(mutableListOf<TelemetryData>())
    private var audioRecordingTimerJob: Job? = null

    fun toggleAudioRecording() {
        if (_isAudioRecording.value) {
            stopAudioRecording()
        } else {
            startAudioRecording()
        }
    }

    fun startAudioRecording() {
        if (_isAudioRecording.value) return
        recordedPcmList.clear()
        recordedTelemetryList.clear()
        _recordingElapsedSec.value = 0
        _isAudioRecording.value = true

        audioRecordingTimerJob?.cancel()
        audioRecordingTimerJob = viewModelScope.launch {
            while (_isAudioRecording.value && _recordingElapsedSec.value < 30) {
                delay(1000L)
                if (_isAudioRecording.value) {
                    _recordingElapsedSec.value += 1
                }
            }
            if (_isAudioRecording.value && _recordingElapsedSec.value >= 30) {
                stopAudioRecording()
            }
        }
    }

    fun stopAudioRecording() {
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
        _showSaveRecordingDialog.value = false
    }

    fun saveAudioRecording(userCustomName: String) {
        val rawName = userCustomName.trim()
        val cleanName = if (rawName.isEmpty()) "Essai" else rawName.take(20).replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
        // [S3] Millisecond suffix: two saves in the same second no longer collide.
        val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH'h'mm'm'ss's'SSS", Locale.US).format(Date())
        val baseName = "${cleanName}_$timeStamp"
        _showSaveRecordingDialog.value = false

        // [C4] Save via MediaStore on IO — the old direct-File write ran on the
        // main thread and lost data silently under scoped storage.
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
                RecordingStore.saveRecording(
                    context = getApplication(),
                    baseName = baseName,
                    pcm = fullPcm,
                    sampleRate = AudioConfig.LIVE_SAMPLE_RATE_HZ,
                    telemetryJson = buildTelemetryJson(baseName)
                )
                withContext(Dispatchers.Main) {
                    recordedPcmList.clear()
                    recordedTelemetryList.clear()
                    _analysisNotice.value = "✅ Enregistrement sauvegardé : $baseName"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // PCM is kept so the user can retry the save.
                    _showSaveRecordingDialog.value = true
                    _analysisNotice.value = "❌ Sauvegarde impossible : ${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
    }

    private fun buildTelemetryJson(baseName: String): String {
        val jsonContent = StringBuilder()
        jsonContent.append("{\n")
        jsonContent.append("  \"folderName\": \"$baseName\",\n")
        jsonContent.append("  \"durationSec\": ${_recordingElapsedSec.value},\n")
        jsonContent.append("  \"sampleRate\": ${AudioConfig.LIVE_SAMPLE_RATE_HZ},\n")
        jsonContent.append("  \"captureSource\": \"${audioRepository.captureSourceLabel}\",\n")
        jsonContent.append("  \"telemetryCount\": ${recordedTelemetryList.size},\n")
        jsonContent.append("  \"telemetryData\": [\n")

        synchronized(recordedTelemetryList) {
            val items = recordedTelemetryList.mapIndexed { idx, item ->
                "    {\"index\": $idx, \"speedKmh\": ${item.speedKmh}, \"accelerationG\": ${item.accelerationG}, \"lat\": ${item.latitude}, \"lng\": ${item.longitude}, \"gpsStatus\": \"${item.gpsStatus}\"}"
            }
            jsonContent.append(items.joinToString(",\n"))
        }
        jsonContent.append("\n  ]\n}")
        return jsonContent.toString()
    }
    
    // Paramètres réglables
    private val _minDb = MutableStateFlow(-120.0)
    val minDb: StateFlow<Double> = _minDb.asStateFlow()

    private val _maxDb = MutableStateFlow(0.0)
    val maxDb: StateFlow<Double> = _maxDb.asStateFlow()

    private val _fftSize = MutableStateFlow(2048)
    val fftSize: StateFlow<Int> = _fftSize.asStateFlow()

    private val _minFreq = MutableStateFlow(0)
    val minFreq: StateFlow<Int> = _minFreq.asStateFlow()

    private val _maxFreq = MutableStateFlow(10000)
    val maxFreq: StateFlow<Int> = _maxFreq.asStateFlow()

    private val _timeWindowSec = MutableStateFlow(5.0)
    val timeWindowSec: StateFlow<Double> = _timeWindowSec.asStateFlow()

    val historySize: Int
        get() {
            val dt = (_fftSize.value / 2.0) / AudioConfig.LIVE_SAMPLE_RATE_HZ
            return (_timeWindowSec.value / dt).toInt().coerceAtLeast(10)
        }

    // Paramètres du détecteur d'émergence automatique
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

    fun updateSettings(newMinDb: Double, newMaxDb: Double, newFftSize: Int, newMinFreq: Int, newMaxFreq: Int, newTimeWindow: Double) {
        // [C14] The dynamic range must stay valid (min at least 5 dB below max);
        // an inverted range corrupted every normalization in canvas/export/PDF.
        val safeMax = newMaxDb
        val safeMin = minOf(newMinDb, safeMax - 5.0)
        _minDb.value = safeMin
        _maxDb.value = safeMax
        _minFreq.value = newMinFreq.coerceAtLeast(0)
        _maxFreq.value = newMaxFreq
        _timeWindowSec.value = newTimeWindow
        if (_fftSize.value != newFftSize) {
            // [C13] FFT size is fixed at WAV_FFT_SIZE in analyzer/video mode; applying
            // the live setting there wiped the loaded spectrogram with no re-render.
            if (_audioSourceMode.value != AudioSourceMode.LIVE) return
            _fftSize.value = newFftSize
            // [C5] flatMapLatest restarts the capture cleanly - the old stop/start
            // here stacked a new consumer on every change.
            liveEngine = LiveAnalysisEngine(newFftSize, AudioConfig.LIVE_SAMPLE_RATE_HZ)
            captureEngine.setFftSize(newFftSize)
            _fftHistoryAbsolute.value = emptyList()
            _fftHistoryTTNR.value = emptyList()
            _telemetryHistory.value = emptyList()
        }
    }
    
    private val _isFrozen = MutableStateFlow(false)
    val isFrozen: StateFlow<Boolean> = _isFrozen.asStateFlow()

    fun toggleFreeze() {
        _isFrozen.value = !_isFrozen.value
    }
    
    private val _isRecording = MutableStateFlow(true)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private val _showH1Overlay = MutableStateFlow(false)
    val showH1Overlay: StateFlow<Boolean> = _showH1Overlay.asStateFlow()

    private val _projectedOrder = MutableStateFlow(1.0)
    val projectedOrder: StateFlow<Double> = _projectedOrder.asStateFlow()

    fun toggleH1Overlay() {
        _showH1Overlay.value = !_showH1Overlay.value
    }

    fun setProjectedOrder(order: Double) {
        _projectedOrder.value = order
    }


    fun toggleRecording() {
        if (_isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    // ------------------------------------------------------------------
    // Pipeline live [C5, C6, C7, plan 2.1/2.2/2.4]
    //
    // - CaptureEngine est l'unique proprietaire du micro (flatMapLatest :
    //   aucun producteur/consommateur empile, micro coupe hors mode LIVE).
    // - TOUT le DSP tourne sur le thread dedie "nvh-dsp" ; seules les
    //   ecritures StateFlow traversent vers le main thread.
    // - La vitesse vient PREDITE de SpeedProvider (estimateur alpha-beta sur
    //   Doppler GNSS) : le delai d'affichage de 1,2 s et l'interpolation
    //   wall-clock ont ete supprimes [G1-G4, L5].
    // ------------------------------------------------------------------

    private val analysisDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "nvh-dsp") }.asCoroutineDispatcher()

    init {
        startLivePipeline()
        speedProvider.start()
    }

    /** ONE consumer for the app's lifetime; enable/fftSize changes flow through CaptureEngine. */
    private fun startLivePipeline() {
        viewModelScope.launch(analysisDispatcher) {
            var frameCount = 0L
            captureEngine.frames().collect { audioBuffer ->
                // [plan 2.6] Debug integrity log (~every 6 s): produced==consumed
                // proves the single consumer loses nothing; the thread name
                // proves DSP is off main.
                if (BuildConfig.DEBUG && ++frameCount % 256 == 0L) {
                    android.util.Log.d(
                        "LivePipeline",
                        "produced=${captureEngine.framesProduced.get()} " +
                            "consumed=${captureEngine.framesConsumed.get()} " +
                            "restarts=${captureEngine.captureRestarts.get()} " +
                            "thread=${Thread.currentThread().name}"
                    )
                }
                if (_audioSourceMode.value == AudioSourceMode.LIVE) {
                    processLiveFrame(audioBuffer)
                }
            }
        }
        // ~1 Hz rafraichissement de la carte GPS ; les valeurs par frame
        // viennent de speedProvider.currentTelemetry().
        viewModelScope.launch {
            speedProvider.telemetry.collect { data ->
                if (_audioSourceMode.value == AudioSourceMode.LIVE) {
                    val current = _telemetryState.value
                    _telemetryState.value = data.copy(
                        ttnrDb = current.ttnrDb,
                        trackedOrderDbFS = current.trackedOrderDbFS,
                        trackedOrderEmergenceDb = current.trackedOrderEmergenceDb
                    )
                }
            }
        }
    }

    private fun startRecording() {
        _isRecording.value = true
        captureEngine.setEnabled(true)
    }

    /** Runs on the dedicated DSP thread [C6]. */
    private fun processLiveFrame(audioBuffer: ShortArray) {
        val kConfig = _kinematicsConfig.value
        // [L5 supprime] Vitesse predite a l'instant meme du frame.
        val telemetryNow = speedProvider.currentTelemetry()
        val telemetryForCalc = if (kConfig.isEnabled) {
            telemetryNow
        } else {
            telemetryNow.copy(theoreticalSpeedKmh = telemetryNow.speedKmh)
        }

        if (_isAudioRecording.value) {
            val stepSize = audioBuffer.size / 2
            val rawChunk = audioBuffer.copyOfRange(audioBuffer.size - stepSize, audioBuffer.size)
            recordedPcmList.add(rawChunk)
            recordedTelemetryList.add(telemetryForCalc)
        }
        if (_isFrozen.value) return

        val maxHist = historySize
        val result = liveEngine.processFrame(audioBuffer)
        val magnitudes = result.magnitudes
        val ttnrSpectrum = result.ttnrSpectrum
        _latestTTNRSpectrum.value = ttnrSpectrum

        // Historique Absolu
        val curAbs = _fftHistoryAbsolute.value.toMutableList()
        curAbs.add(0, magnitudes)
        if (curAbs.size > maxHist) curAbs.removeAt(curAbs.lastIndex)
        _fftHistoryAbsolute.value = curAbs

        // Historique TTNR avec deverrouillage retroactif 150 ms (Zero Amputation)
        val curTtnr = _fftHistoryTTNR.value.toMutableList()
        curTtnr.add(0, ttnrSpectrum)
        if (result.retroUnmaskBins.isNotEmpty() && curTtnr.size >= 6) {
            for (k in 1..result.retroRawRows.size) {
                if (k < curTtnr.size) {
                    val pastRow = curTtnr[k].clone()
                    val pastRaw = result.retroRawRows[k - 1]
                    for (binIdx in result.retroUnmaskBins) {
                        pastRow[binIdx] = pastRaw[binIdx]
                    }
                    curTtnr[k] = pastRow
                }
            }
        }
        if (curTtnr.size > maxHist) curTtnr.removeAt(curTtnr.lastIndex)
        _fftHistoryTTNR.value = curTtnr

        // Suivi de l'ordre selectionne (actif uniquement si vitesse > 1 km/h)
        val speedKmh = if (kConfig.isEnabled) telemetryForCalc.theoreticalSpeedKmh else telemetryForCalc.speedKmh
        var trackedDbFS = -120.0
        var trackedEmergence = 0.0
        if (kConfig.isEnabled && speedKmh > 1.0f) {
            val h1FreqHz = kConfig.calculateH1FreqHz(speedKmh)
            if (h1FreqHz >= 0.5) {
                val nyquistFreq = AudioConfig.LIVE_SAMPLE_RATE_HZ / 2.0
                val totalBins = ttnrSpectrum.size
                val df = nyquistFreq / totalBins
                val targetFreqHz = kConfig.selectedTrackedOrder * h1FreqHz
                val centerBin = Math.round(targetFreqHz / df).toInt().coerceIn(0, totalBins - 1)

                var maxMag = -120.0
                var maxEm = 0.0
                val searchMin = (centerBin - 1).coerceAtLeast(0)
                val searchMax = (centerBin + 1).coerceAtMost(totalBins - 1)

                for (b in searchMin..searchMax) {
                    if (b in magnitudes.indices && magnitudes[b] > maxMag) {
                        maxMag = magnitudes[b]
                    }
                    if (b in ttnrSpectrum.indices && ttnrSpectrum[b] > maxEm) {
                        maxEm = ttnrSpectrum[b]
                    }
                }
                trackedDbFS = maxMag
                trackedEmergence = maxEm
            }
        }

        // Telemetrie synchronisee 1-to-1 avec l'affichage audio
        val ttnrMax = (ttnrSpectrum.maxOrNull() ?: 0.0).toFloat()
        val telemWithTtnr = telemetryForCalc.copy(
            ttnrDb = ttnrMax,
            trackedOrderDbFS = trackedDbFS,
            trackedOrderEmergenceDb = trackedEmergence
        )
        _telemetryState.value = telemWithTtnr

        val curTelem = _telemetryHistory.value.toMutableList()
        curTelem.add(0, telemWithTtnr)
        if (curTelem.size > maxHist) curTelem.removeAt(curTelem.lastIndex)
        _telemetryHistory.value = curTelem

        // Detection d'harmoniques / rapport d'emergence
        if (kConfig.isEnabled && speedKmh > 1.0f) {
            val h1FreqHz = kConfig.calculateH1FreqHz(speedKmh)
            val nowMs = System.currentTimeMillis()

            if (h1FreqHz >= 0.5) {
                val currentRpm = kConfig.calculateRpm(speedKmh)
                val targetOrders = kConfig.parsedTargetOrders()
                val isWhitelistActive = targetOrders.isNotEmpty()

                val reportList = _emergenceReportEntries.value.toMutableList()
                val newDetectedTags = mutableListOf<TrackedHarmonicTag>()
                val nyquistFreq = AudioConfig.LIVE_SAMPLE_RATE_HZ / 2.0
                val totalBins = ttnrSpectrum.size
                val df = nyquistFreq / totalBins

                if (currentRpm > 100.0) {
                    val currentFrameSpectrum = FloatArray(LiveAnalysisEngine.ORDER_BINS) { 0f }

                    for (i in 0 until totalBins) {
                        val ttnrVal = ttnrSpectrum[i]
                        if (ttnrVal > 0) {
                            val freqHz = i * df
                            val order = freqHz / h1FreqHz
                            val orderIndex = (order * 10.0).toInt()
                            if (orderIndex in 0..999) {
                                currentFrameSpectrum[orderIndex] = maxOf(currentFrameSpectrum[orderIndex], ttnrVal.toFloat())
                            }
                        }
                    }

                    val ema = liveEngine.blendOrderEma(currentFrameSpectrum)

                    for (j in 0..999) {
                        if (ema[j] > 2.0f) {
                            var isLocalMax = true
                            for (k in maxOf(0, j - 4)..minOf(999, j + 4)) {
                                if (ema[k] > ema[j]) {
                                    isLocalMax = false
                                    break
                                } else if (k < j && ema[k] == ema[j]) {
                                    isLocalMax = false
                                    break
                                }
                            }

                            if (isLocalMax) {
                                val orderValue = j / 10.0
                                var isAllowed = true
                                if (isWhitelistActive) {
                                    isAllowed = targetOrders.any { Math.abs(it - orderValue) <= 0.25 }
                                } else {
                                    if (ema[j] < 3.0f) isAllowed = false
                                }

                                if (isAllowed) {
                                    val orderName = "Ordre H$orderValue"
                                    val freqHz = (orderValue * h1FreqHz).toInt()
                                    val binIndex = (freqHz / df).toInt().coerceIn(0, totalBins - 1)
                                    val absVal = if (binIndex < magnitudes.size) magnitudes[binIndex] else -120.0

                                    val tag = TrackedHarmonicTag(
                                        orderName = orderName,
                                        orderValue = orderValue,
                                        freqHz = freqHz,
                                        ttnrDb = ema[j].toDouble(),
                                        absDbFS = absVal,
                                        speedKmh = speedKmh,
                                        rpm = currentRpm,
                                        binIndex = binIndex,
                                        lastSeenTimestampMs = nowMs
                                    )
                                    newDetectedTags.add(tag)

                                    val existingReport = reportList.find {
                                        Math.abs(it.orderValue - orderValue) <= 0.2 &&
                                            (speedKmh <= it.maxSpeedKmh + 15f && speedKmh >= it.minSpeedKmh - 15f)
                                    }
                                    if (existingReport != null) {
                                        existingReport.minSpeedKmh = minOf(existingReport.minSpeedKmh, speedKmh)
                                        existingReport.maxSpeedKmh = maxOf(existingReport.maxSpeedKmh, speedKmh)
                                        existingReport.minRpm = minOf(existingReport.minRpm, currentRpm.toInt())
                                        existingReport.maxRpm = maxOf(existingReport.maxRpm, currentRpm.toInt())
                                        existingReport.minFreqHz = minOf(existingReport.minFreqHz, freqHz)
                                        existingReport.maxFreqHz = maxOf(existingReport.maxFreqHz, freqHz)
                                        existingReport.maxEmergenceDb = maxOf(existingReport.maxEmergenceDb, ema[j].toDouble())
                                        existingReport.countDetections++
                                        existingReport.lastTimestampMs = nowMs
                                    } else {
                                        reportList.add(
                                            EmergenceReportEntry(
                                                orderName = orderName,
                                                orderValue = orderValue,
                                                minSpeedKmh = speedKmh,
                                                maxSpeedKmh = speedKmh,
                                                minRpm = currentRpm.toInt(),
                                                maxRpm = currentRpm.toInt(),
                                                minFreqHz = freqHz,
                                                maxFreqHz = freqHz,
                                                maxEmergenceDb = ema[j].toDouble(),
                                                countDetections = 1,
                                                lastTimestampMs = nowMs
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val maxHoldMs = (kConfig.holdTimeSec * 1000.0).toLong().coerceAtLeast(1000L)
                val updatedTagMap = _trackedHarmonicTags.value
                    .filter { nowMs - it.lastSeenTimestampMs < maxHoldMs }
                    .associateBy { it.orderName }
                    .toMutableMap()

                for (tag in newDetectedTags) {
                    updatedTagMap[tag.orderName] = tag
                }

                _trackedHarmonicTags.value = updatedTagMap.values.sortedBy { it.orderValue }
                _emergenceReportEntries.value = reportList
            }
        }
    }

    private fun stopRecording() {
        _isRecording.value = false
        captureEngine.setEnabled(false) // mic released via the capture flow's awaitClose
    }
    
    fun exportData(pedalPercent: String, comments: String) {
        viewModelScope.launch {
            val history = fftHistory.value
            val telemHistory = _telemetryHistory.value
            if (history.isEmpty()) return@launch
            
            val bitmapWidth = history.size
            val binCount = history.first().size
            val maxF = _maxFreq.value
            val nyquistFreq = (_loadedWavData.value?.sampleRate ?: AudioConfig.LIVE_SAMPLE_RATE_HZ) / 2
            val displayedBinCount = min(binCount, (maxF * binCount) / nyquistFreq)
            val bitmapHeight = displayedBinCount
            
            // 1. Générer le bitmap brut du spectrogramme (Absolu ou TTNR selon mode actuel)
            val spectroBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(bitmapWidth * bitmapHeight) { android.graphics.Color.BLACK }
            val mode = _displayMode.value
            val minVal = if (mode == DisplayMode.TTNR) 1.0 else _minDb.value
            val maxVal = if (mode == DisplayMode.TTNR) 20.0 else _maxDb.value
            
            for (x in 0 until bitmapWidth) {
                val frameData = history[x]
                for (y in 0 until bitmapHeight) {
                    val b = bitmapHeight - 1 - y
                    val valMagnitude = if (b < frameData.size) frameData[b] else minVal
                    val normalized = ((valMagnitude - minVal) / (maxVal - minVal)).toFloat()
                    pixels[y * bitmapWidth + (bitmapWidth - 1 - x)] = getJetColorInt(normalized)
                }
            }
            spectroBitmap.setPixels(pixels, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight)
            
            // 2. Préparation du canvas de rendu global d'exportation
            val outWidth = 1400
            val outHeight = 1850
            val outBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outBitmap)
            canvas.drawColor(android.graphics.Color.parseColor("#121212"))
            
            val paintTitle = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 42f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            val paintText = Paint().apply {
                color = android.graphics.Color.LTGRAY
                textSize = 28f
                isAntiAlias = true
            }
            val paintAxis = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            val paintLine = Paint().apply {
                color = android.graphics.Color.GRAY
                strokeWidth = 2f
                isAntiAlias = true
            }
            
            // --- EN-TÊTE ---
            var curY = 60f
            canvas.drawText("NVH SPECTRO - RAPPORT (${mode.label.uppercase()})", 60f, curY, paintTitle)
            
            try {
                val logoBitmap = android.graphics.BitmapFactory.decodeResource(
                    getApplication<Application>().resources,
                    R.drawable.logo_vibratec
                )
                if (logoBitmap != null) {
                    val logoW = 280f
                    val logoH = (logoBitmap.height.toFloat() / logoBitmap.width.toFloat()) * logoW
                    val logoRect = android.graphics.RectF(outWidth - logoW - 60f, 30f, outWidth - 60f, 30f + logoH)
                    canvas.drawBitmap(logoBitmap, null, logoRect, null)
                }
            } catch (e: Exception) {
            }

            curY += 45f
            
            val telemetry = _telemetryState.value
            val metadataStr = "Vitesse: ${String.format("%.1f", telemetry.speedKmh)} km/h | Pédale: ${if (pedalPercent.isBlank()) "-" else pedalPercent}% | Accél: ${String.format("%.2f", telemetry.accelerationG)}g | Mode: ${mode.label}"
            canvas.drawText(metadataStr, 60f, curY, paintText)
            curY += 40f
            
            if (comments.isNotBlank()) {
                canvas.drawText("Commentaires: $comments", 60f, curY, paintText)
                curY += 40f
            }
            
            curY += 20f
            
            val marginLeft = 200f
            val marginRight = 60f
            val plotWidth = outWidth - marginLeft - marginRight
            
            // --- 1. SPECTROGRAMME (Hauteur 500px) ---
            val spectroHeight = 500f
            val dstRect = android.graphics.RectF(marginLeft, curY, marginLeft + plotWidth, curY + spectroHeight)
            canvas.drawBitmap(spectroBitmap, null, dstRect, null)
            
            val actualMaxFreq = (displayedBinCount * nyquistFreq) / binCount
            canvas.drawLine(marginLeft, curY, marginLeft, curY + spectroHeight, paintLine)
            
            val yTicks = 7
            for (i in 0 until yTicks) {
                val fraction = i.toFloat() / (yTicks - 1)
                val yPos = curY + spectroHeight - (fraction * spectroHeight)
                val freqValue = (fraction * actualMaxFreq).toInt()
                
                canvas.drawLine(marginLeft - 10f, yPos, marginLeft, yPos, paintLine)
                
                val textYPos = when (i) {
                    0 -> yPos
                    yTicks - 1 -> yPos + 30f
                    else -> yPos + 10f
                }
                canvas.drawText("${freqValue} Hz", 20f, textYPos, paintAxis)
            }
            
            curY += spectroHeight + 60f
            
            // --- 2. LES 3 COURBES ÉPILÉES ---
            val graphHeight = 220f
            val graphGap = 60f
            val timeWindow = _timeWindowSec.value
            
            fun drawStackedGraph(
                title: String,
                unit: String,
                colorInt: Int,
                values: List<Double>
            ) {
                val bgPaint = Paint().apply { color = android.graphics.Color.parseColor("#1E1E1E") }
                canvas.drawRect(marginLeft, curY, marginLeft + plotWidth, curY + graphHeight, bgPaint)
                canvas.drawLine(marginLeft, curY, marginLeft, curY + graphHeight, paintLine)
                canvas.drawLine(marginLeft, curY + graphHeight, marginLeft + plotWidth, curY + graphHeight, paintLine)
                
                val minV = if (values.isNotEmpty()) values.minOrNull() ?: 0.0 else 0.0
                val maxV = if (values.isNotEmpty()) values.maxOrNull() ?: 1.0 else 1.0
                val rangeV = if (maxV > minV) maxV - minV else 1.0
                
                canvas.drawText(String.format("%.1f %s", maxV, unit), 20f, curY + 30f, paintAxis)
                canvas.drawText(String.format("%.1f %s", minV, unit), 20f, curY + graphHeight, paintAxis)
                
                val titlePaint = Paint().apply {
                    color = colorInt
                    textSize = 26f
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                }
                canvas.drawText(title, marginLeft + 20f, curY + 35f, titlePaint)
                
                if (values.size > 1) {
                    val path = Path()
                    val pCount = values.size
                    val linePaint = Paint().apply {
                        color = colorInt
                        strokeWidth = 3.5f
                        style = Paint.Style.STROKE
                        isAntiAlias = true
                    }
                    
                    for (i in 0 until pCount) {
                        val fractionX = (pCount - 1 - i).toFloat() / max(1, historySize - 1)
                        val x = marginLeft + (1f - fractionX) * plotWidth
                        val normY = ((values[i] - minV) / rangeV).toFloat()
                        val y = (curY + graphHeight) - (normY * graphHeight)
                        
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    canvas.drawPath(path, linePaint)
                }
                
                curY += graphHeight + graphGap
            }
            
            val speedValues = telemHistory.map { it.speedKmh.toDouble() }
            drawStackedGraph("Vitesse (km/h)", "km/h", android.graphics.Color.parseColor("#00E676"), speedValues)
            
            val accelValues = telemHistory.map { it.accelerationG.toDouble() }
            drawStackedGraph("Accélération (g)", "g", android.graphics.Color.parseColor("#FF9100"), accelValues)
            
            val altValues = telemHistory.map { it.altitude }
            drawStackedGraph("Altitude (m)", "m", android.graphics.Color.parseColor("#00B0FF"), altValues)
            
            val xBottomY = curY - graphGap + 35f
            val xSteps = 5
            for (i in 0..xSteps) {
                val fraction = i.toFloat() / xSteps
                val x = marginLeft + fraction * plotWidth
                val tSec = -timeWindow * (1f - fraction)
                canvas.drawText(String.format("%.1fs", tSec), x - 25f, xBottomY, paintAxis)
            }
            canvas.drawText("Temps (s)", marginLeft + plotWidth / 2f - 40f, xBottomY + 35f, paintAxis)
            
            val resolver = getApplication<Application>().contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "NVHSpectro_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NVHSpectro")
            }
            
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { outStream ->
                    outBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                }
            }
        }
    }

    fun toggleReportMode() {
        if (!_isReportModeActive.value) {
            _reportFftHistory.value = fftHistory.value.toList()
            _reportFftHistoryAbsolute.value = _fftHistoryAbsolute.value.toList()
            _reportFftHistoryTTNR.value = _fftHistoryTTNR.value.toList()
            _isReportModeActive.value = true
        } else {
            _isReportModeActive.value = false
            _currentUserPoints.value = emptyList()
            _currentSmartPath.value = emptyList()
            _isDrawingMode.value = false
        }
    }

    fun clearCurrentSmartTrack() {
        _currentUserPoints.value = emptyList()
        _currentSmartPath.value = emptyList()
    }
    
    fun clearCurrentPoints() {
        _currentUserPoints.value = emptyList()
        _currentSmartPath.value = emptyList()
    }
    
    fun selectValidatedOrder(order: SmartTrackedOrder?) {
        _selectedValidatedOrder.value = order
    }
    
    fun removeValidatedOrder(order: SmartTrackedOrder) {
        _manualTrackedOrders.value = _manualTrackedOrders.value.filter { it != order }
        if (_selectedValidatedOrder.value == order) {
            _selectedValidatedOrder.value = null
        }
    }

    fun clearAllValidatedOrders() {
        _manualTrackedOrders.value = emptyList()
    }

    fun addManualTrackPoint(frameIndex: Int, binIndex: Int) {
        val currentPoints = _currentUserPoints.value.toMutableList()
        currentPoints.add(ManualOrderAnchor(frameIndex, binIndex, isUserPlaced = true))
        currentPoints.sortBy { it.frameIndex }
        _currentUserPoints.value = currentPoints
        recalculateSmartPath()
    }

    private fun recalculateSmartPath() {
        val points = _currentUserPoints.value
        if (points.size < 2) {
            _currentSmartPath.value = emptyList()
            return
        }
        
        val isTTNR = _displayMode.value == DisplayMode.TTNR
        val historyToUse = if (_isReportModeActive.value) {
            if (isTTNR) _reportFftHistoryTTNR.value else _reportFftHistoryAbsolute.value
        } else {
            if (isTTNR) _fftHistoryTTNR.value else _fftHistoryAbsolute.value
        }
        if (historyToUse.isEmpty()) return

        val startFrame = points.first().frameIndex.coerceIn(0, historyToUse.size - 1)
        val endFrame = points.last().frameIndex.coerceIn(0, historyToUse.size - 1)
        
        if (startFrame >= endFrame) {
            _currentSmartPath.value = points
            return
        }
        
        val numFrames = endFrame - startFrame + 1
        val binCount = historyToUse[startFrame].size
        
        // Ligne de guide : stricte ligne droite (lineaire) entre les points pour donner la PENTE generale
        fun getExpectedBinF(globalFrame: Int): Float {
            if (globalFrame <= points.first().frameIndex) return points.first().binIndex.toFloat()
            if (globalFrame >= points.last().frameIndex) return points.last().binIndex.toFloat()
            for (i in 0 until points.size - 1) {
                if (globalFrame >= points[i].frameIndex && globalFrame <= points[i+1].frameIndex) {
                    val f1 = points[i].frameIndex
                    val b1 = points[i].binIndex.toFloat()
                    val f2 = points[i+1].frameIndex
                    val b2 = points[i+1].binIndex.toFloat()
                    if (f1 == f2) return b1
                    val fraction = (globalFrame - f1).toFloat() / (f2 - f1).toFloat()
                    return b1 + fraction * (b2 - b1)
                }
            }
            return points.last().binIndex.toFloat()
        }

        // Energie en dB
        val dbEnergies = Array(numFrames) { FloatArray(binCount) }
        for (f in 0 until numFrames) {
            val globalFrame = startFrame + f
            val spectrum = historyToUse[globalFrame]
            for (b in 0 until binCount) {
                dbEnergies[f][b] = spectrum[b].toFloat()
            }
        }

        val rawPath = mutableListOf<ManualOrderAnchor>()
        val searchRadius = 20 // Approx +/- 20 bins
        var prevTrackedBinF = getExpectedBinF(startFrame)

        for (f in 0 until numFrames) {
            val globalFrame = startFrame + f
            val isUserPoint = points.any { it.frameIndex == globalFrame }
            
            // Si c'est un point utilisateur, on s'y fixe de force
            if (isUserPoint) {
                val pt = points.first { it.frameIndex == globalFrame }
                rawPath.add(ManualOrderAnchor(globalFrame, pt.binIndex, isUserPlaced = true, exactBinF = pt.binIndex.toFloat()))
                prevTrackedBinF = pt.binIndex.toFloat()
                continue
            }
            
            val expectedBinF = getExpectedBinF(globalFrame)
            val centerSearchInt = Math.round(expectedBinF)
            
            val minBin = (centerSearchInt - searchRadius).coerceAtLeast(0)
            val maxBin = (centerSearchInt + searchRadius).coerceAtMost(binCount - 1)
            
            var bestBin = centerSearchInt
            var maxScore = -Float.MAX_VALUE
            
            for (b in minBin..maxBin) {
                val e = dbEnergies[f][b]
                
                // Peak detection: on cherche un maximum local pour ne pas glisser sur le flanc d'une autre harmonique
                val isLocalMax = if (b > 0 && b < binCount - 1) {
                    e > dbEnergies[f][b-1] && e > dbEnergies[f][b+1]
                } else {
                    true
                }
                
                if (isLocalMax) {
                    // Penalite pour assurer la continuite du tracé
                    val jumpDist = Math.abs(b - prevTrackedBinF)
                    // Penalite legere pour rester pres du guide utilisateur
                    val guideDist = Math.abs(b - expectedBinF)
                    
                    val score = e - (1.5f * jumpDist) - (0.5f * guideDist)
                    if (score > maxScore) {
                        maxScore = score
                        bestBin = b
                    }
                }
            }
            
            // Si aucun max local n'est trouve (spectre tres plat), on utilise la ligne guide
            if (maxScore == -Float.MAX_VALUE) {
                bestBin = Math.round(expectedBinF)
            }
            
            // Sub-bin parabolic interpolation
            var exactBinF = bestBin.toFloat()
            if (bestBin > 0 && bestBin < binCount - 1 && maxScore != -Float.MAX_VALUE) {
                val y1 = dbEnergies[f][bestBin - 1]
                val y2 = dbEnergies[f][bestBin]
                val y3 = dbEnergies[f][bestBin + 1]
                
                val denom = 2f * (y1 - 2f * y2 + y3)
                if (denom != 0f) {
                    val p = (y1 - y3) / denom
                    exactBinF = bestBin + p.coerceIn(-0.5f, 0.5f)
                }
            }
            
            // Derivative threshold: si le saut est vraiment aberrant, on le limite
            if (Math.abs(exactBinF - prevTrackedBinF) > 15f) {
                exactBinF = prevTrackedBinF + Math.signum(exactBinF - prevTrackedBinF) * 15f
                bestBin = Math.round(exactBinF)
            }
            
            rawPath.add(ManualOrderAnchor(globalFrame, bestBin, isUserPlaced = false, exactBinF = exactBinF))
            prevTrackedBinF = exactBinF
        }
        
        // Smoothing (Moyenne glissante)
        val smoothedPath = mutableListOf<ManualOrderAnchor>()
        val smoothingWindow = 2 // Fenetre de 5 points (-2 a +2)
        for (i in rawPath.indices) {
            val anchor = rawPath[i]
            if (anchor.isUserPlaced) {
                smoothedPath.add(anchor)
                continue
            }
            var sumBinF = 0f
            var count = 0
            for (j in -smoothingWindow..smoothingWindow) {
                val idx = i + j
                if (idx in rawPath.indices) {
                    sumBinF += rawPath[idx].exactBinF
                    count++
                }
            }
            val avgBinF = sumBinF / count
            smoothedPath.add(ManualOrderAnchor(anchor.frameIndex, Math.round(avgBinF), false, avgBinF))
        }
        
        _currentSmartPath.value = smoothedPath
    }

    fun validateCurrentOrder(customName: String? = null) {
        val path = _currentSmartPath.value
        if (path.isEmpty()) return
        
        val reportHistory = _reportFftHistory.value
        val reportHistoryTTNR = _reportFftHistoryTTNR.value
        val telemHistory = _telemetryHistory.value
        val sampleRate = _loadedWavData.value?.sampleRate ?: AudioConfig.LIVE_SAMPLE_RATE_HZ
        val nyquist = sampleRate / 2.0
        val totalBins = if (reportHistory.isNotEmpty()) reportHistory[0].size else 2048
        val df = nyquist / totalBins

        var minRpm: Int? = null
        var maxRpm: Int? = null
        var minSpeed: Float? = null
        var maxSpeed: Float? = null
        var minFreqHz = Int.MAX_VALUE
        var maxFreqHz = Int.MIN_VALUE
        var maxEmergence = -100.0

        for (anchor in path) {
            val f = anchor.frameIndex
            if (f in reportHistory.indices) {
                val b = anchor.binIndex.coerceIn(0, totalBins - 1)
                val freqHz = (b * df).toInt()
                if (freqHz < minFreqHz) minFreqHz = freqHz
                if (freqHz > maxFreqHz) maxFreqHz = freqHz
                
                val emergence = reportHistoryTTNR[f][b]
                if (emergence > maxEmergence) maxEmergence = emergence
            }
            
            if (telemHistory.isNotEmpty() && f in reportHistory.indices) {
                // [C17] Frame indices are NOT telemetry indices in WAV mode (12,900 frames
                // vs ~30 samples) — the old direct telemHistory[f] read speeds/RPM from the
                // wrong instants on the customer PDF. Map by time ratio instead.
                val telem = telemHistory[TimelineMapper.mapIndex(f, reportHistory.size, telemHistory.size)]
                val speed = if (_kinematicsConfig.value.isEnabled) telem.theoreticalSpeedKmh else telem.speedKmh
                if (speed > 1.0f) {
                    if (minSpeed == null || speed < minSpeed) minSpeed = speed
                    if (maxSpeed == null || speed > maxSpeed) maxSpeed = speed
                    
                    val rpm = _kinematicsConfig.value.calculateRpm(speed).toInt()
                    if (rpm > 100) {
                        if (minRpm == null || rpm < minRpm) minRpm = rpm
                        if (maxRpm == null || rpm > maxRpm) maxRpm = rpm
                    }
                }
            }
        }

        if (minFreqHz == Int.MAX_VALUE) minFreqHz = 0
        if (maxFreqHz == Int.MIN_VALUE) maxFreqHz = 0

        val count = _manualTrackedOrders.value.size
        val name = customName?.takeIf { it.isNotBlank() } ?: "Ordre ${count + 1}"
        
        val colors = listOf(
            androidx.compose.ui.graphics.Color(0xFFB026FF),
            androidx.compose.ui.graphics.Color(0xFFFF1493),
            androidx.compose.ui.graphics.Color(0xFF32CD32),
            androidx.compose.ui.graphics.Color(0xFFFFA500),
            androidx.compose.ui.graphics.Color(0xFF8A2BE2),
            androidx.compose.ui.graphics.Color(0xFF00FFFF),
            androidx.compose.ui.graphics.Color(0xFFFFD700)
        )
        val color = colors[count % colors.size]

        val order = SmartTrackedOrder(
            name = name,
            color = color,
            path = path,
            minRpm = minRpm,
            maxRpm = maxRpm,
            minSpeedKmh = minSpeed,
            maxSpeedKmh = maxSpeed,
            minFreqHz = minFreqHz,
            maxFreqHz = maxFreqHz,
            maxEmergenceDb = maxEmergence
        )

        _manualTrackedOrders.value = _manualTrackedOrders.value + order
        clearCurrentSmartTrack()
    }

    /** [L1] Every owned resource has a release path on ViewModel death. */
    override fun onCleared() {
        captureEngine.setEnabled(false)
        speedProvider.stop()
        playback.release()
        audioRepository.stopAudioCapture()
        analysisDispatcher.close()
        super.onCleared()
    }

    fun savePdfToUri(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outStream ->
                    com.example.nvhspectro.utils.PdfReportGenerator.generateReport(
                        context = context,
                        outStream = outStream,
                        historyAbs = _reportFftHistoryAbsolute.value,
                        historyTtnr = _reportFftHistoryTTNR.value,
                        minDb = _minDb.value,
                        maxDb = _maxDb.value,
                        trackedOrders = _manualTrackedOrders.value,
                        kinematicsConfig = _kinematicsConfig.value,
                        globalMaxFreq = _maxFreq.value.toFloat(),
                        sampleRate = _loadedWavData.value?.sampleRate ?: AudioConfig.LIVE_SAMPLE_RATE_HZ
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

}
