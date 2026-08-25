import re

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Add imports
if 'import com.example.nvhspectro.SpectrogramCanvas' not in text:
    text = text.replace('import com.example.nvhspectro.MainViewModel', 'import com.example.nvhspectro.MainViewModel\nimport com.example.nvhspectro.SpectrogramCanvas')
if 'import androidx.compose.ui.platform.LocalContext' not in text:
    text = text.replace('import androidx.compose.ui.unit.sp', 'import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.platform.LocalContext')

# Add context
if 'val context = LocalContext.current' not in text:
    text = text.replace('val sampleRate = 44100', 'val sampleRate = 44100\n    val context = LocalContext.current')

# Fix generatePdfReport
text = text.replace('generatePdfReport(null)', 'generatePdfReport(context)')

# Fix lambda types
text = text.replace('onAddManualPoint = { frameIdx, binIdx ->', 'onAddManualPoint = { frameIdx: Int, binIdx: Int ->')

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text)
