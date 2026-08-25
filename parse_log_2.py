import re
from datetime import datetime

with open(r'C:\Users\Louis\.gemini\antigravity-ide\brain\d589ca98-4207-491e-b901-fb5a46127aea\.system_generated\tasks\task-3707.log', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(reversed(lines)):
    if "FATAL EXCEPTION" in line or "java.lang.IllegalArgumentException" in line or "Exception" in line:
        if "E/AndroidRuntime" in line:
            start_idx = len(lines) - 1 - i
            print("Found Exception at line", start_idx)
            for j in range(max(0, start_idx-2), min(len(lines), start_idx+30)):
                print(lines[j].rstrip())
            break
