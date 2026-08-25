# Refonte de l'interface Rapport Manuel (Selon le schéma paysage)

## But
Repenser l'interface du mode Rapport pour qu'elle corresponde **exactement** au schéma que vous avez fourni. Résoudre les problèmes critiques de lenteur du zoom, du décalage des points de dessin, du rendu TTNR et de la liste écrasée.

## User Review Required
> [!IMPORTANT]
> - **Validation automatique vs Bouton Valider Point** : Dans votre message précédent, vous m'aviez demandé "Valide automatiquement, ne me demande pas de mettre SUBMIT si tu peux". Cependant, sur votre nouveau schéma, il y a un bouton "valider point" vert. **Je propose de garder la validation automatique au touché** (un point apparaît dès qu'on touche l'écran) pour gagner du temps, et d'ignorer le bouton "valider point" du schéma. Est-ce que cela vous va ?
> - **Mode Portrait** : Le schéma est pensé pour le format paysage. En mode portrait, la disposition sera adaptée (colormap en haut, outils en bas) pour rester lisible. 

## Proposed Changes

### UI & Layout (ReportModeScreen.kt)
- **Architecture Grid/Row** : Refonte complète de la vue pour correspondre à la disposition demandée (paysage priorisé).
- **Haut Gauche** : Deux Row de boutons "Toggle" pour (Absolue/TTNR) et (Navigation/Dessin).
- **Haut Droit** : Cartouche d'informations GMPe (Véhicule, Nom, V1000).
- **Centre Gauche** : SpectrogramArea occupant le maximum d'espace disponible (weight(1f)).
- **Droite (Barre latérale)** :
  - **Liste Ordre** (LazyColumn) avec un poids (weight(1f)) pour qu'elle ne soit plus écrasée.
  - **Valider ordre** (Jaune/Vert)
  - **Supprime points** (Bleu)
  - **Supprime ordre** (Violet)
- **Bas** : Boutons "Export PDF" et "Quitte rapport".

### Performance & Pan/Zoom (SpectrogramColormap.kt)
- **GPU Acceleration** : Remplacement de la méthode de zoom actuelle (calculs srcRect dans le drawImage) par l'utilisation du modificateur graphicsLayer de Compose. Cela va utiliser la carte graphique du téléphone pour zoomer et déplacer l'image de manière **totalement fluide et instantanée** (60 FPS garanti).
- **Correction des Coordonnées** : La logique actuelle calcule mal les coordonnées en zoom. Avec graphicsLayer, la transformation des coordonnées du doigt vers les pixels de l'image (pour tracer les points exactement sous le doigt) sera mathématiquement exacte en inversant la matrice de transformation.

### Correction du Mode TTNR
- Le toggle Absolue/TTNR du mode rapport sera correctement branché à la variable displayMode transmise au SpectrogramColormap (actuellement, la valeur du mode "Live" restait figée).

## Verification Plan
1. Lancer l'application, ouvrir un fichier WAV ou Live.
2. Passer en mode Rapport.
3. Vérifier que la disposition correspond exactement à votre schéma.
4. Faire un zoom (pinch) : vérifier que l'action est **fluide**.
5. Passer en mode Dessin : poser un point, vérifier qu'il apparaît **exactement** sous le doigt, même en étant zoomé ou décalé.
6. Cliquer sur le bouton TTNR : vérifier que le colormap change bien de couleurs.
