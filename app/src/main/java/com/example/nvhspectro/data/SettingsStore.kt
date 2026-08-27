package com.example.nvhspectro.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.nvhspectro.MeasurementSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val Context.nvhSettingsDataStore by preferencesDataStore(name = "nvh_settings")

/**
 * [S1, plan 3.6] Settings + kinematics survive process death. The historical
 * app persisted NOTHING — an overnight OS kill silently discarded the whole
 * test configuration, including the painstakingly-entered GMPe chain.
 *
 * Restore runs once at startup (before observers start, so defaults never
 * clobber stored values); every later change is written back, debounced so
 * slider drags do not hammer the disk.
 */
class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.nvhSettingsDataStore
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun restoreInto(session: MeasurementSession) {
        try {
            val prefs = dataStore.data.first()
            session.updateDisplaySettings(
                newMinDb = prefs[KEY_MIN_DB] ?: session.minDb.value,
                newMaxDb = prefs[KEY_MAX_DB] ?: session.maxDb.value,
                newMinFreq = prefs[KEY_MIN_FREQ] ?: session.minFreq.value,
                newMaxFreq = prefs[KEY_MAX_FREQ] ?: session.maxFreq.value,
                newTimeWindowSec = prefs[KEY_TIME_WINDOW] ?: session.timeWindowSec.value
            )
            prefs[KEY_FFT_SIZE]?.let { session.setFftSize(it) }
            session.updateDetectorSettings(
                enabled = prefs[KEY_DETECTOR_ENABLED] ?: session.isDetectorEnabled.value,
                thresholdDb = prefs[KEY_DETECTOR_THRESHOLD] ?: session.emergenceThresholdDb.value,
                magnitudeGateDb = prefs[KEY_DETECTOR_GATE] ?: session.magnitudeGateDbFS.value
            )
            prefs[KEY_KINEMATICS]?.let { stored ->
                try {
                    session.setKinematicsConfig(json.decodeFromString(KinematicsConfig.serializer(), stored))
                } catch (e: Exception) {
                    // A malformed blob must never block startup; defaults stand.
                }
            }
        } catch (e: Exception) {
            // First run / unreadable store: defaults stand.
        }
    }

    @OptIn(FlowPreview::class)
    fun startObserving(session: MeasurementSession, scope: CoroutineScope) {
        scope.launch {
            combine(
                listOf(
                    session.minDb, session.maxDb, session.fftSize, session.minFreq,
                    session.maxFreq, session.timeWindowSec, session.isDetectorEnabled,
                    session.emergenceThresholdDb, session.magnitudeGateDbFS, session.kinematicsConfig
                )
            ) { it.copyOf() }
                .debounce(WRITE_DEBOUNCE_MS)
                .collect { values ->
                    try {
                        dataStore.edit { prefs ->
                            prefs[KEY_MIN_DB] = values[0] as Double
                            prefs[KEY_MAX_DB] = values[1] as Double
                            prefs[KEY_FFT_SIZE] = values[2] as Int
                            prefs[KEY_MIN_FREQ] = values[3] as Int
                            prefs[KEY_MAX_FREQ] = values[4] as Int
                            prefs[KEY_TIME_WINDOW] = values[5] as Double
                            prefs[KEY_DETECTOR_ENABLED] = values[6] as Boolean
                            prefs[KEY_DETECTOR_THRESHOLD] = values[7] as Double
                            prefs[KEY_DETECTOR_GATE] = values[8] as Double
                            prefs[KEY_KINEMATICS] =
                                json.encodeToString(KinematicsConfig.serializer(), values[9] as KinematicsConfig)
                        }
                    } catch (e: Exception) {
                        // A failed write loses one snapshot, not the app.
                    }
                }
        }
    }

    private companion object {
        val KEY_MIN_DB = doublePreferencesKey("minDb")
        val KEY_MAX_DB = doublePreferencesKey("maxDb")
        val KEY_FFT_SIZE = intPreferencesKey("fftSize")
        val KEY_MIN_FREQ = intPreferencesKey("minFreq")
        val KEY_MAX_FREQ = intPreferencesKey("maxFreq")
        val KEY_TIME_WINDOW = doublePreferencesKey("timeWindowSec")
        val KEY_DETECTOR_ENABLED = booleanPreferencesKey("detectorEnabled")
        val KEY_DETECTOR_THRESHOLD = doublePreferencesKey("detectorThresholdDb")
        val KEY_DETECTOR_GATE = doublePreferencesKey("detectorGateDbFS")
        val KEY_KINEMATICS = stringPreferencesKey("kinematicsConfig")
        const val WRITE_DEBOUNCE_MS = 500L
    }
}
