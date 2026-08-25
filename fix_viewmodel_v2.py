import os

file_path = r'app\src\main\java\com\example\nvhspectro\MainViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Remove magnitude zeroing in processFullWavSpectrogram
block1 = '''                val filters = _activeFilters.value
                if (filters.isNotEmpty()) {
                    val df = sampleRate.toDouble() / fftProcessor.fftSize
                    for (i in magnitudes.indices) {
                        val f = i * df
                        var allowed = true
                        for (filter in filters) {
                            if (!filter.isFrequencyAllowed(f)) {
                                allowed = false
                                break
                            }
                        }
                        if (!allowed) {
                            magnitudes[i] = -120.0
                        }
                    }
                }'''
code = code.replace(block1, '')

# 2. Remove magnitude zeroing in processWavFrameAt
block2 = '''                    val filters = _activeFilters.value
                    if (filters.isNotEmpty()) {
                        val df = 44100.0 / fftProcessor.fftSize
                        for (i in magnitudes.indices) {
                            val f = i * df
                            var allowed = true
                            for (filter in filters) {
                                if (!filter.isFrequencyAllowed(f)) {
                                    allowed = false
                                    break
                                }
                            }
                            if (!allowed) {
                                magnitudes[i] = -120.0
                            }
                        }
                    }'''
code = code.replace(block2, '')


# 3. Cascade BiQuads 8 times
old_biquads = '''            val biquads = filters.map { filter ->
                BiQuadFilter(filter.type, filter.minFreq.toDouble(), filter.maxFreq.toDouble(), 44100.0)
            }'''
            
new_biquads = '''            // Cascading 8 times for a brickwall effect (-96 dB/octave)
            val biquads = filters.flatMap { filter ->
                (1..8).map { BiQuadFilter(filter.type, filter.minFreq.toDouble(), filter.maxFreq.toDouble(), 44100.0) }
            }'''
            
code = code.replace(old_biquads, new_biquads)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(code)

print("Fixed MainViewModel.kt for Colormaps and Audio Cascade")
