import re

with open('app/src/main/java/com/example/nvhspectro/MainViewModel.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace guidePenalty = 0.5f * ... with 0.1f * ...
text = text.replace('val guidePenalty = 0.5f * distToExpected * distToExpected', 'val guidePenalty = 0.1f * distToExpected * distToExpected')

with open('app/src/main/java/com/example/nvhspectro/MainViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(text)

print('Updated MainViewModel.kt penalty')
