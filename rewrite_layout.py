import sys

file_path = 'app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_content = []
in_box = False
box_started = False
for line in lines:
    new_content.append(line)

