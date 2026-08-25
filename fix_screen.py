import os

file_path = r'app\src\main\java\com\example\nvhspectro\MainScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    code = f.read()

old_call = '''                SpectrogramCanvas(
                    history = fftHistory,
                    absHistory = fftHistoryAbsolute,
                    ttnrHistory = fftHistoryTTNR,
                    minDb = minDb,
                    maxDb = maxDb,
                    minFreq = minFreq,
                    maxFreq = maxFreq,
                    fftSize = fftSize,
                    sampleRate = 44100,
                    historySize = if (isWavMode && fftHistoryAbsolute.isNotEmpty()) fftHistoryAbsolute.size else viewModel.historySize,
                    displayMode = displayMode,
                    isDetectorEnabled = isDetectorEnabled,
                    emergenceThresholdDb = emergenceThresholdDb,
                    magnitudeGateDbFS = magnitudeGateDbFS,
                    trackedHarmonicTags = trackedHarmonicTags,'''

new_call = '''                SpectrogramCanvas(
                    history = fftHistory,
                    absHistory = fftHistoryAbsolute,
                    ttnrHistory = fftHistoryTTNR,
                    minDb = minDb,
                    maxDb = maxDb,
                    minFreq = minFreq,
                    maxFreq = maxFreq,
                    fftSize = fftSize,
                    sampleRate = 44100,
                    historySize = if (isWavMode && fftHistoryAbsolute.isNotEmpty()) fftHistoryAbsolute.size else viewModel.historySize,
                    displayMode = displayMode,
                    isDetectorEnabled = isDetectorEnabled,
                    emergenceThresholdDb = emergenceThresholdDb,
                    magnitudeGateDbFS = magnitudeGateDbFS,
                    trackedHarmonicTags = trackedHarmonicTags,
                    activeFilters = activeFilters,'''

code = code.replace(old_call, new_call)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(code)

print("Fixed MainScreen.kt")
