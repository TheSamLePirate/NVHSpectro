import re

with open('app/src/main/java/com/example/nvhspectro/MainScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Change weight of Zone 1
content = re.sub(
    r'\.weight\(0\.55f\)\s*\n\s*\.background\(Color\.Black\),',
    r'.weight(if (isReportModeActive) 1f else 0.55f)\n                    .background(Color.Black),',
    content
)

# Insert ManualReportControlsPanel inside the Box of Zone 1
# Zone 1 ends around line 680, let's find the exact place to insert.
# It ends with:
# } else if (fftHistory.isEmpty()) {
#     Text("Analyse audio & sonogramme en cours...", color = Color.White)
# }
# }
insertion_point = r'\} else if \(fftHistory\.isEmpty\(\)\) \{\s*Text\("Analyse audio & sonogramme en cours\.\.\.", color = Color\.White\)\s*\}\s*\}'

replacement_insertion = '''} else if (fftHistory.isEmpty()) {
                    Text("Analyse audio & sonogramme en cours...", color = Color.White)
                }
                
                if (isReportModeActive) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        ManualReportControlsPanel(viewModel = viewModel)
                    }
                }
            }'''

content = re.sub(insertion_point, replacement_insertion, content)

# Wrap Zone 2 in if (!isReportModeActive)
# Zone 2 starts with:
# // Zone 2: Données Véhicule / Télémétrie OU Lecteur Vidéo (Mode Vidéo)
# if (isVideoMode) {
# And ends with the end of the Column that wraps Zone 1 and Zone 2.
# Let's find the start of Zone 2 and replace it.
zone2_start = r'// Zone 2: Donn\w+es V\w+hicule / T\w+l\w+m\w+trie OU Lecteur Vid\w+o \(Mode Vid\w+o\)'
replacement_zone2_start = r'if (!isReportModeActive) {\n            // Zone 2: Donn?es V?hicule / T?l?m?trie OU Lecteur Vid?o (Mode Vid?o)'

# We need to find where to put the closing brace for if (!isReportModeActive).
# Zone 2 is inside the main Column which ends before // Dialogues (Paramètres, Kinematics, etc) or if (showOrderSelectionDialog)
# Let's just do a textual replacement using string methods instead of regex for safety if possible.

