import sys
with open('app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
'''                        val plotHeight = h - marginTop - marginBottom
                        
                        val touchX = offset.x''',
'''                        val plotHeight = h - marginTop - marginBottom
                        if (w <= 0 || h <= 0 || plotWidth <= 0 || plotHeight <= 0) return@detectTapGestures
                        
                        val touchX = offset.x'''
)

content = content.replace(
'''                        val plotHeight = h - marginTop - marginBottom
                        
                        val srcWidth = bitmapWidth / newZoom''',
'''                        val plotHeight = h - marginTop - marginBottom
                        if (w <= 0 || h <= 0 || plotWidth <= 0 || plotHeight <= 0) return@detectTransformGestures
                        
                        val srcWidth = bitmapWidth / newZoom'''
)

with open('app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print("pointerInput checks added")
