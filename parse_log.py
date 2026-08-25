import re

with open(r'C:\Users\Louis\.gemini\antigravity-ide\brain\d589ca98-4207-491e-b901-fb5a46127aea\.system_generated\tasks\task-3707.log', 'r', encoding='utf-8') as f:
    log = f.read()

exceptions = re.findall(r'FATAL EXCEPTION.*?(\n.*?)+?(?=\n\n|\Z)', log, re.DOTALL)
for e in exceptions:
    print(e[:500])

match = re.search(r'E/AndroidRuntime.*Exception.*', log)
if match:
    start_idx = match.start()
    print("Found Exception:")
    print(log[start_idx:start_idx+1000])

