import os

# 1. FFTProcessor.kt
fft_path = r'app\src\main\java\com\example\nvhspectro\FFTProcessor.kt'
with open(fft_path, 'r', encoding='utf-8') as f:
    fft = f.read()

# Change minEmergenceRequired
fft = fft.replace(
'''                val minEmergenceRequired = when {
                    f < 1500.0 -> 4.5
                    f < 4000.0 -> 3.8
                    else -> 4.0
                }''',
'''                val minEmergenceRequired = -3.0'''
)

# Change coercion of finalPeakTtnr from 0.0 to -3.0
fft = fft.replace(
'''maxOf(ttnrCbDb, localEmergenceDb - 1.5).coerceIn(0.0, 30.0)''',
'''maxOf(ttnrCbDb, localEmergenceDb - 1.5).coerceIn(-3.0, 30.0)'''
)

# Change else 0.0 to else -100.0
fft = fft.replace(
'''} else {
                    0.0
                }''',
'''} else {
                    -100.0
                }'''
)

# Change finalPeakTtnr >= 1.0 to >= -3.0
fft = fft.replace(
'''if (finalPeakTtnr >= 1.0) {''',
'''if (finalPeakTtnr >= -3.0) {'''
)

# Change * 0.45 leakage to -4.0 (so it works with negative dB)
fft = fft.replace(
'''< finalPeakTtnr * 0.45) {
                        rawTtnr[i - 1] = finalPeakTtnr * 0.45''',
'''< finalPeakTtnr - 4.0) {
                        rawTtnr[i - 1] = finalPeakTtnr - 4.0'''
)
fft = fft.replace(
'''< finalPeakTtnr * 0.45) {
                        rawTtnr[i + 1] = finalPeakTtnr * 0.45''',
'''< finalPeakTtnr - 4.0) {
                        rawTtnr[i + 1] = finalPeakTtnr - 4.0'''
)

# Change hasStructure to work with negative dB
fft = fft.replace(
'''val hasStructure = (prevVal >= 0.20 * valCurr || nextVal >= 0.20 * valCurr)''',
'''val hasStructure = (prevVal >= valCurr - 8.0 || nextVal >= valCurr - 8.0)'''
)

# Change valCurr <= 0.0 to <= -3.0
fft = fft.replace('if (valCurr <= 0.0) continue', 'if (valCurr <= -3.0) continue')

# Change prevVal/nextVal default from 0.0 to -100.0
fft = fft.replace('else 0.0\n            val nextVal', 'else -100.0\n            val nextVal')
fft = fft.replace('else 0.0\n\n            val hasStructure', 'else -100.0\n\n            val hasStructure')

# Change filteredTtnr else assignment from 0.0 to -100.0
fft = fft.replace('filteredTtnr[i] = 0.0', 'filteredTtnr[i] = -100.0')

# Change EMA threshold from 2.0 to -3.0
fft = fft.replace('integVal < 2.0', 'integVal < -3.0')
fft = fft.replace('filteredTtnr[i] < 2.0', 'filteredTtnr[i] < -3.0')

# Force 0.0 to -100.0 in EMA else
fft = fft.replace('0.0 else integVal', '-100.0 else integVal')
fft = fft.replace('0.0 else filteredTtnr[i]', '-100.0 else filteredTtnr[i]')

with open(fft_path, 'w', encoding='utf-8') as f:
    f.write(fft)


# 2. MainViewModel.kt
vm_path = r'app\src\main\java\com\example\nvhspectro\MainViewModel.kt'
with open(vm_path, 'r', encoding='utf-8') as f:
    vm = f.read()

# Change the 150ms zero amputation gate threshold from 2.0 to -3.0
vm = vm.replace('if (rawTtnr[i] >= 2.0) {', 'if (rawTtnr[i] >= -3.0) {')

# Change validatedTtnr assignment from 0.0 to -100.0
vm = vm.replace('validatedTtnr[i] = 0.0', 'validatedTtnr[i] = -100.0')

# Change minVal to -3.0
vm = vm.replace('val minVal = if (mode == DisplayMode.TTNR) 0.0 else _minDb.value', 'val minVal = if (mode == DisplayMode.TTNR) -3.0 else _minDb.value')

with open(vm_path, 'w', encoding='utf-8') as f:
    f.write(vm)


# 3. SpectrogramColormap.kt
color_path = r'app\src\main\java\com\example\nvhspectro\SpectrogramColormap.kt'
with open(color_path, 'r', encoding='utf-8') as f:
    color = f.read()

color = color.replace('val effectiveMin = if (displayMode == DisplayMode.TTNR) 0.0 else minDb', 'val effectiveMin = if (displayMode == DisplayMode.TTNR) -3.0 else minDb')
color = color.replace('if (displayMode == DisplayMode.TTNR && magnitude < 0.8)', 'if (displayMode == DisplayMode.TTNR && magnitude < -3.0)')

with open(color_path, 'w', encoding='utf-8') as f:
    f.write(color)

print("Applied simple sensitivity fixes")
