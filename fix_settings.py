import os

file_path = r'app\src\main\java\com\example\nvhspectro\ui\SettingsDialog.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Update AddFilterDialog call
old_call = '''    if (showFilterDialog) {
        AddFilterDialog(
            onDismiss = { showFilterDialog = false },'''
new_call = '''    if (showFilterDialog) {
        AddFilterDialog(
            existingCount = activeFilters.size,
            onDismiss = { showFilterDialog = false },'''
code = code.replace(old_call, new_call)

# 2. Update AddFilterDialog definition and color logic
old_def = '''fun AddFilterDialog(
    onDismiss: () -> Unit,
    onAddFilter: (AudioFilter) -> Unit
) {
    var selectedType by remember { mutableStateOf(FilterType.LOW_PASS) }
    var minFreqText by remember { mutableStateOf("") }
    var maxFreqText by remember { mutableStateOf("") }
    
    val colors = listOf(Color(0xFFFF5252), Color(0xFF448AFF), Color(0xFF69F0AE), Color(0xFFFFE082), Color(0xFFE040FB))
    val randomColor = remember { colors.random() }'''

new_def = '''fun AddFilterDialog(
    existingCount: Int,
    onDismiss: () -> Unit,
    onAddFilter: (AudioFilter) -> Unit
) {
    var selectedType by remember { mutableStateOf(FilterType.LOW_PASS) }
    var minFreqText by remember { mutableStateOf("") }
    var maxFreqText by remember { mutableStateOf("") }
    
    val assignedColor = remember(existingCount) {
        when (existingCount) {
            0 -> Color(0xFFFFEB3B) // Jaune
            1 -> Color(0xFF00E676) // Vert
            else -> Color(0xFFFF5252) // Rouge
        }
    }'''
code = code.replace(old_def, new_def)
code = code.replace('color = randomColor', 'color = assignedColor')

# 3. Limit to 3 filters in the IconButton
old_icon = '''                            IconButton(
                                onClick = { showFilterDialog = true },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0xFFE91E63), RoundedCornerShape(14.dp))
                            ) {
                                Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }'''

new_icon = '''                            IconButton(
                                onClick = { showFilterDialog = true },
                                enabled = activeFilters.size < 3,
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(if (activeFilters.size < 3) Color(0xFFE91E63) else Color.Gray.copy(alpha=0.5f), RoundedCornerShape(14.dp))
                            ) {
                                Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }'''
code = code.replace(old_icon, new_icon)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(code)

print("Fixed SettingsDialog.kt")
