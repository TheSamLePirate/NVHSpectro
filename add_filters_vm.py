import os

vm_path = r'app\src\main\java\com\example\nvhspectro\MainViewModel.kt'
with open(vm_path, 'r', encoding='utf-8') as f:
    vm = f.read()

# Add import
vm = vm.replace('import androidx.lifecycle.AndroidViewModel', 'import androidx.lifecycle.AndroidViewModel\nimport com.example.nvhspectro.data.AudioFilter')

# Add StateFlow
vm = vm.replace(
    'val kinematicsConfig: StateFlow<KinematicsConfig> = _kinematicsConfig.asStateFlow()',
    'val kinematicsConfig: StateFlow<KinematicsConfig> = _kinematicsConfig.asStateFlow()\n\n    private val _activeFilters = MutableStateFlow<List<AudioFilter>>(emptyList())\n    val activeFilters: StateFlow<List<AudioFilter>> = _activeFilters.asStateFlow()\n    fun addAudioFilter(filter: AudioFilter) {\n        _activeFilters.value = _activeFilters.value + filter\n    }\n    fun removeAudioFilter(filterId: String) {\n        _activeFilters.value = _activeFilters.value.filter { it.id != filterId }\n    }'
)

# In processWavAudio, apply filters
filter_apply = '''
                val filters = _activeFilters.value
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
                }
                val rawTtnr = localFftProcessor.computeTTNR(magnitudes, sampleRate)
'''
vm = vm.replace(
    'val rawTtnr = localFftProcessor.computeTTNR(magnitudes, sampleRate)',
    filter_apply
)

# In processAudioChunk (Live mode), apply filters
filter_apply_live = '''
                    val filters = _activeFilters.value
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
                    }
                    val rawTtnr = fftProcessor.computeTTNR(magnitudes, 44100)
'''
vm = vm.replace(
    'val rawTtnr = fftProcessor.computeTTNR(magnitudes, 44100)',
    filter_apply_live
)


with open(vm_path, 'w', encoding='utf-8') as f:
    f.write(vm)

print("Added activeFilters to MainViewModel")
