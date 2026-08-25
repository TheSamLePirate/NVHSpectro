import re

with open('app/src/main/java/com/example/nvhspectro/MainScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Remove the old if (isReportModeActive) block for ManualReportControlsPanel inside Zone 1
content = re.sub(r'\s*if \(isReportModeActive\) \{\s*Box\(modifier = Modifier\.align\(Alignment\.BottomCenter\)\) \{\s*ManualReportControlsPanel\(viewModel = viewModel\)\s*\}\s*\}', '', content)

# Change weight(if (isReportModeActive) 1f else 0.55f) back to weight(0.55f)
content = content.replace('.weight(if (isReportModeActive) 1f else 0.55f)', '.weight(0.55f)')

# Remove the if (!isReportModeActive) { block around Zone 2.
# This one is tricky via regex because of indentation, so I'll just remove the exact string if (!isReportModeActive) { 
# and the matching closing brace.
# Actually, since it's just one indent, we can remove it. But since it's now in the else block, it doesn't hurt functionally.
# However, it's better to clean it up. Let's do a simple regex:
zone2_pattern = r'(\s*)if \(!isReportModeActive\) \{\n(.*?)\n\s*\}\n\s*// Dialogues'
match = re.search(zone2_pattern, content, re.DOTALL)
if match:
    indent = match.group(1)
    inner_content = match.group(2)
    # unindent inner content
    unindented = '\n'.join([line[4:] if line.startswith('    ') else line for line in inner_content.split('\n')])
    content = content[:match.start()] + indent + unindented + '\n\n        // Dialogues' + content[match.end():]

with open('app/src/main/java/com/example/nvhspectro/MainScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("MainScreen cleaned up.")
