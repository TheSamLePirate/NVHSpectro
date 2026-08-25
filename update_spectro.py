import re

with open('app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add imports
if 'import com.example.nvhspectro.data.ManualOrderAnchor' not in content:
    content = content.replace('import com.example.nvhspectro.data.KinematicsConfig',
                              'import com.example.nvhspectro.data.KinematicsConfig\nimport com.example.nvhspectro.data.ManualOrderAnchor\nimport com.example.nvhspectro.data.SmartTrackedOrder')

# 2. Add parameters to SpectrogramCanvas
old_params = '''    telemetryHistory: List<TelemetryData> = emptyList(),
    activeFilters: List<AudioFilter> = emptyList()'''
new_params = '''    telemetryHistory: List<TelemetryData> = emptyList(),
    activeFilters: List<AudioFilter> = emptyList(),
    isReportModeActive: Boolean = false,
    currentUserPoints: List<ManualOrderAnchor> = emptyList(),
    currentSmartPath: List<ManualOrderAnchor> = emptyList(),
    manualTrackedOrders: List<SmartTrackedOrder> = emptyList(),
    onAddManualPoint: (Int, Int) -> Unit = { _, _ -> }'''
content = content.replace(old_params, new_params)

# 3. Add zoom/pan state inside the composable
state_injection = '''    var cursorYRatio by remember { mutableFloatStateOf(0.5f) }'''
new_state = '''    var cursorYRatio by remember { mutableFloatStateOf(0.5f) }
    
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    
    LaunchedEffect(isReportModeActive) {
        if (!isReportModeActive) {
            zoom = 1f
            pan = Offset.Zero
        }
    }'''
content = content.replace(state_injection, new_state)

# 4. Modify the Canvas call (around line ~220) to include pointer inputs
# First, let's find the Canvas block
canvas_search = r"Canvas\(modifier = modifier\)\s*\{"
canvas_replace = '''Canvas(
        modifier = modifier.then(
            if (isReportModeActive) {
                Modifier.pointerInput(Unit) {
                    androidx.compose.foundation.gestures.detectTransformGestures { _, panChange, zoomChange, _ ->
                        zoom = (zoom * zoomChange).coerceIn(1f, 10f)
                        pan += panChange
                    }
                }.pointerInput(Unit) {
                    detectTapGestures { offset ->
                        // Calculate canvas center
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        // Inverse transform
                        val logicalX = (offset.x - pan.x - cx) / zoom + cx
                        val logicalY = (offset.y - pan.y - cy) / zoom + cy
                        
                        val frameIndex = ((logicalX / size.width) * (bitmapWidth - 1)).toInt().coerceIn(0, bitmapWidth - 1)
                        val binIndex = ((maxBin - 1) - (logicalY / size.height) * (displayedBinCount - 1)).toInt()
                        
                        onAddManualPoint(frameIndex, binIndex)
                    }
                }.androidx.compose.ui.graphics.graphicsLayer(
                    scaleX = zoom,
                    scaleY = zoom,
                    translationX = pan.x,
                    translationY = pan.y
                )
            } else {
                Modifier
            }
        )
    ) {'''
content = re.sub(canvas_search, canvas_replace, content)

# 5. Add rendering logic for the manual tracks inside the Canvas onDraw
# Find the end of the drawing block. We will inject it right after:
# "if (showH1Overlay && kinematicsConfig.isEnabled && ...)" block or before the end of the Canvas block.
overlay_injection = '''
        // --- MANUAL SMART TRACKING OVERLAYS ---
        if (isReportModeActive) {
            // 1. Draw Validated Orders
            manualTrackedOrders.forEach { order ->
                val path = androidx.compose.ui.graphics.Path()
                var isFirst = true
                order.path.forEach { anchor ->
                    val x = (anchor.frameIndex.toFloat() / (bitmapWidth - 1).coerceAtLeast(1)) * size.width
                    val y = ((maxBin - 1 - anchor.binIndex).toFloat() / (displayedBinCount - 1).coerceAtLeast(1)) * size.height
                    if (isFirst) {
                        path.moveTo(x, y)
                        isFirst = false
                    } else {
                        path.lineTo(x, y)
                    }
                }
                drawPath(
                    path = path,
                    color = order.color.copy(alpha = 0.8f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 4f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.cornerPathEffect(10f)
                    )
                )
            }

            // 2. Draw Current Smart Path
            if (currentSmartPath.isNotEmpty()) {
                val path = androidx.compose.ui.graphics.Path()
                var isFirst = true
                currentSmartPath.forEach { anchor ->
                    val x = (anchor.frameIndex.toFloat() / (bitmapWidth - 1).coerceAtLeast(1)) * size.width
                    val y = ((maxBin - 1 - anchor.binIndex).toFloat() / (displayedBinCount - 1).coerceAtLeast(1)) * size.height
                    if (isFirst) {
                        path.moveTo(x, y)
                        isFirst = false
                    } else {
                        path.lineTo(x, y)
                    }
                }
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.9f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 3f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                )
            }

            // 3. Draw Current User Anchor Points
            currentUserPoints.forEach { anchor ->
                val x = (anchor.frameIndex.toFloat() / (bitmapWidth - 1).coerceAtLeast(1)) * size.width
                val y = ((maxBin - 1 - anchor.binIndex).toFloat() / (displayedBinCount - 1).coerceAtLeast(1)) * size.height
                drawCircle(
                    color = Color.Red,
                    radius = 6f,
                    center = Offset(x, y)
                )
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = Offset(x, y)
                )
            }
        }
'''

# We will inject the overlays right before the final closing brace of the SpectrogramCanvas composable.
# Or rather, right before the closing brace of the Canvas{ } block.
# Finding the end of the Canvas block is tricky with regex. Let's append it to the end of the onDraw block.
# Let's find "if (showH1Overlay && kinematicsConfig.isEnabled" and inject before it.
h1_marker = "if (showH1Overlay && kinematicsConfig.isEnabled"
content = content.replace(h1_marker, overlay_injection + "\n        " + h1_marker)


with open('app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("SpectrogramColormap updated.")
