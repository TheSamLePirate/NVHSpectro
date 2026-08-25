import os

viewmodel_path = r'app\src\main\java\com\example\nvhspectro\MainViewModel.kt'
with open(viewmodel_path, 'r', encoding='utf-8') as f:
    vm_code = f.read()

vm_code = vm_code.replace(
    'val minVal = if (mode == DisplayMode.TTNR) -3.0 else _minDb.value',
    'val minVal = if (mode == DisplayMode.TTNR) 1.0 else _minDb.value'
)

with open(viewmodel_path, 'w', encoding='utf-8') as f:
    f.write(vm_code)

print("Updated MainViewModel.kt threshold")
