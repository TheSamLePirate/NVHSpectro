import re

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('viewModel.addManualPoint', 'viewModel.addManualTrackPoint')

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text)
