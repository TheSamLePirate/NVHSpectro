import sys
file_path = 'app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

old_launched_effect = "LaunchedEffect(history, effectiveMin, effectiveMax, displayMode, isWavAnalyzerMode) {"
new_launched_effect = "LaunchedEffect(history, effectiveMin, effectiveMax, displayMode, isWavAnalyzerMode, isReportModeActive) {"

old_if = "if (isWavAnalyzerMode) {"
new_if = "if (isWavAnalyzerMode || isReportModeActive) {"

content = content.replace(old_launched_effect, new_launched_effect)
content = content.replace("if (isWavAnalyzerMode) {", "if (isWavAnalyzerMode || isReportModeActive) {", 1) # Only first one inside LaunchedEffect

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Bitmap drawing fixed")
