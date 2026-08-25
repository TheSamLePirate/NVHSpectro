import sys
with open('app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
'''      ) {
        val w = size.width
        val h = size.height

        val marginLeft = 150f
        val marginTop = 60f
        val marginBottom = 120f
        val marginRight = 40f
        
        val plotWidth = w - marginLeft - marginRight
        val plotHeight = h - marginTop - marginBottom''',
'''      ) {
        val w = size.width
        val h = size.height

        val marginLeft = 150f
        val marginTop = 60f
        val marginBottom = 120f
        val marginRight = 40f
        
        val plotWidth = w - marginLeft - marginRight
        val plotHeight = h - marginTop - marginBottom
        
        if (w <= 0 || h <= 0 || plotWidth <= 0 || plotHeight <= 0) return@Canvas'''
)

with open('app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print("Canvas check added")
