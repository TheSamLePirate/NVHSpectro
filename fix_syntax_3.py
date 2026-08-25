import sys
with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
'''        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            SpectrogramCanvas(
        SpectrogramCanvas(''',
'''        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            SpectrogramCanvas('''
)

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print("Syntax fixed")
