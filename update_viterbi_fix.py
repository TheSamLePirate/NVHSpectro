import re

with open('app/src/main/java/com/example/nvhspectro/MainViewModel.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Fix the bug
bad_code = '''        // Energie en dB
        val dbEnergies = Array(numFrames) { FloatArray(binCount) }
        for (f in 0 until numFrames) {
            val globalFrame = startFrame + f
            val spectrum = historyToUse[globalFrame]
            for (b in 0 until binCount) {
                val raw = spectrum[b]
                dbEnergies[f][b] = if (raw > 0.0) (10.0 * Math.log10(raw)).toFloat() else -100f
            }
        }'''

good_code = '''        // Energie en dB (deja calcule par FFTProcessor)
        val dbEnergies = Array(numFrames) { FloatArray(binCount) }
        for (f in 0 until numFrames) {
            val globalFrame = startFrame + f
            val spectrum = historyToUse[globalFrame]
            for (b in 0 until binCount) {
                dbEnergies[f][b] = spectrum[b].toFloat()
            }
        }'''

if bad_code in text:
    text = text.replace(bad_code, good_code)
    with open('app/src/main/java/com/example/nvhspectro/MainViewModel.kt', 'w', encoding='utf-8') as f:
        f.write(text)
    print("Fixed the log10 bug!")
else:
    print("Could not find the bad code. Please check.")
