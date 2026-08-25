import os

file_path = r'app\src\main\java\com\example\nvhspectro\SpectrogramColormap.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    code = f.read()

# Update effectiveMin
code = code.replace(
    'val effectiveMin = if (displayMode == DisplayMode.TTNR) 0.0 else minDb',
    'val effectiveMin = if (displayMode == DisplayMode.TTNR) 1.5 else minDb'
)

# Update magnitude < 0.8 to magnitude < 1.5
code = code.replace(
    'if (displayMode == DisplayMode.TTNR && magnitude < 0.8)',
    'if (displayMode == DisplayMode.TTNR && magnitude < 1.5)'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(code)

print("Updated SpectrogramColormap.kt")
