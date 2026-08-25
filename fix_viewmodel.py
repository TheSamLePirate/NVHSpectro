import os

file_path = r'app\src\main\java\com\example\nvhspectro\MainViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    code = f.read()

# Fix _isPlayingWav -> _isWavPlaying
code = code.replace('_isPlayingWav', '_isWavPlaying')

# Fix pcmData -> pcmSamples
code = code.replace('originalData.pcmData', 'originalData.pcmSamples')

# Fix ambiguous import java.io.File. We will just search for all imports and remove java.io.File, then re-add exactly one.
# Wait, let's just replace all 'import java.io.File' with empty string and add it once.
code = code.replace('import java.io.File\n', '')
code = code.replace(
    'import android.media.MediaPlayer',
    'import android.media.MediaPlayer\nimport java.io.File'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(code)

print("Fixed MainViewModel.kt")
