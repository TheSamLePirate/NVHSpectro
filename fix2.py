import sys
import re

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Fix imports
text = text.replace('import com.example.nvhspectro.HarmonicTag', 'import com.example.nvhspectro.data.TrackedHarmonicTag')
text = text.replace('import com.example.nvhspectro.AudioFilterConfig', 'import com.example.nvhspectro.data.AudioFilter')
text = text.replace('import com.example.nvhspectro.KinematicsConfig', 'import com.example.nvhspectro.data.KinematicsConfig')
text = text.replace('import com.example.nvhspectro.TrackedOrder', 'import com.example.nvhspectro.data.SmartTrackedOrder')
text = text.replace('import com.example.nvhspectro.PointF', 'import android.graphics.PointF')

# Fix types in SpectrogramArea
text = text.replace('trackedHarmonicTags: List<HarmonicTag>', 'trackedHarmonicTags: List<TrackedHarmonicTag>')
text = text.replace('activeFilters: List<AudioFilterConfig>', 'activeFilters: List<AudioFilter>')
text = text.replace('manualTrackedOrders: List<TrackedOrder>', 'manualTrackedOrders: List<SmartTrackedOrder>')

# Fix types in fft history
text = text.replace('fftHistory: List<FloatArray>', 'fftHistory: List<DoubleArray>')
text = text.replace('fftHistoryAbsolute: List<FloatArray>', 'fftHistoryAbsolute: List<DoubleArray>')
text = text.replace('fftHistoryTTNR: List<FloatArray>', 'fftHistoryTTNR: List<DoubleArray>')

# Fix getEffectiveV1000
text = text.replace('kinematicsConfig.getEffectiveV1000()', 'kinematicsConfig.effectiveV1000')

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text)
