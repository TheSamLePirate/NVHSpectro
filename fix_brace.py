import sys
with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'w', encoding='utf-8') as f:
    # Just remove the last line if it only contains a closing brace
    if lines[-1].strip() == '}':
        f.writelines(lines[:-1])
    else:
        # Or remove the last '}' found in the file
        content = "".join(lines)
        last_brace = content.rfind('}')
        if last_brace != -1:
            content = content[:last_brace] + content[last_brace+1:]
        f.write(content)
print("Removed extra brace")
