import os

file_path = r'app\src\main\java\com\example\nvhspectro\MainViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    code = f.read()

code = code.replace(
    'val minVal = if (mode == DisplayMode.TTNR) 1.0 else _minDb.value',
    'val minVal = if (mode == DisplayMode.TTNR) 1.5 else _minDb.value'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(code)

print("Updated MainViewModel.kt to 1.5")
