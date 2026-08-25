import sys
with open('app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    "if (w <= 0 || h <= 0 || plotWidth <= 0 || plotHeight <= 0) return@Canvas",
    "if (w <= 0 || h <= 0 || plotWidth <= 50f || plotHeight <= 50f) return@Canvas"
)
content = content.replace(
    "if (w <= 0 || h <= 0 || plotWidth <= 0 || plotHeight <= 0) return@detectTapGestures",
    "if (w <= 0 || h <= 0 || plotWidth <= 50f || plotHeight <= 50f) return@detectTapGestures"
)
content = content.replace(
    "if (w <= 0 || h <= 0 || plotWidth <= 0 || plotHeight <= 0) return@detectTransformGestures",
    "if (w <= 0 || h <= 0 || plotWidth <= 50f || plotHeight <= 50f) return@detectTransformGestures"
)

with open('app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print("Canvas check strengthened")
