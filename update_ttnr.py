import os, re

viewmodel_path = r'app\src\main\java\com\example\nvhspectro\MainViewModel.kt'
with open(viewmodel_path, 'r', encoding='utf-8') as f:
    vm_code = f.read()

# Add recentPowerBuffer
vm_code = vm_code.replace(
    'private var previousTTNRSpectrum = DoubleArray(0)',
    'private var previousTTNRSpectrum = DoubleArray(0)\n    private val recentPowerBuffer = mutableListOf<DoubleArray>()'
)

# Replace audio processing block using regex
pattern_audio_proc = r'(// Traitement FFT Absolu.*?_fftHistoryTTNR\.value = curTtnr)'

new_audio_proc = '''// Traitement FFT Absolu
                    val magnitudes = fftProcessor.processFFT(audioBuffer)
                    val validMags = magnitudes.copyOfRange(0, magnitudes.size / 2)
                    
                    // Welch Averaging: Convert to linear power
                    val powerArray = DoubleArray(magnitudes.size) { i -> Math.pow(10.0, magnitudes[i] / 10.0) }
                    recentPowerBuffer.add(0, powerArray)
                    if (recentPowerBuffer.size > 5) recentPowerBuffer.removeLast()

                    val averagedPower = DoubleArray(magnitudes.size)
                    val nFrames = recentPowerBuffer.size.toDouble()
                    for (pArr in recentPowerBuffer) {
                        for (i in pArr.indices) {
                            averagedPower[i] += pArr[i]
                        }
                    }

                    val averagedMagnitudesDbFS = DoubleArray(magnitudes.size) { i ->
                        10.0 * kotlin.math.log10((averagedPower[i] / nFrames).coerceAtLeast(1e-12))
                    }

                    // Traitement TTNR sur spectre moyenné (Welch Method)
                    val rawTtnr = fftProcessor.computeTTNR(averagedMagnitudesDbFS, 44100)

                    val ttnrSpectrum = DoubleArray(rawTtnr.size)
                    if (previousTTNRSpectrum.size == rawTtnr.size) {
                        for (i in rawTtnr.indices) {
                            ttnrSpectrum[i] = 0.50 * rawTtnr[i] + 0.50 * previousTTNRSpectrum[i]
                        }
                    } else {
                        System.arraycopy(rawTtnr, 0, ttnrSpectrum, 0, rawTtnr.size)
                    }
                    previousTTNRSpectrum = ttnrSpectrum
                    _latestTTNRSpectrum.value = ttnrSpectrum
                    
                    // Mettre a jour l'historique Absolu
                    val curAbs = _fftHistoryAbsolute.value.toMutableList()
                    curAbs.add(0, magnitudes)
                    if (curAbs.size > maxHist) curAbs.removeLast()
                    _fftHistoryAbsolute.value = curAbs

                    // Mettre a jour l'historique TTNR
                    val curTtnr = _fftHistoryTTNR.value.toMutableList()
                    curTtnr.add(0, ttnrSpectrum)
                    if (curTtnr.size > maxHist) curTtnr.removeLast()
                    _fftHistoryTTNR.value = curTtnr'''

vm_code = re.sub(pattern_audio_proc, new_audio_proc, vm_code, flags=re.DOTALL)

# Replace ttnrPersistenceCount logic declaration removal
vm_code = re.sub(r'private var ttnrPersistenceCount = IntArray\(0\)\s*private val recentRawTTNRBuffer = mutableListOf<DoubleArray>\(\)\s*', '', vm_code)

# Replace DisplayMode.TTNR minVal
vm_code = vm_code.replace(
    'val minVal = if (mode == DisplayMode.TTNR) 0.0 else _minDb.value',
    'val minVal = if (mode == DisplayMode.TTNR) -3.0 else _minDb.value'
)

with open(viewmodel_path, 'w', encoding='utf-8') as f:
    f.write(vm_code)

print("Updated MainViewModel.kt")

fft_path = r'app\src\main\java\com\example\nvhspectro\FFTProcessor.kt'
with open(fft_path, 'r', encoding='utf-8') as f:
    fft_code = f.read()

# Replace minEmergenceRequired
pattern_emerg = r'(val minEmergenceRequired = when \{.*?else -> 4\.0\s*\})'
new_emerg = 'val minEmergenceRequired = -3.0'
fft_code = re.sub(pattern_emerg, new_emerg, fft_code, flags=re.DOTALL)

# Replace hasStructure
pattern_struct = r'val hasStructure = \(prevVal >= 0\.20 \* valCurr \|\| nextVal >= 0\.20 \* valCurr\)'
new_struct = 'val hasStructure = (prevVal >= -3.0 || nextVal >= -3.0 || valCurr >= 2.0)'
fft_code = re.sub(pattern_struct, new_struct, fft_code)

# Update the clamp from 0.0 to -3.0
fft_code = fft_code.replace('maxOf(ttnrCbDb, localEmergenceDb - 1.5).coerceIn(0.0, 30.0)', 'maxOf(ttnrCbDb, localEmergenceDb - 1.5).coerceIn(-3.0, 30.0)')
fft_code = fft_code.replace('} else {\n                    0.0\n                }', '} else {\n                    -100.0\n                }')
fft_code = fft_code.replace('if (finalPeakTtnr >= 1.0) {', 'if (finalPeakTtnr >= -3.0) {')

fft_code = fft_code.replace('filteredTtnr[i] = 0.0', 'filteredTtnr[i] = -100.0')

# Also, at the start of loop, set to -100 instead of 0
fft_code = fft_code.replace('if (valCurr <= 0.0) continue', 'if (valCurr <= -3.0) continue')
fft_code = fft_code.replace('else 0.0', 'else -100.0')

with open(fft_path, 'w', encoding='utf-8') as f:
    f.write(fft_code)

print("Updated FFTProcessor.kt")
