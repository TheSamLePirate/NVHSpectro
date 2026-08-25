with open('app/src/main/java/com/example/nvhspectro/MainViewModel.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

brace_count = 0
for i, line in enumerate(lines):
    brace_count += line.count('{') - line.count('}')
    if brace_count == 0 and 'class MainViewModel' in ''.join(lines[:i]):
        print(f"Class MainViewModel seems to close at line {i+1}")

print(f"Final brace count: {brace_count}")
