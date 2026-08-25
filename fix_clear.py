import os

viewmodel_path = r'app\src\main\java\com\example\nvhspectro\MainViewModel.kt'
with open(viewmodel_path, 'r', encoding='utf-8') as f:
    vm_code = f.read()

vm_code = vm_code.replace(
'''    fun clearEmergenceReport() {
        ttnrPersistenceCount = IntArray(0)
        recentRawTTNRBuffer.clear()
        _emergenceReportEntries.value = emptyList()''',
'''    fun clearEmergenceReport() {
        recentPowerBuffer.clear()
        _emergenceReportEntries.value = emptyList()''')

with open(viewmodel_path, 'w', encoding='utf-8') as f:
    f.write(vm_code)

print("Fixed MainViewModel.kt")
