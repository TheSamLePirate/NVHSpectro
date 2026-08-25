import re

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Fix SpectrogramColormap to SpectrogramCanvas
text = text.replace('SpectrogramColormap', 'SpectrogramCanvas')

# Fix isWavAnalyzerMode
old_isWav = 'val isWavAnalyzerMode by viewModel.isWavAnalyzerMode.collectAsState()'
new_isWav = '''val audioSourceMode by viewModel.audioSourceMode.collectAsState()
    val isWavAnalyzerMode = (audioSourceMode == com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER || audioSourceMode == com.example.nvhspectro.AudioSourceMode.VIDEO)'''
text = text.replace(old_isWav, new_isWav)

# Fix sampleRate
old_sampleRate = 'val sampleRate by viewModel.sampleRate.collectAsState()'
new_sampleRate = 'val sampleRate = 44100'
text = text.replace(old_sampleRate, new_sampleRate)

# Add missing import for AudioSourceMode
if 'import com.example.nvhspectro.AudioSourceMode' not in text:
    text = text.replace('import com.example.nvhspectro.DisplayMode', 'import com.example.nvhspectro.DisplayMode\nimport com.example.nvhspectro.AudioSourceMode')

# Fix addManualPoint
# Let's check if viewModel has addManualPoint(Int, Int) or addManualAnchor(ManualOrderAnchor)
# Actually, I'll just look up what MainViewModel has, but first let me just replace addManualPoint if needed.
# If I don't know the exact signature of addManualPoint, I should check MainViewModel.kt.
# I will do that in the next step.

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text)
