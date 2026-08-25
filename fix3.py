import sys
with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('currentUserPoints: List<Any>', 'currentUserPoints: List<android.graphics.PointF>')
text = text.replace('manualTrackedOrders: List<Any>', 'manualTrackedOrders: List<com.example.nvhspectro.data.SmartTrackedOrder>')
text = text.replace('selectedValidatedOrder: Any?', 'selectedValidatedOrder: com.example.nvhspectro.data.SmartTrackedOrder?')

text = text.replace('as? com.example.nvhspectro.SmartTrackedOrder', 'as? com.example.nvhspectro.data.SmartTrackedOrder')

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text)
