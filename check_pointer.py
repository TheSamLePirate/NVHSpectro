import sys
with open('app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if '.pointerInput' in line:
        print(f"Line {i}: {line.strip()}")
