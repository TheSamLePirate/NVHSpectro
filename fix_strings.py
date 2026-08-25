import sys
with open("app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt", "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace(r"\${", "${")
content = content.replace(r"\"", "\"")
content = content.replace("?", "é")

with open("app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt", "w", encoding="utf-8") as f:
    f.write(content)
print("done")
