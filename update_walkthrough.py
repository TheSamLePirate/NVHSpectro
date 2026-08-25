import re

with open('C:/Users/Louis/.gemini/antigravity-ide/brain/d589ca98-4207-491e-b901-fb5a46127aea/walkthrough.md', 'r', encoding='utf-8') as f:
    content = f.read()

new_content = content + '''

## Implémentation du Bouton Rapport (Tracking Manuel)

Le système de création manuelle de rapport d'ordres est maintenant intégré et fonctionnel.

### Ce qui a été réalisé :

1. **Refonte de l'interface graphique** :
   - Le bouton "Rapport" dans la barre inférieure agit maintenant comme une bascule pour le isReportModeActive ("Mode Rapport").
   - Lorsque ce mode est activé, le graphique de télémetrie inférieur se masque, laissant la place à un **spectrogramme plein écran**.
   - Un panneau de contrôle ManualReportControlsPanel apparaît en surimpression pour gérer la validation et l'exportation.

2. **Interaction Tactile (Pan/Zoom/Tap)** :
   - Mise à jour du SpectrogramCanvas pour supporter le geste de **Zoom** (pincement) et de **Pan** (déplacement).
   - Les "Taps" simples sur le spectrogramme sont captés, transformés à travers la matrice de zoom/pan, puis convertis en (frameIndex, binIndex).
   - Ces points d'ancrage utilisateur s'affichent en temps réel (cercles rouges/blancs).

3. **Tracking Intelligent ("Smart Ridge Tracking")** :
   - Au fur et à mesure que l'utilisateur place des points, le MainViewModel interpole un tracé.
   - Autour de cette interpolation linéaire, l'algorithme cherche le maximum local d'énergie (local max energy search radius = 4 bins) pour "coller" parfaitement à l'harmonique visée.
   - Ce chemin intelligent est tracé en pointillé blanc sur le spectrogramme.

4. **Validation et Rapport PDF** :
   - Une fois le tracé satisfaisant, l'utilisateur appuie sur "Valider l'Ordre".
   - L'application calcule le nom de l'ordre, sa plage RPM, sa plage de fréquences, sa plage de vitesse, et son émergence maximale (en dB TTNR) et l'ajoute à la liste des manualTrackedOrders.
   - L'ordre validé est dessiné de manière permanente avec une couleur unique.
   - Le bouton "Générer PDF" génère un rapport au format A4 (ndroid.graphics.pdf.PdfDocument) contenant l'en-tête, le contexte cinématique et le tableau récapitulatif des ordres identifiés, puis le sauvegarde dans le dossier Downloads de l'appareil.

### Vérification Manuelle

Je vous invite à tester ce nouveau workflow directement sur le téléphone :
- Ouvrez un fichier WAV avec un balayage moteur.
- Appuyez sur **Rapport Manuel**.
- Zoomez sur une zone d'intérêt et tapotez pour créer un tracé sur une harmonique.
- Vérifiez que la ligne pointillée suit bien l'harmonique (Smart Tracking).
- Validez l'ordre et exportez le PDF.
'''

with open('C:/Users/Louis/.gemini/antigravity-ide/brain/d589ca98-4207-491e-b901-fb5a46127aea/walkthrough.md', 'w', encoding='utf-8') as f:
    f.write(new_content)
print("Walkthrough updated")
