import os

file_path = r'app\src\main\java\com\example\nvhspectro\MainScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    code = f.read()

# Pass activeFilters to SpectrogramCanvas
old_call = '''                    telemetryHistory = telemetryHistory
                )
            }'''
new_call = '''                    telemetryHistory = telemetryHistory,
                    activeFilters = activeFilters
                )
            }'''

code = code.replace(old_call, new_call)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(code)

print("Passed activeFilters to SpectrogramCanvas")
