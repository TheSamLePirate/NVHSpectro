import sys

with open('app/src/main/java/com/example/nvhspectro/MainScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix weight
content = content.replace(
    '.weight(0.55f)\n                    .background(Color.Black),',
    '.weight(if (isReportModeActive) 1f else 0.55f)\n                    .background(Color.Black),'
)

# Insert ManualReportControlsPanel at the end of Zone 1
zone1_end = '''} else if (fftHistory.isEmpty()) {
                    Text("Analyse audio & sonogramme en cours...", color = Color.White)
                }
            }'''

new_zone1_end = '''} else if (fftHistory.isEmpty()) {
                    Text("Analyse audio & sonogramme en cours...", color = Color.White)
                }
                
                if (isReportModeActive) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        ManualReportControlsPanel(viewModel = viewModel)
                    }
                }
            }'''

content = content.replace(zone1_end, new_zone1_end)

# Enclose Zone 2 and WavPlayerBar inside if (!isReportModeActive)
# Let's find "Lecteur WAV" up to the end of the Column
start_marker = '            // Lecteur WAV (si un fichier est charg'
end_marker = '        // Dialogues (Param'

start_idx = content.find(start_marker)
end_idx = content.find(end_marker)

if start_idx != -1 and end_idx != -1:
    before = content[:start_idx]
    after = content[end_idx:]
    middle = content[start_idx:end_idx]
    
    # Let's indent middle by 4 spaces and wrap in if (!isReportModeActive) { ... }
    lines = middle.split('\n')
    indented = '\n'.join(['    ' + line if line.strip() else line for line in lines])
    new_middle = '            if (!isReportModeActive) {\n' + indented + '            }\n\n    '
    
    content = before + new_middle + after
else:
    print("Could not find markers for Zone 2!")

with open('app/src/main/java/com/example/nvhspectro/MainScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("MainScreen layout updated successfully.")
