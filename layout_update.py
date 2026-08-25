import sys

file_path = 'app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Change root Box to Row
content = content.replace(
'''    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A))
    ) {
        // --- COUCHE 1 : LE SPECTROGRAMME PLEIN ECRAN ---''',
'''    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A))
    ) {
        // --- GAUCHE : LE SPECTROGRAMME ET SA BARRE ---
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            SpectrogramCanvas('''
)

# 2. Add closing brace for the left Box before the right panel
content = content.replace(
'''        // 2. Panneau latǸral droit (Contrles du rapport)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.85f)
                .width(260.dp)
                .padding(end = 16.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xD91E1E2E), Color(0xCC12121A))
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(16.dp))
                .padding(16.dp),''',
'''        } // Fin de la Box gauche

        // --- DROITE : PANNEAU LATERAL ---
        Column(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
                .background(Color(0xFF161622))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Contenu du rapport (liste, boutons)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xD91E1E2E), Color(0xCC12121A))
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(16.dp))
                    .padding(16.dp),'''
)

# 3. Replace the Bottom Bar
content = content.replace(
'''        // 3. Barre du bas (Export / Quitter)
        val localContext = androidx.compose.ui.platform.LocalContext.current
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.generatePdfReport(localContext) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29B6F6)),
                modifier = Modifier.height(56.dp).width(200.dp)
            ) {
                Text("EXPORTER LE PDF", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }

            Button(
                onClick = { viewModel.toggleReportMode() },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFF5252)),
                modifier = Modifier.height(56.dp).width(200.dp)
            ) {
                Text("QUITTER", color = Color(0xFFFF5252), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
    }
}''',
'''            } // Fin de la colonne du rapport
            
            // 3. Barre du bas (Export / Quitter)
            val localContext = androidx.compose.ui.platform.LocalContext.current
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.generatePdfReport(localContext) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29B6F6)),
                    modifier = Modifier.height(56.dp).fillMaxWidth()
                ) {
                    Text("EXPORTER LE PDF", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
    
                Button(
                    onClick = { viewModel.toggleReportMode() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFF5252)),
                    modifier = Modifier.height(56.dp).fillMaxWidth()
                ) {
                    Text("QUITTER", color = Color(0xFFFF5252), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
        } // Fin de la Column de droite
    } // Fin de la Row principale
}'''
)

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Layout updated.")
