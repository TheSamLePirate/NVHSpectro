import re

with open('app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# We need to change the detectTapGestures to correctly reverse the graphicsLayer transform.
# Also change detectTransformGestures to NOT limit pan based on srcWidth but instead limit it based on graphicsLayer bounds.
# And change the Canvas drawing to draw the full bitmap and apply graphicsLayer.

# Replace the whole Canvas(...) { ... } block logic.
# Wait, let's use a simpler string replacement for just the relevant parts.

# 1. Update the modifier of the Canvas to use graphicsLayer
old_canvas_modifier = '''    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isReportModeActive, isDrawingMode, zoom, pan, bitmapWidth, bitmapHeight) {'''

new_canvas_modifier = '''    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                if (isReportModeActive) {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                }
            }
            .pointerInput(isReportModeActive, isDrawingMode, zoom, pan, bitmapWidth, bitmapHeight) {'''

text = text.replace(old_canvas_modifier, new_canvas_modifier)

# 2. Update detectTapGestures
old_tap = '''                        if (touchX in marginLeft..(w-marginRight) && touchY in marginTop..(h-marginBottom)) {
                            val x = touchX - marginLeft
                            val y = touchY - marginTop
                            
                            val srcWidth = (bitmapWidth / zoom).coerceAtLeast(1f)
                            val srcHeight = (bitmapHeight / zoom).coerceAtLeast(1f)
                            val maxPanX = (bitmapWidth - srcWidth).coerceAtLeast(0f)
                            val maxPanY = (bitmapHeight - srcHeight).coerceAtLeast(0f)
                            
                            val srcX = if (plotWidth > 0) (-pan.x / plotWidth * bitmapWidth).coerceIn(0f, maxPanX) else 0f
                            val srcY = if (plotHeight > 0) (-pan.y / plotHeight * bitmapHeight).coerceIn(0f, maxPanY) else 0f
                            
                            val bitmapX = srcX + if (plotWidth > 0) (x / plotWidth) * srcWidth else 0f
                            val bitmapY = srcY + if (plotHeight > 0) (y / plotHeight) * srcHeight else 0f
                            
                            val numFrames = history.size
                            val frameIndex = if (bitmapWidth > 0) ((bitmapX / bitmapWidth) * (numFrames - 1)).toInt().coerceIn(0, numFrames - 1) else 0
                            
                            val displayedBinCount = (maxBin - minBin).coerceAtLeast(1)
                            val binIndex = (maxBin - 1) - if (bitmapHeight > 0) ((bitmapY / bitmapHeight) * (displayedBinCount - 1)).toInt() else 0
                            
                            onAddManualPoint(frameIndex, binIndex.coerceIn(minBin, maxBin - 1))
                        }'''

new_tap = '''                        // Remove pan and reverse scale to find the original coordinate BEFORE transform
                        val inverseTouchX = (touchX - pan.x - w / 2f) / zoom + w / 2f
                        val inverseTouchY = (touchY - pan.y - h / 2f) / zoom + h / 2f
                        
                        if (inverseTouchX in marginLeft..(w-marginRight) && inverseTouchY in marginTop..(h-marginBottom)) {
                            val x = inverseTouchX - marginLeft
                            val y = inverseTouchY - marginTop
                            
                            val bitmapX = if (plotWidth > 0) (x / plotWidth) * bitmapWidth else 0f
                            val bitmapY = if (plotHeight > 0) (y / plotHeight) * bitmapHeight else 0f
                            
                            val numFrames = history.size
                            val frameIndex = if (bitmapWidth > 0) ((bitmapX / bitmapWidth) * (numFrames - 1)).toInt().coerceIn(0, numFrames - 1) else 0
                            
                            val displayedBinCount = (maxBin - minBin).coerceAtLeast(1)
                            val binIndex = (maxBin - 1) - if (bitmapHeight > 0) ((bitmapY / bitmapHeight) * (displayedBinCount - 1)).toInt() else 0
                            
                            onAddManualPoint(frameIndex, binIndex.coerceIn(minBin, maxBin - 1))
                        }'''

text = text.replace(old_tap, new_tap)


# 3. Update detectTransformGestures
old_transform = '''                        val srcWidth = bitmapWidth / newZoom
                        val srcHeight = bitmapHeight / newZoom
                        val maxPanX = bitmapWidth - srcWidth
                        val maxPanY = bitmapHeight - srcHeight
                        
                        val maxPanXOffset = -(maxPanX / bitmapWidth.toFloat()) * plotWidth
                        val maxPanYOffset = -(maxPanY / bitmapHeight.toFloat()) * plotHeight
                        
                        val newPanX = if (maxPanXOffset < 0f) (pan.x + panChange.x).coerceIn(maxPanXOffset, 0f) else 0f
                        val newPanY = if (maxPanYOffset < 0f) (pan.y + panChange.y).coerceIn(maxPanYOffset, 0f) else 0f
                        
                        zoom = newZoom
                        pan = androidx.compose.ui.geometry.Offset(newPanX, newPanY)'''

new_transform = '''                        // graphicsLayer centers scale around the middle of the layout.
                        // We will allow arbitrary panning and just track it.
                        // We clamp zoom to [1f, 10f]
                        
                        val newPanX = pan.x + panChange.x
                        val newPanY = pan.y + panChange.y
                        
                        // simple limit to prevent panning too far out of bounds (approximate)
                        val limitX = (w * newZoom) / 2f
                        val limitY = (h * newZoom) / 2f
                        
                        zoom = newZoom
                        pan = androidx.compose.ui.geometry.Offset(
                            newPanX.coerceIn(-limitX, limitX), 
                            newPanY.coerceIn(-limitY, limitY)
                        )'''

text = text.replace(old_transform, new_transform)

# 4. Update the actual drawing part (remove the srcX/srcY calculation that scales the image crop, instead draw the whole image and let graphicsLayer scale it)
old_draw = '''        val srcWidth = (bitmapWidth / zoom).toInt().coerceAtLeast(1)
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

new_draw = '''        // Draw the full bitmap; graphicsLayer will take care of zooming and panning automatically!
        drawImage(
            image = imageBitmap,
            srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
            srcSize = androidx.compose.ui.unit.IntSize(bitmapWidth, bitmapHeight),
            dstOffset = androidx.compose.ui.unit.IntOffset(marginLeft.toInt(), marginTop.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(plotWidth.toInt(), plotHeight.toInt()),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.None
        )'''

text = text.replace(old_draw, new_draw)

with open('app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt', 'w', encoding='utf-8') as f:
    f.write(text)

