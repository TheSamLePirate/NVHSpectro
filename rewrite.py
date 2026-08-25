import re

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# I will find the various blocks by using regex or just rewrite it from a known state.
# Wait, let's just grab the whole ReportModeScreen function and parse it.
