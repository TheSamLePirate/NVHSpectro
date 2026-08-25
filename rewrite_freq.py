import re

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('minFreq = minFreq.toFloat(),', 'minFreq = minFreq,')
text = text.replace('maxFreq = maxFreq.toFloat(),', 'maxFreq = maxFreq,')

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text)
