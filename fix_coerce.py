import sys
with open('app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    '''val textX = (x - labelWidth / 2f).coerceIn(marginLeft, plotRight - labelWidth)''',
    '''val textX = (x - labelWidth / 2f).coerceIn(marginLeft, maxOf(marginLeft, plotRight - labelWidth))'''
)

with open('app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print("coerceIn fixed")
