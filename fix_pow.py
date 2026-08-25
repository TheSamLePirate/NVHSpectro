import os

file_path = r'app\src\main\java\com\example\nvhspectro\FFTProcessor.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    code = f.read()

code = code.replace('kotlin.math.pow', 'Math.pow')
code = code.replace('10.0.pow(', 'Math.pow(10.0, ')
code = code.replace('.pow(2)', ', 2.0)')
code = code.replace('.pow(0.69)', ', 0.69)')

# Wait, let's fix the specific lines
# line 98: val cb = 25.0 + 75.0 * Math.pow(1.0 + 1.4 * Math.pow(f / 1000.0, 2.0), 0.69)
# line 71: Math.pow(10.0, magnitudesDbFS[i] / 10.0)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(code)

print("Fixed pow")
