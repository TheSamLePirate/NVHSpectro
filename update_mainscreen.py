import re

with open('app/src/main/java/com/example/nvhspectro/MainScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace Scaffold( with if (isReportModeActive) ... else { Scaffold(
scaffold_match = r'Scaffold\('
scaffold_replacement = '''if (isReportModeActive) {
        com.example.nvhspectro.ui.ReportModeScreen(viewModel = viewModel)
    } else {
        Scaffold('''

content = re.sub(scaffold_match, scaffold_replacement, content, count=1)

# Now we need to close the } else { at the end of AppScreen.
# AppScreen ends with something like:
#         if (showSaveRecordingDialog) {
#             ...
#         }
#     }
# }
# Let's find un ManualReportControlsPanel and delete it entirely.
manual_panel_start = content.find('@Composable\nfun ManualReportControlsPanel')
if manual_panel_start != -1:
    content = content[:manual_panel_start]
    # We need to add the closing brace for else { before the end of AppScreen.
    # The end of AppScreen is right before ManualReportControlsPanel
    # Let's find the last } before ManualReportControlsPanel
    last_brace = content.rfind('}')
    if last_brace != -1:
        # We need to insert } right after the Scaffold's end. Wait, AppScreen has a closing brace.
        # Actually, let's just replace the very last part of AppScreen.
        appscreen_end_pattern = r'        if \(showSaveRecordingDialog\)(.*?)\n    }\n\}'
        
        # We will use regex to find the end of AppScreen and append     }\n to close the else block.
        match = re.search(r'        if \(showSaveRecordingDialog\).*?\n    }\n', content, re.DOTALL)
        if match:
            end_pos = match.end()
            content = content[:end_pos] + "    }\n" + content[end_pos:]
        
with open('app/src/main/java/com/example/nvhspectro/MainScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("MainScreen updated.")
