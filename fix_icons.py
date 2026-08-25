import os

file_path = r'app\src\main\java\com\example\nvhspectro\ui\SettingsDialog.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    code = f.read()

# Remove imports
code = code.replace('import androidx.compose.material.icons.Icons\n', '')
code = code.replace('import androidx.compose.material.icons.filled.Add\n', '')
code = code.replace('import androidx.compose.material.icons.filled.Close\n', '')

# Replace Icon with Text
old_add = 'Icon(Icons.Default.Add, contentDescription = "Ajouter un filtre", tint = Color.White, modifier = Modifier.size(16.dp))'
new_add = 'Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)'
code = code.replace(old_add, new_add)

old_close = 'Icon(Icons.Default.Close, contentDescription = "Supprimer", tint = Color.White, modifier = Modifier.size(16.dp))'
new_close = 'Text("X", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)'
code = code.replace(old_close, new_close)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(code)

print("Fixed icons")
