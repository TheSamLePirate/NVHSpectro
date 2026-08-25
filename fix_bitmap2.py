import sys
file_path = 'app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

old_width = "val bitmapWidth = if (isWavAnalyzerMode && history.isNotEmpty()) history.size else historySize"
new_width = "val bitmapWidth = if ((isWavAnalyzerMode || isReportModeActive) && history.isNotEmpty()) history.size else historySize"

content = content.replace(old_width, new_width)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("bitmapWidth fixed")
