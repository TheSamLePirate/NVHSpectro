package com.example.nvhspectro

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nvhspectro.data.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

enum class DisplayMode(val label: String) {
    ABSOLUTE("Absolue (dBFS)"),
    TTNR("TTNR (Emergence)")
}

enum class AudioSourceMode {
    LIVE,
    WAV_ANALYZER
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val audioRepository = AudioRepository()
    private val telemetryRepository = TelemetryRepository(application)
    private var fftProcessor = FFTProcessor(2048)
    
    // États Kinématiques GMPe & Rapport d'Émergence
    private val _kinematicsConfig = MutableStateFlow(KinematicsConfig())
    val kinematicsConfig: StateFlow<KinematicsConfig> = _kinematicsConfig.asStateFlow()

    private val _trackedHarmonicTags = MutableStateFlow<List<TrackedHarmonicTag>>(emptyList())
    val trackedHarmonicTags: StateFlow<List<TrackedHarmonicTag>> = _trackedHarmonicTags.asStateFlow()

    private val _emergenceReportEntries = MutableStateFlow<List<EmergenceReportEntry>>(emptyList())
    val emergenceReportEntries: StateFlow<List<EmergenceReportEntry>> = _emergenceReportEntries.asStateFlow()

    private val candidateTrackerList = mutableListOf<CandidateHarmonicTracker>()
    private var ttnrPersistenceCount = IntArray(0)
    private val recentRawTTNRBuffer = mutableListOf<DoubleArray>()

    fun updateKinematicsConfig(config: KinematicsConfig) {
        _kinematicsConfig.value = config
        if (_audioSourceMode.value == AudioSourceMode.WAV_ANALYZER && _loadedWavData.value != null) {
            processWavFrameAt(_wavPlaybackPositionMs.value)
        }
    }

    fun updateSelectedTrackedOrder(order: Double) {
        val currentConfig = _kinematicsConfig.value
        _kinematicsConfig.value = currentConfig.copy(selectedTrackedOrder = order)
        if (_audioSourceMode.value == AudioSourceMode.WAV_ANALYZER && _loadedWavData.value != null) {
            processWavFrameAt(_wavPlaybackPositionMs.value)
        }
    }

    fun clearEmergenceReport() {
        candidateTrackerList.clear()
        ttnrPersistenceCount = IntArray(0)
        recentRawTTNRBuffer.clear()
        _emergenceReportEntries.value = emptyList()
        _trackedHarmonicTags.value = emptyList()
    }
    
    // Etats de l'UI
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
        _showAudioModeMenu.value = false
        if (_audioSourceMode.value == mode) return
        _audioSourceMode.value = mode

        if (mode == AudioSourceMode.LIVE) {
            stopWavPlayback()
            _loadedWavData.value = null
            _loadedWavFileName.value = null
            _fftHistoryAbsolute.value = emptyList()
            _fftHistoryTTNR.value = emptyList()
        } else {
            stopWavPlayback()
            _fftHistoryAbsolute.value = emptyList()
            _fftHistoryTTNR.value = emptyList()
        }
    }

    fun openWavSelectionDialog() {
        _showWavSelectionDialog.value = true
    }

    fun closeWavSelectionDialog() {
        _showWavSelectionDialog.value = false
    }

    private var mediaPlayer: MediaPlayer? = null

    private fun initMediaPlayer(wavFile: File? = null, uri: android.net.Uri? = null, context: android.content.Context? = null) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                if (wavFile != null) {
                    setDataSource(wavFile.absolutePath)
                } else if (uri != null && context != null) {
                    setDataSource(context, uri)
                }
                prepare()
                setOnCompletionListener {
                    _isWavPlaying.value = false
                    _loadedWavData.value?.let { d -> _wavPlaybackPositionMs.value = d.durationMs }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadWavFile(wavFile: File, jsonFile: File?) {
        _showWavSelectionDialog.value = false
        stopWavPlayback()
        val data = WavDataReader.readWavFile(wavFile, jsonFile)
        if (data != null) {
            initMediaPlayer(wavFile = wavFile)
            val exactDuration = mediaPlayer?.duration?.toLong()?.takeIf { it > 0 } ?: data.durationMs
            val updatedData = data.copy(durationMs = exactDuration)

            _loadedWavData.value = updatedData
            _loadedWavFileName.value = wavFile.name
            _wavPlaybackPositionMs.value = 0L
            processFullWavSpectrogram(updatedData)
            processWavFrameAt(0L)
        }
    }

    fun loadWavFromUri(context: android.content.Context, uri: android.net.Uri) {
        _showWavSelectionDialog.value = false
        stopWavPlayback()
        val data = WavDataReader.readWavFromUri(context, uri)
        if (data != null) {
            initMediaPlayer(uri = uri, context = context)
            val exactDuration = mediaPlayer?.duration?.toLong()?.takeIf { it > 0 } ?: data.durationMs
            val updatedData = data.copy(durationMs = exactDuration)

            _loadedWavData.value = updatedData
            _loadedWavFileName.value = uri.lastPathSegment?.substringAfterLast("/") ?: "fichier.wav"
            _wavPlaybackPositionMs.value = 0L
            processFullWavSpectrogram(updatedData)
            processWavFrameAt(0L)
        }
    }

    private fun processFullWavSpectrogram(data: LoadedWavData) {
        val sampleRate = data.sampleRate
        val fftN = _fftSize.value
        val stepSize = fftN / 2
        val totalSamples = data.pcmSamples.size
        
        if (totalSamples < fftN) {
            _fftHistoryAbsolute.value = emptyList()
            _fftHistoryTTNR.value = emptyList()
            _telemetryHistory.value = emptyList()
            return
        }

        val frameCount = ((totalSamples - fftN) / stepSize).coerceAtLeast(1)
        val absList = ArrayList<DoubleArray>(frameCount)
        val ttnrList = ArrayList<DoubleArray>(frameCount)

        val frameBuffer = ShortArray(fftN)
        for (i in 0 until frameCount) {
            val startSample = i * stepSize
            val avail = totalSamples - startSample
            val copyLen = avail.coerceAtMost(fftN)
            if (copyLen > 0) {
                System.arraycopy(data.pcmSamples, startSample, frameBuffer, 0, copyLen)
            } else {
                java.util.Arrays.fill(frameBuffer, 0.toShort())
            }
            val magnitudes = fftProcessor.processFFT(frameBuffer)
            val rawTtnr = fftProcessor.computeTTNR(magnitudes, sampleRate)
            absList.add(magnitudes)
            ttnrList.add(rawTtnr)
        }

        _fftHistoryAbsolute.value = absList
        _fftHistoryTTNR.value = ttnrList

        if (data.telemetryList.isNotEmpty()) {
            _telemetryHistory.value = data.telemetryList
        } else {
            val synthetic = List(frameCount) { TelemetryData(gpsStatus = GpsStatus.NONE) }
            _telemetryHistory.value = synthetic
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

        try {
            mediaPlayer?.let { player ->
                if (_wavPlaybackPositionMs.value >= data.durationMs) {
                    _wavPlaybackPositionMs.value = 0L
                    player.seekTo(0)
                } else {
                    player.seekTo(_wavPlaybackPositionMs.value.toInt())
                }
                player.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        wavPlaybackJob?.cancel()
        wavPlaybackJob = viewModelScope.launch {
            val sampleRate = data.sampleRate
            val stepSize = _fftSize.value / 2
            val stepMs = ((stepSize.toDouble() / sampleRate.toDouble()) * 1000.0).toLong().coerceAtLeast(15L)

            while (_isWavPlaying.value && _wavPlaybackPositionMs.value < data.durationMs) {
                val currentMpPos = mediaPlayer?.currentPosition?.toLong() ?: _wavPlaybackPositionMs.value
                _wavPlaybackPositionMs.value = currentMpPos.coerceIn(0L, data.durationMs)
                processWavFrameAt(_wavPlaybackPositionMs.value)
                delay(stepMs)
            }

            if (_wavPlaybackPositionMs.value >= data.durationMs) {
                _isWavPlaying.value = false
                _wavPlaybackPositionMs.value = data.durationMs
            }
        }
    }

    fun pauseWavPlayback() {
        _isWavPlaying.value = false
        wavPlaybackJob?.cancel()
        wavPlaybackJob = null
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopWavPlayback() {
        pauseWavPlayback()
        _wavPlaybackPositionMs.value = 0L
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun seekWavTo(posMs: Long) {
        val data = _loadedWavData.value ?: return
        val clamped = posMs.coerceIn(0L, data.durationMs)
        _wavPlaybackPositionMs.value = clamped
        try {
            mediaPlayer?.seekTo(clamped.toInt())
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

        if (absList.isNotEmpty()) {
            val frameIdx = (ratio * (absList.size - 1)).toInt().coerceIn(0, absList.size - 1)
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
        if (teleHist.isNotEmpty()) {
            val teleIdx = (ratio * (teleHist.size - 1)).toInt().coerceIn(0, teleHist.size - 1)
            telem = teleHist[teleIdx]
        } else if (data.telemetryList.isNotEmpty()) {
            val teleIdx = (ratio * (data.telemetryList.size - 1)).toInt().coerceIn(0, data.telemetryList.size - 1)
            telem = data.telemetryList[teleIdx]
        }

        val kConfig = _kinematicsConfig.value
        if (kConfig.isEnabled && telem.speedKmh > 1.0f && currentAbs.isNotEmpty() && currentTtnr.isNotEmpty()) {
            val speedKmh = telem.speedKmh
            val h1FreqHz = kConfig.calculateH1FreqHz(speedKmh)
            if (h1FreqHz >= 0.5) {
                val nyquistFreq = 44100 / 2.0
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
        
        val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH'h'mm's'ss", Locale.getDefault()).format(Date())
        val folderName = "${cleanName}_${timeStamp}"
        
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val exportFolder = File(downloadsDir, "NVH_Spectro_Exports/$folderName")
        exportFolder.mkdirs()

        // 1. Fichier WAV
        val totalSamples = recordedPcmList.sumOf { it.size }
        val fullPcm = ShortArray(totalSamples)
        var offset = 0
        synchronized(recordedPcmList) {
            for (block in recordedPcmList) {
                System.arraycopy(block, 0, fullPcm, offset, block.size)
                offset += block.size
            }
        }
        val wavFile = File(exportFolder, "${folderName}.wav")
        WavAudioWriter.writePcmToWav(fullPcm, wavFile, 44100)

        // 2. Fichier JSON Télémétrie
        val jsonFile = File(exportFolder, "${folderName}_telemetrie.json")
        val jsonContent = StringBuilder()
        jsonContent.append("{\n")
        jsonContent.append("  \"folderName\": \"$folderName\",\n")
        jsonContent.append("  \"durationSec\": ${_recordingElapsedSec.value},\n")
        jsonContent.append("  \"sampleRate\": 44100,\n")
        jsonContent.append("  \"telemetryCount\": ${recordedTelemetryList.size},\n")
        jsonContent.append("  \"telemetryData\": [\n")
        
        synchronized(recordedTelemetryList) {
            val items = recordedTelemetryList.mapIndexed { idx, item ->
                "    {\"index\": $idx, \"speedKmh\": ${item.speedKmh}, \"accelerationG\": ${item.accelerationG}, \"lat\": ${item.latitude}, \"lng\": ${item.longitude}, \"gpsStatus\": \"${item.gpsStatus}\"}"
            }
            jsonContent.append(items.joinToString(",\n"))
        }
        jsonContent.append("\n  ]\n}")
        
        jsonFile.writeText(jsonContent.toString())

        recordedPcmList.clear()
        recordedTelemetryList.clear()
        _showSaveRecordingDialog.value = false
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
            val dt = (_fftSize.value / 2.0) / 44100.0
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
        _minDb.value = newMinDb
        _maxDb.value = newMaxDb
        _minFreq.value = newMinFreq.coerceAtLeast(0)
        _maxFreq.value = newMaxFreq
        _timeWindowSec.value = newTimeWindow
        if (_fftSize.value != newFftSize) {
            _fftSize.value = newFftSize
            fftProcessor = FFTProcessor(newFftSize)
            _fftHistoryAbsolute.value = emptyList()
            _fftHistoryTTNR.value = emptyList()
            _telemetryHistory.value = emptyList()
            if (_isRecording.value) {
                stopRecording()
                startRecording()
            }
        }
    }
    
    private val _isFrozen = MutableStateFlow(false)
    val isFrozen: StateFlow<Boolean> = _isFrozen.asStateFlow()

    fun toggleFreeze() {
        _isFrozen.value = !_isFrozen.value
    }
    
    private val _isRecording = MutableStateFlow(true)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    init {
        startRecording()
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private var previousTTNRSpectrum = DoubleArray(0)

    private fun startRecording() {
        _isRecording.value = true
        
        // 1. Mettre à jour l'état GPS instantané
        viewModelScope.launch {
            telemetryRepository.startTelemetry().collect { data ->
                val current = _telemetryState.value
                _telemetryState.value = data.copy(
                    ttnrDb = current.ttnrDb,
                    trackedOrderDbFS = current.trackedOrderDbFS,
                    trackedOrderEmergenceDb = current.trackedOrderEmergenceDb
                )
            }
        }
        
        // 2. Lancer la capture Audio et Synchronisation 1-to-1 du Spectrogramme ET de la Télémétrie
        viewModelScope.launch {
            audioRepository.startAudioCapture(_fftSize.value).collect { audioBuffer ->
                if (_audioSourceMode.value == AudioSourceMode.LIVE) {
                    if (_isAudioRecording.value) {
                        val stepSize = audioBuffer.size / 2
                        val rawChunk = audioBuffer.copyOfRange(audioBuffer.size - stepSize, audioBuffer.size)
                        recordedPcmList.add(rawChunk)
                        recordedTelemetryList.add(_telemetryState.value.copy())
                    }
                    if (!_isFrozen.value) {
                    val maxHist = historySize

                    // Traitement FFT Absolu
                    val magnitudes = fftProcessor.processFFT(audioBuffer)
                    
                    // Traitement TTNR (Émergence tonale ECMA-74)
                    val rawTtnr = fftProcessor.computeTTNR(magnitudes, 44100)
                    
                    // Initialisation / Ajustement de la taille du tampon de persistance 150 ms
                    if (ttnrPersistenceCount.size != rawTtnr.size) {
                        ttnrPersistenceCount = IntArray(rawTtnr.size)
                        recentRawTTNRBuffer.clear()
                    }

                    // Enregistrement de la trame brute dans le tampon rétrospectif (6 trames ~= 150 ms)
                    val rawCopy = rawTtnr.clone()
                    recentRawTTNRBuffer.add(0, rawCopy)
                    if (recentRawTTNRBuffer.size > 6) {
                        recentRawTTNRBuffer.removeLast()
                    }

                    // Calcul de la persistance par bin et validation du tampon rétrospectif 150 ms
                    val validatedTtnr = DoubleArray(rawTtnr.size)
                    val retroUnmaskBins = mutableListOf<Int>()

                    for (i in rawTtnr.indices) {
                        if (rawTtnr[i] >= 2.0) {
                            ttnrPersistenceCount[i]++
                        } else {
                            ttnrPersistenceCount[i] = 0
                        }

                        if (ttnrPersistenceCount[i] >= 6) {
                            validatedTtnr[i] = rawTtnr[i]
                            if (ttnrPersistenceCount[i] == 6) {
                                retroUnmaskBins.add(i)
                            }
                        } else {
                            validatedTtnr[i] = 0.0
                        }
                    }

                    // Lissage temporel EMA NVH
                    val ttnrSpectrum = DoubleArray(rawTtnr.size)
                    if (previousTTNRSpectrum.size == rawTtnr.size) {
                        for (i in rawTtnr.indices) {
                            ttnrSpectrum[i] = 0.75 * validatedTtnr[i] + 0.25 * previousTTNRSpectrum[i]
                        }
                    } else {
                        System.arraycopy(validatedTtnr, 0, ttnrSpectrum, 0, validatedTtnr.size)
                    }
                    previousTTNRSpectrum = ttnrSpectrum
                    _latestTTNRSpectrum.value = ttnrSpectrum
                    
                    // Mettre à jour l'historique Absolu
                    val curAbs = _fftHistoryAbsolute.value.toMutableList()
                    curAbs.add(0, magnitudes)
                    if (curAbs.size > maxHist) curAbs.removeLast()
                    _fftHistoryAbsolute.value = curAbs

                    // Mettre à jour l'historique TTNR avec déverrouillage rétroactif 150 ms (Zéro Amputation)
                    val curTtnr = _fftHistoryTTNR.value.toMutableList()
                    curTtnr.add(0, ttnrSpectrum)
                    
                    // Restitution rétroactive des 5 trames précédentes pour les pics validés à 150 ms
                    if (retroUnmaskBins.isNotEmpty() && curTtnr.size >= 6 && recentRawTTNRBuffer.size >= 6) {
                        for (k in 1..5) {
                            if (k < curTtnr.size && k < recentRawTTNRBuffer.size) {
                                val pastRow = curTtnr[k].clone()
                                val pastRaw = recentRawTTNRBuffer[k]
                                for (binIdx in retroUnmaskBins) {
                                    pastRow[binIdx] = pastRaw[binIdx]
                                }
                                curTtnr[k] = pastRow
                            }
                        }
                    }

                    if (curTtnr.size > maxHist) curTtnr.removeLast()
                    _fftHistoryTTNR.value = curTtnr

                    // Traitement des Harmoniques & Détection de Cinématique NVH (Actif UNIQUEMENT si Vitesse > 1.0 km/h)
                    val kConfig = _kinematicsConfig.value
                    val speedKmh = _telemetryState.value.speedKmh

                    var trackedDbFS = -120.0
                    var trackedEmergence = 0.0

                    if (kConfig.isEnabled && speedKmh > 1.0f) {
                        val h1FreqHz = kConfig.calculateH1FreqHz(speedKmh)
                        if (h1FreqHz >= 0.5) {
                            val nyquistFreq = 44100 / 2.0
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

                    // Synchronisation stricte 1-to-1 de la Télémétrie sur le temps d'affichage audio avec la valeur TTNR et l'Ordre Traqué
                    val ttnrMax = (ttnrSpectrum.maxOrNull() ?: 0.0).toFloat()
                    val telemWithTtnr = _telemetryState.value.copy(
                        ttnrDb = ttnrMax,
                        trackedOrderDbFS = trackedDbFS,
                        trackedOrderEmergenceDb = trackedEmergence
                    )

                    _telemetryState.value = telemWithTtnr

                    val curTelem = _telemetryHistory.value.toMutableList()
                    curTelem.add(0, telemWithTtnr)
                    if (curTelem.size > maxHist) curTelem.removeLast()
                    _telemetryHistory.value = curTelem

                    if (kConfig.isEnabled && speedKmh > 1.0f) {
                        val h1FreqHz = kConfig.calculateH1FreqHz(speedKmh)
                        val nowMs = System.currentTimeMillis()
                        
                        if (h1FreqHz >= 0.5) {
                            val nyquistFreq = 44100 / 2.0
                            val totalBins = ttnrSpectrum.size
                            val df = nyquistFreq / totalBins
                            
                            val threshDb = _emergenceThresholdDb.value
                            val gateDbFS = _magnitudeGateDbFS.value
                            val currentRpmInt = kConfig.calculateRpm(speedKmh).toInt()

                            val targetOrders = kConfig.parsedTargetOrders()
                            val isWhitelistActive = targetOrders.isNotEmpty()

                            val updatedTrackersThisFrame = mutableSetOf<CandidateHarmonicTracker>()
                            
                            for (i in 1 until totalBins - 1) {
                                val ttnrVal = ttnrSpectrum[i]
                                val absVal = if (i < magnitudes.size) magnitudes[i] else -120.0
                                
                                if (ttnrVal >= threshDb && absVal >= gateDbFS) {
                                    val prevTtnr = ttnrSpectrum[i - 1]
                                    val nextTtnr = ttnrSpectrum[i + 1]
                                    if (ttnrVal >= prevTtnr && ttnrVal >= nextTtnr) {
                                        val freqHz = i * df
                                        val exactOrderRatio = freqHz / h1FreqHz
                                        
                                        if (exactOrderRatio >= 0.5) {
                                            var candidateOrder = exactOrderRatio
                                            var isAllowed = true

                                            if (isWhitelistActive) {
                                                val matchedTarget = targetOrders.find { target -> Math.abs(exactOrderRatio - target) <= 0.25 }
                                                if (matchedTarget != null) {
                                                    candidateOrder = matchedTarget
                                                } else {
                                                    isAllowed = false
                                                }
                                            }

                                            if (isAllowed) {
                                                // Recherche d'un candidat existant à +-0.10 d'ordre (ex: 22.1 à 22.3 pour 22.2)
                                                val existingTracker = candidateTrackerList.find { 
                                                    Math.abs(it.currentMeanOrder - candidateOrder) <= 0.10 
                                                }

                                                if (existingTracker != null) {
                                                    existingTracker.orderSum += candidateOrder
                                                    existingTracker.count++
                                                    existingTracker.lastSeenTimestampMs = nowMs
                                                    existingTracker.lastFreqHz = freqHz.toInt()
                                                    existingTracker.maxTtnrDb = maxOf(existingTracker.maxTtnrDb, ttnrVal)
                                                    existingTracker.maxAbsDbFS = maxOf(existingTracker.maxAbsDbFS, absVal)
                                                    existingTracker.minSpeedKmh = minOf(existingTracker.minSpeedKmh, speedKmh)
                                                    existingTracker.maxSpeedKmh = maxOf(existingTracker.maxSpeedKmh, speedKmh)
                                                    existingTracker.minRpm = minOf(existingTracker.minRpm, currentRpmInt)
                                                    existingTracker.maxRpm = maxOf(existingTracker.maxRpm, currentRpmInt)
                                                    existingTracker.minFreqHz = minOf(existingTracker.minFreqHz, freqHz.toInt())
                                                    existingTracker.maxFreqHz = maxOf(existingTracker.maxFreqHz, freqHz.toInt())
                                                    existingTracker.binIndex = i
                                                    updatedTrackersThisFrame.add(existingTracker)
                                                } else {
                                                    val newTracker = CandidateHarmonicTracker(
                                                        orderSum = candidateOrder,
                                                        count = 1,
                                                        firstSeenTimestampMs = nowMs,
                                                        lastSeenTimestampMs = nowMs,
                                                        lastFreqHz = freqHz.toInt(),
                                                        maxTtnrDb = ttnrVal,
                                                        maxAbsDbFS = absVal,
                                                        minSpeedKmh = speedKmh,
                                                        maxSpeedKmh = speedKmh,
                                                        minRpm = currentRpmInt,
                                                        maxRpm = currentRpmInt,
                                                        minFreqHz = freqHz.toInt(),
                                                        maxFreqHz = freqHz.toInt(),
                                                        binIndex = i
                                                    )
                                                    candidateTrackerList.add(newTracker)
                                                    updatedTrackersThisFrame.add(newTracker)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // Éliminer les candidats non mis à jour depuis plus de 250 ms (perte de pic)
                            candidateTrackerList.retainAll { nowMs - it.lastSeenTimestampMs <= 250L }

                            // Valider et enregistrer les candidats ayant au moins 0.4s (400 ms) d'émergence continue
                            val newDetectedTags = mutableListOf<TrackedHarmonicTag>()
                            val reportMap = _emergenceReportEntries.value.associateBy { it.orderName }.toMutableMap()

                            for (tracker in candidateTrackerList) {
                                if (nowMs - tracker.firstSeenTimestampMs >= 400L) {
                                    val orderName = tracker.formattedOrderName
                                    val orderValue = Math.round(tracker.currentMeanOrder * 10.0) / 10.0

                                    val tag = TrackedHarmonicTag(
                                        orderName = orderName,
                                        orderValue = orderValue,
                                        freqHz = tracker.lastFreqHz,
                                        ttnrDb = tracker.maxTtnrDb,
                                        absDbFS = tracker.maxAbsDbFS,
                                        speedKmh = speedKmh,
                                        rpm = kConfig.calculateRpm(speedKmh),
                                        binIndex = tracker.binIndex,
                                        lastSeenTimestampMs = nowMs
                                    )
                                    newDetectedTags.add(tag)

                                    val existingReport = reportMap[orderName]
                                    if (existingReport != null) {
                                        existingReport.minSpeedKmh = minOf(existingReport.minSpeedKmh, tracker.minSpeedKmh)
                                        existingReport.maxSpeedKmh = maxOf(existingReport.maxSpeedKmh, tracker.maxSpeedKmh)
                                        existingReport.minRpm = minOf(existingReport.minRpm, tracker.minRpm)
                                        existingReport.maxRpm = maxOf(existingReport.maxRpm, tracker.maxRpm)
                                        existingReport.minFreqHz = minOf(existingReport.minFreqHz, tracker.minFreqHz)
                                        existingReport.maxFreqHz = maxOf(existingReport.maxFreqHz, tracker.maxFreqHz)
                                        existingReport.maxEmergenceDb = maxOf(existingReport.maxEmergenceDb, tracker.maxTtnrDb)
                                        existingReport.countDetections++
                                        existingReport.lastTimestampMs = nowMs
                                    } else {
                                        reportMap[orderName] = EmergenceReportEntry(
                                            orderName = orderName,
                                            orderValue = orderValue,
                                            minSpeedKmh = tracker.minSpeedKmh,
                                            maxSpeedKmh = tracker.maxSpeedKmh,
                                            minRpm = tracker.minRpm,
                                            maxRpm = tracker.maxRpm,
                                            minFreqHz = tracker.minFreqHz,
                                            maxFreqHz = tracker.maxFreqHz,
                                            maxEmergenceDb = tracker.maxTtnrDb,
                                            countDetections = 1,
                                            lastTimestampMs = nowMs
                                        )
                                    }
                                }
                            }

                            // Mise à jour de la rémanence (fusionner les nouveaux tags avec les tags récents encore valides)
                            val maxHoldMs = (kConfig.holdTimeSec * 1000.0).toLong().coerceAtLeast(1000L)
                            val updatedTagMap = _trackedHarmonicTags.value
                                .filter { nowMs - it.lastSeenTimestampMs < maxHoldMs }
                                .associateBy { it.orderName }
                                .toMutableMap()
                            
                            for (tag in newDetectedTags) {
                                updatedTagMap[tag.orderName] = tag
                            }
                            
                            _trackedHarmonicTags.value = updatedTagMap.values.sortedBy { it.orderValue }
                            _emergenceReportEntries.value = reportMap.values.toList()
                        }
                    }
                }
            }
        }
    }
}

    private fun stopRecording() {
        _isRecording.value = false
        audioRepository.stopAudioCapture()
    }
    
    fun exportData(pedalPercent: String, comments: String) {
        viewModelScope.launch {
            val history = fftHistory.value
            val telemHistory = _telemetryHistory.value
            if (history.isEmpty()) return@launch
            
            val bitmapWidth = history.size
            val binCount = history.first().size
            val maxF = _maxFreq.value
            val nyquistFreq = 44100 / 2
            val displayedBinCount = min(binCount, (maxF * binCount) / nyquistFreq)
            val bitmapHeight = displayedBinCount
            
            // 1. Générer le bitmap brut du spectrogramme (Absolu ou TTNR selon mode actuel)
            val spectroBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(bitmapWidth * bitmapHeight) { android.graphics.Color.BLACK }
            val mode = _displayMode.value
            val minVal = if (mode == DisplayMode.TTNR) 0.0 else _minDb.value
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
            canvas.drawText("${actualMaxFreq} Hz", 40f, curY + 30f, paintAxis)
            canvas.drawText("0 Hz", 40f, curY + spectroHeight, paintAxis)
            
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
}
