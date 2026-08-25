import sys

# Fix MainViewModel.kt
with open('app/src/main/java/com/example/nvhspectro/MainViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('_reportFftHistory.value = _fftHistory.value.toList()', '_reportFftHistory.value = fftHistory.value.toList()')

with open('app/src/main/java/com/example/nvhspectro/MainViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)

# Fix ReportModeScreen.kt
with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'r', encoding='utf-8') as f:
    content2 = f.read()

content2 = content2.replace('viewModel.addManualPoint(frame, bin)', 'viewModel.addManualTrackPoint(frame, bin)')

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content2)

print("Fixes applied.")
