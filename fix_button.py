import re

with open('app/src/main/java/com/example/nvhspectro/MainScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# I will find the exact string.
pattern = r"                    // 2\. Bouton Rapport d'\w+.*?Text\(\s*text = \"[^\"]+\",\s*fontSize = 11\.sp,\s*fontWeight = FontWeight\.Bold,\s*maxLines = 1\s*\)\s*\}"
replacement = '''                    // 2. Bouton Rapport d'Emergence
                    Button(
                        onClick = { viewModel.toggleReportMode() },
                        enabled = !isVideoMode,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isReportModeActive) Color(0xFFE91E63) else MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color(0xFF424242),
                            disabledContentColor = Color.Gray
                        )
                    ) {
                        Text(
                            text = if (isReportModeActive) "Quitter Rapport" else "Rapport Manuel",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }'''
new_content = re.sub(pattern, replacement, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/nvhspectro/MainScreen.kt', 'w', encoding='utf-8') as f:
    f.write(new_content)
print("Button updated")
