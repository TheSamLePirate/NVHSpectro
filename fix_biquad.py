import os

file_path = r'app\src\main\java\com\example\nvhspectro\MainViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    code = f.read()

old_biquads = '''            // Cascading 8 times for a brickwall effect (-96 dB/octave)
            val biquads = filters.flatMap { filter ->
                (1..8).map { BiQuadFilter(filter.type, filter.minFreq.toDouble(), filter.maxFreq.toDouble(), 44100.0) }
            }'''

new_biquads = '''            // Cascading 4 times with specific Q factors for a true 8th-order Butterworth filter (-48 dB/octave)
            // Cela Ǹvite que le filtre "bave" avant la frǸquence de coupure.
            val qFactors = listOf(0.509795579, 0.601344887, 0.899976223, 2.562915448)
            val biquads = filters.flatMap { filter ->
                qFactors.map { q -> 
                    BiQuadFilter(filter.type, filter.minFreq.toDouble(), filter.maxFreq.toDouble(), 44100.0, q) 
                }
            }'''
            
code = code.replace(old_biquads, new_biquads)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(code)

print("Fixed MainViewModel.kt")
