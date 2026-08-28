# NVH Spectro 🚗🔊

**NVH Spectro** est une application Android d'analyse acoustique et de suivi d'ordres pour les
essais véhicule (thermique, hybride, électrique). Elle capte le bruit habitacle par le
microphone du téléphone, l'analyse en temps réel, et projette les ordres du groupe
motopropulseur sur le spectre à partir de la vitesse GNSS et de la cinématique saisie.

![Android](https://img.shields.io/badge/Platform-Android%207.0%2B-green.svg)
![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-brightgreen.svg)
![Version](https://img.shields.io/badge/version-14.0.0-informational.svg)

> **Aucun accès réseau.** L'application ne déclare pas la permission INTERNET — une règle
> vérifiée par la CI. Enregistrements, télémétrie et journaux restent sur l'appareil et n'en
> sortent que par un export ou un partage explicite.

---

## 🎯 Fonctionnalités

### Analyse spectrale
- **Spectrogramme déroulant** avec palette « Jet », axes fréquence/temps gradués et curseur
  de fréquence.
- **Deux modes d'affichage** : niveau absolu (dBFS) ou **indice d'émergence NVH** (dB
  au-dessus du bruit local).
- **Réglages temps réel** : fenêtre temporelle, plage de fréquences, taille FFT
  (512 → 4096), plage dynamique dB, seuils du détecteur d'émergence.
- **Filtres DSP** (passe-bas / passe-haut / passe-bande / coupe-bande, Butterworth 8ᵉ ordre)
  appliqués **à la fois** à l'écoute et à l'analyse affichée — pas de filtre « pour l'oreille
  seulement ».

### Suivi d'ordres (GMPe)
- Cinématique par **V1000 direct**, **rapport global** ou **chaîne détaillée**
  (réducteur × pont × géométrie pneu).
- Projection H1/Hn sur le spectrogramme, suivi automatique de l'ordre sélectionné,
  étiquetage des harmoniques émergentes et rapport d'émergence accumulé.
- **Fenêtre de recherche dérivée de l'incertitude de vitesse** et bornée : si l'ordre ne peut
  pas être distingué de ses voisins, le suivi est **suspendu** (« Non identifiable ») au lieu
  de renvoyer une valeur ambiguë.

### Télémétrie GNSS
- Vitesse issue du récepteur GNSS interne (`GPS_PROVIDER` uniquement pour la chaîne de
  mesure), filtrée par un **Kalman [vitesse, accélération]** avec covariance.
- Chaque estimation porte son **âge, son incertitude et sa validité**. Périmée, elle
  n'alimente plus aucun calcul : l'écran affiche `--` plutôt qu'un dernier chiffre figé.
- **L'accélération est dérivée de la vitesse GNSS**, pas de l'accéléromètre (choix délibéré :
  pas de problème d'angle de montage). Elle ne voit pas les transitoires entre deux fixes.

### Analyse de fichiers
- Import **WAV** (PCM 16 bits, mono/stéréo ; 24 bits / flottant explicitement refusés avec un
  message) et extraction de la piste audio d'une **vidéo locale**, analysés **à leur propre
  fréquence d'échantillonnage**.
- Lecture synchronisée son / spectrogramme / image, avec la télémétrie du sidecar rejouée par
  **lissage RTS** (aller-retour) et non par simple interpolation.

### Rapports et exports
- **Enregistrement 30 s** (audio + télémétrie) vers `Downloads/NVH_Spectro_Exports`.
- **Mode rapport manuel** : tracé assisté des ordres sur le spectrogramme, plages
  vitesse / régime / fréquence par ordre.
- **Export PNG** de la vue figée et **rapport PDF** portant un cartouche de traçabilité :
  date, version de build, source (avec la route micro réellement obtenue), paramètres
  d'analyse, statut de reconstruction de la vitesse et niveau de confiance utilisé.
- **Journal de diagnostic** local, consultable et partageable depuis la fiche *Informations*.

### Terrain et accessibilité
- Thème sombre fixe, calibré pour un habitacle de jour comme de nuit.
- Cibles tactiles 48 dp, libellés vocaux (TalkBack), texte qui suit l'échelle système.
- **La couleur n'est jamais le seul canal** : le voyant GNSS porte aussi une forme (●/▲/✕)
  et son état écrit.
- Dégradation par permission : sans microphone, l'application reste utilisable en analyse de
  fichiers ; sans localisation précise, elle le dit à l'endroit où la vitesse s'afficherait.

---

## 🛠️ Architecture

Deux modules ; la séparation est vérifiée par la CI.

```
core/                       Kotlin pur — zéro import Android, testable sur JVM
├── FFTProcessor            Hann, FFT réelle, calibration, indice d'émergence
├── OrderTrackingEngine     UNE implémentation, partagée direct + fichier
├── OrderSearchPolicy       Budget d'erreur vitesse → RPM → fréquence d'ordre
├── KalmanSpeedEstimator    Vitesse/accélération avec covariance et rejet robuste
├── GnssSpeedSession        Qualification des fixes et règle de validité
├── MeasurementSession      État de session partagé (source, historiques, réglages)
├── PlotGeometry            Géométrie de tracé et transformation zoom/pan
└── TelemetryCodec          Sidecar télémétrie v3 (lit v1/v2)

app/                        Android
├── MainActivity, MainScreen, ui/       Compose, dialogues, canvases
├── LiveViewModel                       micro + GNSS + DSP direct
├── AnalyzerViewModel                   WAV/vidéo, lecture, filtres
├── ReportViewModel                     rapport manuel, exports PNG/PDF
├── CaptureEngine, AudioRepository      capture (un seul propriétaire du micro)
├── SpeedProvider, GnssDiagnosticsMonitor
├── data/                               fichiers, stockage, journal, réglages
└── export/                             PNG, PDF, cartouche de traçabilité
```

Détails DSP, budget d'erreur et limites d'emploi :
**[`doc/ARCHITECTURE_AND_DSP_METHODS.md`](doc/ARCHITECTURE_AND_DSP_METHODS.md)**.

---

## 🚀 Compilation

### Prérequis
- JDK 17
- Android SDK (compileSdk 36) ; `local.properties` doit contenir `sdk.dir=…`
- minSdk 24 (Android 7.0) · targetSdk 36

### Commandes

```bash
export JAVA_HOME=/chemin/vers/jdk-17

./gradlew :core:test :app:testDebugUnitTest   # tests unitaires
./gradlew :core:koverVerify                   # couverture :core ≥ 90 %
./gradlew :app:lintDebug                      # zéro erreur, sans baseline
./gradlew :app:assembleDebug                  # APK debug
./gradlew :app:assembleRelease                # APK release minifié (R8)
./ci/checks.sh                                # gates d'hygiène et de correction
./gradlew :app:installDebug                   # installation sur l'appareil connecté
```

Sous Windows/PowerShell, remplacer `export JAVA_HOME=…` par
`$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` et `./gradlew` par `.\gradlew`.

L'APK est nommé d'après la version : `APP_NVH_Spectro_v14.0.0-<variant>.apk`. La version est
définie **à un seul endroit** (`appVersionName` / `appVersionCode` dans `app/build.gradle.kts`)
et s'affiche telle quelle dans la fiche *Informations*.

---

## ✅ Qualité

Chaque `push` déclenche la CI (`.github/workflows/ci.yml`) : gates d'hygiène → ktlint →
detekt → tests unitaires (`:core` + `:app`) → couverture → lint → assemblage debug **et
release minifié**.

`ci/checks.sh` fait échouer la build si : un binaire ou un script jetable est versionné, la
version cesse d'être single-sourcée, une fréquence d'échantillonnage littérale apparaît hors
`AudioConfig`, `:core` importe Android, une couleur littérale apparaît hors `theme/Color.kt`,
un texte utilisateur est écrit en dur dans un fichier Compose, une surface WebView/JS ou la
permission INTERNET réapparaît, ou du code mort supprimé revient.

**Les tests verts ne suffisent pas** : tout changement au comportement visible est vérifié sur
appareil avant d'être déclaré fait, et la preuve est consignée dans `AAA-TRACKING.md`.

---

## 📌 État du projet

Refonte qualité menée sur la branche `aaa/phase0`, pilotée par des documents versionnés :

| Document | Rôle |
|---|---|
| `V13.1-audit.md` | Audit d'origine — 74 constats avec preuves `fichier:ligne` (gelé) |
| `audit-gps.md` | Audit dédié à la chaîne de vitesse GNSS — constats GPS-01…GPS-15 (gelé) |
| `V13.1-AAA-plan.md` / `plan-gps.md` | Plans de correction par phases et portes de sortie |
| `AAA-TRACKING.md` | Journal d'exécution : étapes, commits, déviations, preuves de gate |
| **`V13.2-audit.md`** | **Audit de l'état courant — 31 constats ouverts (voir ci-dessous)** |

**Phases 0 → 4 terminées** (fondations, correction de la mesure, concurrence et chaîne de
vitesse, architecture et persistance, UX/rapports/accessibilité) ainsi que **GPS-0 → GPS-4**.

---

## 🔍 Audit V13.2 — où en est réellement l'application

**[`V13.2-audit.md`](V13.2-audit.md)** est un audit complet et sans complaisance de la version
livrée ici : lecture manuelle des 75 fichiers Kotlin de production (~15 500 lignes), du build,
de la CI, du manifeste et des 202 tests, chaque constat cité en `fichier:ligne`. Il est mesuré
non pas contre la version précédente, mais contre la barre visée : **un instrument de terrain
qu'un acousticien peut défendre devant un client.**

> **Verdict : B− en ingénierie logicielle, C en maturité instrument.**
> La classe de défauts de mesure qui définissait la V13.1 (fréquences d'échantillonnage
> fausses, WAV mal parsés, consommateurs fuités, pipelines plantés) est **close**. Ce qui
> sépare encore cette version de l'objectif n'est plus la qualité du code — c'est la **preuve**.

| Domaine | Note | En une phrase |
|---|:---:|---|
| Chaîne de vitesse GNSS | **A−** | La meilleure partie de l'app — mais zéro donnée terrain la soutient |
| Données et stockage | **A−** | MediaStore, DataStore, sidecar v3 migré ; chemin pré-API-29 cassé |
| Sécurité et vie privée | **A−** | Absence d'INTERNET vérifiée par la CI, cycles micro/GNSS corrects |
| Architecture | **B+** | `:core` pur et couvert à 93 % ; `AppScreen` reste un monolithe |
| Concurrence et cycle de vie | **B** | DSP hors du thread principal — sauf une régression (voir ci-dessous) |
| Documentation | **B** | Réécrite à partir du code réel, avec clause de falsifiabilité |
| DSP / justesse de mesure | **B−** | Échelles honnêtes, FFT réelle ; l'indice d'émergence reste heuristique |
| Performance et mémoire | **B−** | Rendu hors main ; **aucune mesure** sur aucun appareil |
| Qualité Compose / UI | **B−** | Géométrie dp/sp, contrastes testés ; recomposition globale à 43 Hz |
| Tests | **B−** | 202 tests JVM et un golden DSP ; **zéro test instrumenté** |
| Build et release | **C+** | CI solide ; pas de signature, `applicationId` encore `com.example` |
| **Validation métrologique terrain** | **D** | **Aucune vérité terrain. Le plus grand écart à l'objectif.** |

### Les quatre écarts qui comptent

1. **Aucun chiffre affiché n'est validé sur matériel réel.** Toutes les constantes de
   l'estimateur et tous les seuils sont marqués `PROVISIONAL` dans le code — c'est honnête, mais
   la campagne qui les figerait (GPS-5 : 3 téléphones contre une vérité terrain, biais/MAE/RMSE/
   P95, couverture σ) **n'a pas eu lieu**. Toutes les portes de vérification ont été passées sur
   **un seul émulateur** ; aucun téléphone physique n'apparaît dans les preuves.
2. **Une régression réelle dans le chemin critique** (constat C-1, *Critique*) : le balayage
   d'ordres sur fichier complet (~10⁷–10⁸ opérations pour 5 minutes d'audio) tourne
   **sur le thread principal**, après chaque chargement et à chaque modification de la
   cinématique — l'interface gèle.
3. **La release n'existe pas** : pas de configuration de signature, `applicationId` toujours
   `com.example.nvhspectro`, aucun tag, aucun changelog, aucun test instrumenté.
4. **L'incertitude du V1000 n'est pas modélisée** (rayon dynamique, déflexion sous charge,
   arrondi des rapports). Pour H18 à V1000 = 10 km/h, 1,5 % d'erreur de rayon déplace la
   projection d'environ 7,6 Hz — comparable à tout le terme GNSS autour duquel les fenêtres de
   recherche ont été construites. **La bande de confiance affichée n'est pas l'erreur totale.**

L'audit liste 12 priorités de remédiation classées par nature (justesse, métrologie, release,
tests, performance). La suite est la **phase 5** et **GPS-5** : validation terrain, performance
et endurance, préparation de release.

> ⚠️ **Limite d'emploi.** Tant que la campagne de validation n'a pas eu lieu, les constantes de
> l'estimateur et les seuils sont **provisoires** : les résultats sont exploitables en essai
> exploratoire, mais **aucune revendication de précision ne doit dépasser la preuve collectée**.
> L'indice d'émergence est une méthode maison — il n'est **pas** conforme ECMA-74 / ISO 1996-2.

---

## 🌐 Site web

Présentation, documentation et explication de la chaîne de mesure — déployés depuis `site/`
par `.github/workflows/pages.yml`, sur chaque dépôt qui héberge le projet :

| Dépôt | Site |
| --- | --- |
| `Luigi-BARTH/NVHSpectro` (référence) | **<https://luigi-barth.github.io/NVHSpectro/>** |
| `TheSamLePirate/NVHSpectro` (miroir) | <https://thesamlepirate.github.io/NVHSpectro/> |

Le site est indépendant du compte : les liens « GitHub ↗ » et la commande `git clone` sont
déduits de l'hôte qui sert la page. La balise `canonical` pointe vers le dépôt de référence.

> Pour activer Pages sur un dépôt : *Settings → Pages → Source = **GitHub Actions***, et
> *Settings → Actions → General → Workflow permissions = **Read and write***. Sur un dépôt
> **privé**, Pages exige un plan GitHub Pro/Team ; sinon, passer le dépôt en public.

---

## 📄 Licence & contact

Développé pour l'analyse NVH automobile chez VIBRATEAM [Vibratec (Everenn Group)].
Contact : louis.barthelemy@vibrateam.fr
