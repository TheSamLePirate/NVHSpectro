import os

info_path = r'app\src\main\java\com\example\nvhspectro\ui\InfoDialog.kt'
with open(info_path, 'r', encoding='utf-8') as f:
    code = f.read()
code = code.replace('v12.1.0', 'v12.1.1')
with open(info_path, 'w', encoding='utf-8') as f:
    f.write(code)

gradle_path = r'app\build.gradle.kts'
with open(gradle_path, 'r', encoding='utf-8') as f:
    code = f.read()
code = code.replace('versionName = "12.1.0"', 'versionName = "12.1.1"')
with open(gradle_path, 'w', encoding='utf-8') as f:
    f.write(code)
