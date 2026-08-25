import sys

with open('app/src/main/java/com/example/nvhspectro/MainViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add states for report snapshot
new_states = '''
    private val _reportFftHistory = MutableStateFlow<List<DoubleArray>>(emptyList())
    val reportFftHistory: StateFlow<List<DoubleArray>> = _reportFftHistory.asStateFlow()

    private val _reportFftHistoryAbsolute = MutableStateFlow<List<DoubleArray>>(emptyList())
    val reportFftHistoryAbsolute: StateFlow<List<DoubleArray>> = _reportFftHistoryAbsolute.asStateFlow()

    private val _reportFftHistoryTTNR = MutableStateFlow<List<DoubleArray>>(emptyList())
    val reportFftHistoryTTNR: StateFlow<List<DoubleArray>> = _reportFftHistoryTTNR.asStateFlow()

    private val _isDrawingMode = MutableStateFlow(false)
    val isDrawingMode: StateFlow<Boolean> = _isDrawingMode.asStateFlow()

    fun toggleDrawingMode() {
        _isDrawingMode.value = !_isDrawingMode.value
    }
'''

# Find a good place to insert this, let's insert it before al currentUserPoints
insert_pos = content.find('val currentUserPoints')
if insert_pos != -1:
    content = content[:insert_pos] + new_states + "\n    " + content[insert_pos:]

# Replace toggleReportMode
toggle_old = '''    fun toggleReportMode() {
        if (!_isReportModeActive.value) {
            if (_audioSourceMode.value == AudioSourceMode.LIVE) {
                _isFrozen.value = true
            }
            _isReportModeActive.value = true
        } else {
            _isReportModeActive.value = false
            _currentUserPoints.value = emptyList()
            _currentSmartPath.value = emptyList()
        }
    }'''

toggle_new = '''    fun toggleReportMode() {
        if (!_isReportModeActive.value) {
            _reportFftHistory.value = _fftHistory.value.toList()
            _reportFftHistoryAbsolute.value = _fftHistoryAbsolute.value.toList()
            _reportFftHistoryTTNR.value = _fftHistoryTTNR.value.toList()
            _isReportModeActive.value = true
        } else {
            _isReportModeActive.value = false
            _currentUserPoints.value = emptyList()
            _currentSmartPath.value = emptyList()
            _isDrawingMode.value = false
        }
    }'''

content = content.replace(toggle_old, toggle_new)

with open('app/src/main/java/com/example/nvhspectro/MainViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("MainViewModel updated.")
