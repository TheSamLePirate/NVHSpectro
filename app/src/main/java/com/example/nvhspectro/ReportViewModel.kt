package com.example.nvhspectro

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nvhspectro.data.ManualOrderAnchor
import com.example.nvhspectro.data.SmartTrackedOrder
import com.example.nvhspectro.data.TimelineMapper
import com.example.nvhspectro.export.PdfReportGenerator
import com.example.nvhspectro.export.PngExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [plan 3.3] The report third of the historical MainViewModel: report-mode
 * snapshots, assisted manual order tracing (computed in :core's
 * SmartPathTracker) and the PNG/PDF exports (rendered off the UI thread in
 * the export/ package). Shared state lives in [session].
 */
class ReportViewModel(application: Application, val session: MeasurementSession) : AndroidViewModel(application) {

    private val _isReportModeActive = MutableStateFlow(false)
    val isReportModeActive: StateFlow<Boolean> = _isReportModeActive.asStateFlow()

    private val _reportFftHistory = MutableStateFlow<List<DoubleArray>>(emptyList())
    val reportFftHistory: StateFlow<List<DoubleArray>> = _reportFftHistory.asStateFlow()

    private val _reportFftHistoryAbsolute = MutableStateFlow<List<DoubleArray>>(emptyList())
    val reportFftHistoryAbsolute: StateFlow<List<DoubleArray>> = _reportFftHistoryAbsolute.asStateFlow()

    private val _reportFftHistoryTTNR = MutableStateFlow<List<DoubleArray>>(emptyList())
    val reportFftHistoryTTNR: StateFlow<List<DoubleArray>> = _reportFftHistoryTTNR.asStateFlow()

    private val _isBrillanceModeEnabled = MutableStateFlow(false)
    val isBrillanceModeEnabled: StateFlow<Boolean> = _isBrillanceModeEnabled.asStateFlow()

    fun toggleBrillanceMode() {
        _isBrillanceModeEnabled.value = !_isBrillanceModeEnabled.value
    }

    private val _currentUserPoints = MutableStateFlow<List<ManualOrderAnchor>>(emptyList())
    val currentUserPoints: StateFlow<List<ManualOrderAnchor>> = _currentUserPoints.asStateFlow()

    private val _currentSmartPath = MutableStateFlow<List<ManualOrderAnchor>>(emptyList())
    val currentSmartPath: StateFlow<List<ManualOrderAnchor>> = _currentSmartPath.asStateFlow()

    private val _manualTrackedOrders = MutableStateFlow<List<SmartTrackedOrder>>(emptyList())
    val manualTrackedOrders: StateFlow<List<SmartTrackedOrder>> = _manualTrackedOrders.asStateFlow()

    private val _selectedValidatedOrder = MutableStateFlow<SmartTrackedOrder?>(null)
    val selectedValidatedOrder: StateFlow<SmartTrackedOrder?> = _selectedValidatedOrder.asStateFlow()

    fun toggleReportMode() {
        if (!_isReportModeActive.value) {
            _reportFftHistory.value = session.fftHistory.value.toList()
            _reportFftHistoryAbsolute.value = session.fftHistoryAbsolute.value.toList()
            _reportFftHistoryTTNR.value = session.fftHistoryTTNR.value.toList()
            _isReportModeActive.value = true
        } else {
            _isReportModeActive.value = false
            clearCurrentPoints()
        }
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

    fun addManualTrackPoint(frameIndex: Int, binIndex: Int) {
        val currentPoints = _currentUserPoints.value.toMutableList()
        currentPoints.add(ManualOrderAnchor(frameIndex, binIndex, isUserPlaced = true))
        currentPoints.sortBy { it.frameIndex }
        _currentUserPoints.value = currentPoints

        val historyToUse = if (session.displayMode.value == DisplayMode.TTNR) {
            _reportFftHistoryTTNR.value
        } else {
            _reportFftHistoryAbsolute.value
        }
        _currentSmartPath.value = SmartPathTracker.compute(currentPoints, historyToUse)
    }

    fun validateCurrentOrder(customName: String? = null) {
        val path = _currentSmartPath.value
        if (path.isEmpty()) return

        val reportHistory = _reportFftHistory.value
        val reportHistoryTTNR = _reportFftHistoryTTNR.value
        val telemHistory = session.telemetryHistory.value
        val kConfig = session.kinematicsConfig.value
        val nyquist = session.analysisSampleRate / 2.0
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
                // [C17] Frame indices are NOT telemetry indices — map by time ratio.
                val telem = telemHistory[TimelineMapper.mapIndex(f, reportHistory.size, telemHistory.size)]
                val speed = if (kConfig.isEnabled) telem.theoreticalSpeedKmh else telem.speedKmh
                if (speed > 1.0f) {
                    if (minSpeed == null || speed < minSpeed) minSpeed = speed
                    if (maxSpeed == null || speed > maxSpeed) maxSpeed = speed
                    val rpm = kConfig.calculateRpm(speed).toInt()
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
        _manualTrackedOrders.value = _manualTrackedOrders.value + SmartTrackedOrder(
            name = customName?.takeIf { it.isNotBlank() } ?: "Ordre ${count + 1}",
            color = ORDER_COLORS[count % ORDER_COLORS.size],
            path = path,
            minRpm = minRpm,
            maxRpm = maxRpm,
            minSpeedKmh = minSpeed,
            maxSpeedKmh = maxSpeed,
            minFreqHz = minFreqHz,
            maxFreqHz = maxFreqHz,
            maxEmergenceDb = maxEmergence
        )
        clearCurrentPoints()
    }

    // --------------------------------------------------------------- exports

    /** [C6-export] PNG snapshot rendered on Default, written via MediaStore. */
    fun exportData(pedalPercent: String, comments: String) {
        val input = PngExporter.Input(
            history = session.fftHistory.value,
            telemetryHistory = session.telemetryHistory.value,
            currentTelemetry = session.telemetryState.value,
            displayMode = session.displayMode.value,
            minDb = session.minDb.value,
            maxDb = session.maxDb.value,
            maxFreq = session.maxFreq.value,
            sampleRate = session.analysisSampleRate,
            timeWindowSec = session.timeWindowSec.value,
            historySize = session.historySize,
            pedalPercent = pedalPercent,
            comments = comments
        )
        viewModelScope.launch(Dispatchers.Default) {
            PngExporter.export(getApplication(), input)
        }
    }

    fun savePdfToUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outStream ->
                    PdfReportGenerator.generateReport(
                        context = context,
                        outStream = outStream,
                        historyAbs = _reportFftHistoryAbsolute.value,
                        historyTtnr = _reportFftHistoryTTNR.value,
                        minDb = session.minDb.value,
                        maxDb = session.maxDb.value,
                        trackedOrders = _manualTrackedOrders.value,
                        kinematicsConfig = session.kinematicsConfig.value,
                        globalMaxFreq = session.maxFreq.value.toFloat(),
                        sampleRate = session.analysisSampleRate
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    session.postNotice("❌ Export PDF impossible : ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    companion object {
        private val ORDER_COLORS = listOf(
            Color(0xFFB026FF),
            Color(0xFFFF1493),
            Color(0xFF32CD32),
            Color(0xFFFFA500),
            Color(0xFF8A2BE2),
            Color(0xFF00FFFF),
            Color(0xFFFFD700)
        )
    }
}
