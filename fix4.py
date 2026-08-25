import sys
with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('currentUserPoints: List<android.graphics.PointF>', 'currentUserPoints: List<com.example.nvhspectro.data.ManualOrderAnchor>')
text = text.replace('import android.graphics.PointF', 'import com.example.nvhspectro.data.ManualOrderAnchor')

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text)
