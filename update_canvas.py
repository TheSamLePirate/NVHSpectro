import sys

with open('app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add imports for detectTransformGestures and graphicsLayer
import_gestures = "import androidx.compose.foundation.gestures.detectTransformGestures"
if import_gestures not in content:
    content = content.replace("import androidx.compose.foundation.gestures.detectTapGestures",
                              "import androidx.compose.foundation.gestures.detectTapGestures\nimport androidx.compose.foundation.gestures.detectTransformGestures\nimport androidx.compose.ui.graphics.graphicsLayer")

# Add isDrawingMode to the parameter list of SpectrogramCanvas
params_old = '''    activeFilters: List<AudioFilter> = emptyList(),
    isReportModeActive: Boolean = false,
    currentUserPoints: List<ManualOrderAnchor> = emptyList(),
    currentSmartPath: List<ManualOrderAnchor> = emptyList(),
    manualTrackedOrders: List<SmartTrackedOrder> = emptyList(),
    onAddManualPoint: (Int, Int) -> Unit = { _, _ -> }'''

params_new = '''    activeFilters: List<AudioFilter> = emptyList(),
    isReportModeActive: Boolean = false,
    isDrawingMode: Boolean = false,
    currentUserPoints: List<ManualOrderAnchor> = emptyList(),
    currentSmartPath: List<ManualOrderAnchor> = emptyList(),
    manualTrackedOrders: List<SmartTrackedOrder> = emptyList(),
    onAddManualPoint: (Int, Int) -> Unit = { _, _ -> }'''

content = content.replace(params_old, params_new)

# Locate the Canvas block
canvas_start = content.find('Canvas(')
if canvas_start != -1:
    canvas_modifier_old = '''        Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val marginTop = 60f
                    val marginBottom = 120f
                    val plotHeight = size.height - marginTop - marginBottom
                    if (plotHeight > 0) {
                        val touchY = change.position.y
                        val relativeY = (touchY - marginTop).coerceIn(0f, plotHeight)
                        cursorYRatio = relativeY / plotHeight
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val marginTop = 60f
                    val marginBottom = 120f
                    val plotHeight = size.height - marginTop - marginBottom
                    if (plotHeight > 0) {
                        val relativeY = (offset.y - marginTop).coerceIn(0f, plotHeight)
                        cursorYRatio = relativeY / plotHeight
                    }
                }
            }
    ) {'''

    canvas_modifier_new = '''        Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isDrawingMode) {
                if (isDrawingMode) {
                    // Mode Dessin : Clic pour ajouter un point
                    detectTapGestures { offset ->
                        val marginTop = 60f
                        val marginBottom = 120f
                        val marginLeft = 150f
                        val marginRight = 40f
                        
                        val plotWidth = size.width - marginLeft - marginRight
                        val plotHeight = size.height - marginTop - marginBottom
                        
                        if (plotWidth > 0 && plotHeight > 0) {
                            val tapX = offset.x - marginLeft
                            val tapY = offset.y - marginTop
                            
                            val srcWidth = (bitmapWidth / zoom)
                            val srcHeight = (bitmapHeight / zoom)
                            val maxPanX = bitmapWidth - srcWidth
                            val maxPanY = bitmapHeight - srcHeight
                            val srcX = (-pan.x / plotWidth * bitmapWidth).coerceIn(0f, maxPanX)
                            val srcY = (-pan.y / plotHeight * bitmapHeight).coerceIn(0f, maxPanY)
                            
                            val bitmapX = srcX + (tapX / plotWidth) * srcWidth
                            val bitmapY = srcY + (tapY / plotHeight) * srcHeight
                            
                            val frameIdx = bitmapX.toInt().coerceIn(0, bitmapWidth - 1)
                            val binIdx = (bitmapHeight - 1 - bitmapY).toInt().coerceIn(0, bitmapHeight - 1)
                            val actualBinIndex = minBin + binIdx
                            
                            onAddManualPoint(frameIdx, actualBinIndex)
                            
                            // Met aussi a jour le curseur discret
                            val relativeY = tapY.coerceIn(0f, plotHeight)
                            cursorYRatio = relativeY / plotHeight
                        }
                    }
                } else {
                    // Mode Navigation : Pan & Zoom
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        zoom = (zoom * zoomChange).coerceIn(1f, 10f)
                        val maxPanX = size.width * (zoom - 1f)
                        val maxPanY = size.height * (zoom - 1f)
                        pan = androidx.compose.ui.geometry.Offset(
                            (pan.x + panChange.x * zoom).coerceIn(-maxPanX, 0f),
                            (pan.y + panChange.y * zoom).coerceIn(-maxPanY, 0f)
                        )
                    }
                }
            }
    ) {'''
    content = content.replace(canvas_modifier_old, canvas_modifier_new)

# Modify drawImage to apply pan & zoom
draw_image_old = '''        drawImage(
            image = imageBitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmapWidth, bitmapHeight),
            dstOffset = IntOffset(marginLeft.toInt(), marginTop.toInt()),
            dstSize = IntSize(plotWidth.toInt(), plotHeight.toInt()),
            filterQuality = FilterQuality.None
        )'''

draw_image_new = '''        val srcWidth = (bitmapWidth / zoom).toInt().coerceAtLeast(1)
        val srcHeight = (bitmapHeight / zoom).toInt().coerceAtLeast(1)
        
        val maxPanX = bitmapWidth - srcWidth
        val maxPanY = bitmapHeight - srcHeight
        
        val srcX = (-pan.x / plotWidth * bitmapWidth).toInt().coerceIn(0, maxPanX)
        val srcY = (-pan.y / plotHeight * bitmapHeight).toInt().coerceIn(0, maxPanY)

        drawImage(
            image = imageBitmap,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(srcWidth, srcHeight),
            dstOffset = IntOffset(marginLeft.toInt(), marginTop.toInt()),
            dstSize = IntSize(plotWidth.toInt(), plotHeight.toInt()),
            filterQuality = FilterQuality.None
        )'''

content = content.replace(draw_image_old, draw_image_new)

with open('app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("SpectrogramColormap updated.")
