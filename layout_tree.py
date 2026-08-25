import sys
with open('report_mode_screen_copy.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "Row(" in line or "Box(" in line or "Column(" in line or "Modifier.weight" in line:
        print(f"{i+1}: {line.strip()}")
