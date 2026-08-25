import re

with open('app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Fix 1: pointerInput math
old_pointer_math = '''                        val touchX = offset.x
                        val touchY = offset.y
                        // Remove pan and reverse scale to find the original coordinate BEFORE transform
                        val inverseTouchX = (touchX - pan.x - w / 2f) / zoom + w / 2f
                        val inverseTouchY = (touchY - pan.y - h / 2f) / zoom + h / 2f'''
                        
new_pointer_math = '''                        val touchX = offset.x
                        val touchY = offset.y
                        // pointerInput is after graphicsLayer, so coordinates are already in local space!
                        val inverseTouchX = touchX
                        val inverseTouchY = touchY'''

text = text.replace(old_pointer_math, new_pointer_math)

# Fix 2: Disable horizontal cursor in Report Mode
old_cursor = '''            // --- CURSEUR EN FRÉQUENCE DISCRET ---
            val cursorY = marginTop + cursorYRatio * plotHeight
            val selectedFreqHz = actualMinFreq + ((1f - cursorYRatio) * (actualMaxFreq - actualMinFreq)).toInt()

            native.drawLine(marginLeft, cursorY, plotRight, cursorY, cursorLinePaint)

            val freqStr = " Hz"
            val badgeTextWidth = cursorBadgeTextPaint.measureText(freqStr)
            val badgePaddingHorizontal = 12f
            val badgeHeight = 38f

            val badgeLeft = marginLeft + 10f
            val badgeTop = (cursorY - badgeHeight / 2f).coerceIn(marginTop, plotBottom - badgeHeight)
            val badgeRight = badgeLeft + badgeTextWidth + (badgePaddingHorizontal * 2f)
            val badgeBottom = badgeTop + badgeHeight

            native.drawRoundRect(badgeLeft, badgeTop, badgeRight, badgeBottom, 8f, 8f, cursorBadgeBgPaint)
            native.drawText(freqStr, badgeLeft + badgePaddingHorizontal, badgeTop + 28f, cursorBadgeTextPaint)'''

new_cursor = '''            // --- CURSEUR EN FRÉQUENCE DISCRET ---
            if (!isReportModeActive) {
                val cursorY = marginTop + cursorYRatio * plotHeight
                val selectedFreqHz = actualMinFreq + ((1f - cursorYRatio) * (actualMaxFreq - actualMinFreq)).toInt()

                native.drawLine(marginLeft, cursorY, plotRight, cursorY, cursorLinePaint)

                val freqStr = " Hz"
                val badgeTextWidth = cursorBadgeTextPaint.measureText(freqStr)
                val badgePaddingHorizontal = 12f
                val badgeHeight = 38f

                val badgeLeft = marginLeft + 10f
                val badgeTop = (cursorY - badgeHeight / 2f).coerceIn(marginTop, plotBottom - badgeHeight)
                val badgeRight = badgeLeft + badgeTextWidth + (badgePaddingHorizontal * 2f)
                val badgeBottom = badgeTop + badgeHeight

                native.drawRoundRect(badgeLeft, badgeTop, badgeRight, badgeBottom, 8f, 8f, cursorBadgeBgPaint)
                native.drawText(freqStr, badgeLeft + badgePaddingHorizontal, badgeTop + 28f, cursorBadgeTextPaint)
            }'''

text = text.replace(old_cursor, new_cursor)

with open('app/src/main/java/com/example/nvhspectro/SpectrogramColormap.kt', 'w', encoding='utf-8') as f:
    f.write(text)
