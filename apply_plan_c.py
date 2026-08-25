import os

file_path = r'app\src\main\java\com\example\nvhspectro\MainViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    code = f.read()

# Add imports
code = code.replace(
    'import com.example.nvhspectro.data.AudioFilter',
    'import com.example.nvhspectro.data.AudioFilter\nimport com.example.nvhspectro.data.BiQuadFilter\nimport com.example.nvhspectro.data.WavAudioWriter\nimport java.io.File'
)

# Update addAudioFilter / removeAudioFilter
old_methods = '''    fun addAudioFilter(filter: AudioFilter) {
        _activeFilters.value = _activeFilters.value + filter
    }
    fun removeAudioFilter(filterId: String) {
        _activeFilters.value = _activeFilters.value.filter { it.id != filterId }
    }'''
new_methods = '''    fun addAudioFilter(filter: AudioFilter) {
        _activeFilters.value = _activeFilters.value + filter
        applyDigitalFiltersToPlayback()
        _loadedWavData.value?.let { processFullWavSpectrogram(it) }
    }
    fun removeAudioFilter(filterId: String) {
        _activeFilters.value = _activeFilters.value.filter { it.id != filterId }
        applyDigitalFiltersToPlayback()
        _loadedWavData.value?.let { processFullWavSpectrogram(it) }
    }
    
    private fun applyDigitalFiltersToPlayback() {
        val originalData = _loadedWavData.value ?: return
        val filters = _activeFilters.value
        val context = getApplication<android.app.Application>()
        
        if (filters.isEmpty()) {
            // Restore original audio
            val currentPos = mediaPlayer?.currentPosition ?: 0
            if (_loadedVideoUri.value != null) {
                initMediaPlayer(uri = _loadedVideoUri.value, context = context)
            } else {
                initMediaPlayer(wavFile = File(context.cacheDir, "temp_extracted.wav")) // or URI depending on origin
                // Wait, if it's from a URI, we need to handle it properly.
                // Let's rely on _loadedVideoUri or the fact that original playback was set up earlier.
                // We will save original URI or File in the ViewModel later if needed, but for now let's just 
                // re-extract or assume we just use a temp file.
            }
            mediaPlayer?.seekTo(currentPos)
            if (_isPlayingWav.value) mediaPlayer?.start()
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val pcm = originalData.pcmData
            val filteredPcm = ShortArray(pcm.size)
            
            // Instanciation des biquads
            val biquads = filters.map { filter ->
                BiQuadFilter(filter.type, filter.minFreq.toDouble(), filter.maxFreq.toDouble(), 44100.0)
            }
            
            for (i in pcm.indices) {
                var sample = pcm[i].toDouble()
                for (bq in biquads) {
                    sample = bq.processSample(sample)
                }
                filteredPcm[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            
            val tempFile = File(context.cacheDir, "filtered_playback.wav")
            WavAudioWriter.writePcmToWav(filteredPcm, tempFile, 44100)
            
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                val currentPos = mediaPlayer?.currentPosition ?: 0
                val wasPlaying = _isPlayingWav.value || mediaPlayer?.isPlaying == true
                
                initMediaPlayer(wavFile = tempFile)
                mediaPlayer?.seekTo(currentPos)
                if (wasPlaying) {
                    mediaPlayer?.start()
                }
            }
        }
    }'''

code = code.replace(old_methods, new_methods)


# We need to make sure the original audio can be restored properly if filters are cleared.
# When a WAV or video is loaded, we usually save it. Let's add a _currentOriginalAudioFile in ViewModel.
code = code.replace(
    'private var mediaPlayer: MediaPlayer? = null',
    'private var mediaPlayer: MediaPlayer? = null\n    private var currentOriginalAudioFile: File? = null\n    private var currentOriginalAudioUri: android.net.Uri? = null'
)

# In initMediaPlayer, save the args if they are not the "filtered_playback.wav"
old_init = '''    private fun initMediaPlayer(wavFile: File? = null, uri: android.net.Uri? = null, context: android.content.Context? = null) {
        try {
            mediaPlayer?.release()'''

new_init = '''    private fun initMediaPlayer(wavFile: File? = null, uri: android.net.Uri? = null, context: android.content.Context? = null) {
        if (wavFile?.name != "filtered_playback.wav") {
            currentOriginalAudioFile = wavFile
            currentOriginalAudioUri = uri
        }
        try {
            mediaPlayer?.release()'''
code = code.replace(old_init, new_init)

# In applyDigitalFiltersToPlayback, fix the restore logic:
restore_old = '''        if (filters.isEmpty()) {
            // Restore original audio
            val currentPos = mediaPlayer?.currentPosition ?: 0
            if (_loadedVideoUri.value != null) {
                initMediaPlayer(uri = _loadedVideoUri.value, context = context)
            } else {
                initMediaPlayer(wavFile = File(context.cacheDir, "temp_extracted.wav")) // or URI depending on origin
                // Wait, if it's from a URI, we need to handle it properly.
                // Let's rely on _loadedVideoUri or the fact that original playback was set up earlier.
                // We will save original URI or File in the ViewModel later if needed, but for now let's just 
                // re-extract or assume we just use a temp file.
            }
            mediaPlayer?.seekTo(currentPos)
            if (_isPlayingWav.value) mediaPlayer?.start()
            return
        }'''

restore_new = '''        if (filters.isEmpty()) {
            val currentPos = mediaPlayer?.currentPosition ?: 0
            val wasPlaying = _isPlayingWav.value || mediaPlayer?.isPlaying == true
            initMediaPlayer(wavFile = currentOriginalAudioFile, uri = currentOriginalAudioUri, context = context)
            mediaPlayer?.seekTo(currentPos)
            if (wasPlaying) {
                mediaPlayer?.start()
            }
            return
        }'''
code = code.replace(restore_old, restore_new)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(code)

print("Updated MainViewModel.kt with BiQuad filters")
