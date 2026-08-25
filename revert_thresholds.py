import os

vm_path = r'app\src\main\java\com\example\nvhspectro\MainViewModel.kt'
with open(vm_path, 'r', encoding='utf-8') as f:
    vm_code = f.read()

vm_code = vm_code.replace(
    'val minVal = if (mode == DisplayMode.TTNR) 1.5 else _minDb.value',
    'val minVal = if (mode == DisplayMode.TTNR) 0.0 else _minDb.value'
)

with open(vm_path, 'w', encoding='utf-8') as f:
    f.write(vm_code)


color_path = r'app\src\main\java\com\example\nvhspectro\SpectrogramColormap.kt'
with open(color_path, 'r', encoding='utf-8') as f:
    color_code = f.read()

color_code = color_code.replace(
    'val effectiveMin = if (displayMode == DisplayMode.TTNR) 1.5 else minDb',
    'val effectiveMin = if (displayMode == DisplayMode.TTNR) 0.0 else minDb'
)
color_code = color_code.replace(
    'if (displayMode == DisplayMode.TTNR && magnitude < 1.5)',
    'if (displayMode == DisplayMode.TTNR && magnitude < 0.0)'
)

with open(color_path, 'w', encoding='utf-8') as f:
    f.write(color_code)

print("Reverted thresholds to 0.0")
