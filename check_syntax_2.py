import sys
with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines[40:60], start=41):
    print(f"Line {i}: {line.rstrip()}")
