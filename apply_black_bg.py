import os

# 1. MainViewModel.kt
vm_path = r'app\src\main\java\com\example\nvhspectro\MainViewModel.kt'
with open(vm_path, 'r', encoding='utf-8') as f:
    vm = f.read()

vm = vm.replace('val minVal = if (mode == DisplayMode.TTNR) -3.0 else _minDb.value', 'val minVal = if (mode == DisplayMode.TTNR) 1.0 else _minDb.value')

with open(vm_path, 'w', encoding='utf-8') as f:
    f.write(vm)

# 2. SpectrogramColormap.kt
color_path = r'app\src\main\java\com\example\nvhspectro\SpectrogramColormap.kt'
with open(color_path, 'r', encoding='utf-8') as f:
    color = f.read()

color = color.replace('val effectiveMin = if (displayMode == DisplayMode.TTNR) -3.0 else minDb', 'val effectiveMin = if (displayMode == DisplayMode.TTNR) 1.0 else minDb')
color = color.replace('if (displayMode == DisplayMode.TTNR && magnitude < -3.0)', 'if (displayMode == DisplayMode.TTNR && magnitude < 1.0)')

with open(color_path, 'w', encoding='utf-8') as f:
    f.write(color)

# 3. Version bump
info_path = r'app\src\main\java\com\example\nvhspectro\ui\InfoDialog.kt'
with open(info_path, 'r', encoding='utf-8') as f:
    code = f.read()
code = code.replace('v12.1.3', 'v12.1.4')
with open(info_path, 'w', encoding='utf-8') as f:
    f.write(code)

gradle_path = r'app\build.gradle.kts'
with open(gradle_path, 'r', encoding='utf-8') as f:
    code = f.read()
code = code.replace('versionName = "12.1.3"', 'versionName = "12.1.4"')
with open(gradle_path, 'w', encoding='utf-8') as f:
    f.write(code)

print("Applied black background +1.0 dB")
