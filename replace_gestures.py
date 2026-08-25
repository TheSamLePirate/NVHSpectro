import sys

file_path = 'app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

old_block = """            .pointerInput(Unit) {
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
            }"""

new_block = """            .pointerInput(isReportModeActive, isDrawingMode, zoom, pan, bitmapWidth, bitmapHeight) {
                if (isReportModeActive && isDrawingMode) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val marginLeft = 150f
                        val marginTop = 60f
                        val marginBottom = 120f
                        val marginRight = 40f
                        val plotWidth = w - marginLeft - marginRight
                        val plotHeight = h - marginTop - marginBottom
                        
                        val touchX = offset.x
                        val touchY = offset.y
                        if (touchX in marginLeft..(w-marginRight) && touchY in marginTop..(h-marginBottom)) {
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
                        }
                    }
                } else {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        val newZoom = (zoom * zoomChange).coerceIn(1f, 10f)
                        
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val marginLeft = 150f
                        val marginTop = 60f
                        val marginBottom = 120f
                        val marginRight = 40f
                        val plotWidth = w - marginLeft - marginRight
                        val plotHeight = h - marginTop - marginBottom
                        
                        val srcWidth = bitmapWidth / newZoom
                        val srcHeight = bitmapHeight / newZoom
                        val maxPanX = bitmapWidth - srcWidth
                        val maxPanY = bitmapHeight - srcHeight
                        
                        val maxPanXOffset = -(maxPanX / bitmapWidth.toFloat()) * plotWidth
                        val maxPanYOffset = -(maxPanY / bitmapHeight.toFloat()) * plotHeight
                        
                        val newPanX = if (maxPanXOffset < 0f) (pan.x + panChange.x).coerceIn(maxPanXOffset, 0f) else 0f
                        val newPanY = if (maxPanYOffset < 0f) (pan.y + panChange.y).coerceIn(maxPanYOffset, 0f) else 0f
                        
                        zoom = newZoom
                        pan = androidx.compose.ui.geometry.Offset(newPanX, newPanY)
                    }
                }
            }
            .pointerInput(isReportModeActive) {
                detectDragGestures { change, _ ->
                    if (!isReportModeActive) {
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
            }"""

content = content.replace(old_block, new_block)
with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Gestures replaced successfully")
