# NVH Spectro — Architecture, méthodes DSP et budget d'erreur

**Application :** NVH Spectro · **Version :** 14.0.0 (`versionCode` 14)
**Auteur :** Louis BARTHELEMY — VIBRATEAM [Vibratec (Everenn Group)]
**Contact :** louis.barthelemy@vibrateam.fr
**Dernière révision :** 2026-08-27 — réécrit d'après le code réel (phases AAA 0→4, GPS-0→GPS-4)

> **Statut de ce document.** Il décrit ce que le code fait *aujourd'hui*, y compris ses
> limites. Les versions précédentes de ce fichier (v10) décrivaient une architecture qui
> n'existait plus et attribuaient à l'indice d'émergence une conformité normative qu'il n'a
> jamais eue. Toute affirmation ci-dessous doit rester vérifiable dans le code ; si elle ne
> l'est plus, c'est le document qui est faux.

---

## 1. Ce que l'application mesure — et ce qu'elle ne mesure pas

NVH Spectro est un instrument de terrain : il capte le bruit dans l'habitacle par le
microphone du téléphone, l'analyse en temps réel, et — quand la cinématique GMPe est
renseignée — projette les ordres du groupe motopropulseur sur le spectre à partir de la
vitesse GNSS.

**Ce qui est mesuré :**

| Grandeur | Source | Unité |
|---|---|---|
| Niveau spectral | Microphone → FFT | dBFS (pleine échelle numérique) |
| Indice d'émergence NVH | Dérivé du spectre | dB au-dessus du bruit local |
| Vitesse véhicule | Récepteur GNSS interne (`Location.speed`) | km/h |
| Accélération | **Dérivée de la vitesse GNSS** (état du filtre de Kalman) | g |
| Régime, fréquences d'ordre | Calculés depuis la vitesse et la cinématique saisie | RPM, Hz |

**Ce qui n'est PAS mesuré :**

- **L'accélération ne vient pas de l'accéléromètre.** C'est l'état `a` du filtre vitesse ;
  à ~1 Hz de cadence GNSS elle ne voit pas les transitoires rapides. C'est un choix
  délibéré (pas de problème d'angle de montage), pas une approximation cachée.
- **Aucun niveau acoustique absolu.** Le dBFS est relatif à la pleine échelle du
  convertisseur, pas à 20 µPa. L'application n'est pas un sonomètre et ne remplace pas un
  microphone calibré.
- **Aucune conformité normative** de l'indice d'émergence — voir §4.3.

---

## 2. Architecture logicielle

Deux modules Gradle. La séparation est vérifiée par la CI : `ci/checks.sh` échoue si `:core`
importe quoi que ce soit d'Android.

```
:core   Kotlin pur, zéro import Android — tout ce qui décide d'un nombre.
        Testable sur JVM en quelques secondes ; couverture ≥ 90 % imposée (koverVerify).

:app    Android : capture, GNSS, rendu Compose, fichiers, exports.
```

### 2.1 `:core` — le moteur de mesure

| Fichier | Rôle |
|---|---|
| `AudioConfig` | Le SEUL endroit où une fréquence d'échantillonnage littérale est admise (gate CI) |
| `FFTProcessor` | Fenêtrage Hann, FFT réelle, calibration d'amplitude, indice d'émergence |
| `LiveAnalysisEngine` | État DSP du direct (EMA d'ordre, persistance, compteurs) |
| `OrderTrackingEngine` | UNE implémentation du suivi d'ordre, partagée direct + balayage fichier |
| `OrderSearchPolicy` | Budget d'erreur cinématique et fenêtre de recherche dynamique (§5) |
| `MeasurementSession` | Machine à états partagée : mode source, historiques, réglages, provenance |
| `TimelineMapper` | Correspondance index-trame ↔ temps ↔ index-télémétrie |
| `PlotGeometry` | Géométrie de tracé et transformation zoom/pan (données → pixels) |
| `KalmanSpeedEstimator` | Estimateur vitesse/accélération avec covariance (§6) |
| `GnssSpeedSession` | Qualification des fixes et règle de validité |
| `RtsSpeedSmoother`, `SpeedReconstruction` | Lissage aller-retour pour les rejeux différés |
| `TelemetryCodec` | Sidecar de télémétrie, schéma v3 (lit v1 et v2) |
| `FieldTraceV2` | Trace terrain versionnée pour le réglage de l'estimateur |
| `KinematicsConfig` | V1000, chaîne de transmission, géométrie pneu |
| `WavAnalysis`, `SmartPathTracker`, `FilterChain`, `BiQuadFilter` | Balayage fichier, tracé assisté, filtres |

### 2.2 `:app` — la couche Android

```
MainActivity ─ AppNavigation ─ PermissionGate ─ AppScreen
                                                   │
              ┌────────────────────┬───────────────┴──────────────┐
       LiveViewModel        AnalyzerViewModel              ReportViewModel
   (micro, GNSS, direct)   (WAV/vidéo, lecture,        (rapport manuel, PNG/PDF)
                             filtres)
              └────────────────────┴──────────────────────────────┘
                                   │
                          MeasurementSession  (:core, portée processus)
```

Il n'y a **plus de `MainViewModel`**. Le monolithe de 2 004 lignes a été remplacé par trois
ViewModels partageant une `MeasurementSession`.

> ⚠️ **Écart connu.** La porte d'architecture du plan AAA fixe 300 lignes de code par
> ViewModel. À la sortie de la phase 3 la limite était tenue (281 / 296 / 189) ; les ajouts
> de la phase 4 (politique de permissions, lecture sampled du niveau d'ordre, résolution des
> chaînes de caractères) l'ont fait dépasser : **LiveViewModel 385, AnalyzerViewModel 343,
> ReportViewModel 214** lignes de code. L'enregistreur de terrain 30 s (état, minuterie,
> sauvegarde) est le candidat naturel à l'extraction hors de `LiveViewModel`.

**Chaînes techniques :**

- `CaptureEngine` — l'unique propriétaire du microphone. Les changements de réglage passent
  par `flatMapLatest` : la capture précédente est annulée avant la suivante, ce qui rend
  structurellement impossible l'empilement de consommateurs. Tampon borné (64 trames,
  `DROP_OLDEST`) avec compteurs d'intégrité.
- `AudioRepository` — `AudioRecord` en `UNPROCESSED` si l'appareil le déclare, sinon
  `VOICE_RECOGNITION` (la route obtenue est inscrite dans les exports). Chaque trame porte
  son instant de capture BOOTTIME via `AudioRecord.getTimestamp`.
- Le DSP tourne sur un thread dédié `nvh-dsp` ; seules les écritures de `StateFlow`
  reviennent sur le thread principal.
- `SpeedProvider` — abonnement `GPS_PROVIDER` uniquement pour la chaîne métrologique,
  callbacks sur le thread `nvh-gnss`.
- `SpectrogramImageProducer` — bitmaps produits sur `Dispatchers.Default`, sous-échantillonnés
  à ≤ 4096 colonnes, double-tamponnés.
- `DiagnosticLog` — journal local rotatif (2 × 256 ko), partagé uniquement sur action
  explicite de l'utilisateur. **L'application n'a pas la permission INTERNET** (gate CI) :
  aucune donnée ne peut quitter l'appareil autrement que par un export volontaire.

---

## 3. Acquisition et chaîne temporelle

- **Capture :** PCM 16 bits, 44,1 kHz en direct ; les fichiers importés sont analysés **à
  leur propre fréquence d'échantillonnage** (une vidéo à 48 kHz est analysée à 48 kHz).
- **Fenêtrage :** Hann, recouvrement 50 % (pas = N/2).
- **Tailles FFT :** 512 / 1024 / 2048 / 4096 en direct (défaut 2048) ; 2048 fixe pour
  l'analyse de fichier.
- **Base de temps :** BOOTTIME (`elapsedRealtimeNanos`) partout. Ni `System.currentTimeMillis()`
  ni `Location.time` n'entrent dans un calcul d'intervalle — une correction NTP ne peut plus
  fabriquer une accélération fantôme.
- **Appariement son/vitesse :** la vitesse est évaluée à l'instant de capture du **centre**
  de la fenêtre FFT analysée, pas à l'instant où le DSP la traite. Un retard dans la file
  DSP ne peut donc plus associer un spectre à une vitesse plus récente que le son.

### 3.1 Calibration d'amplitude

Pour une sinusoïde pleine échelle centrée sur une raie, fenêtre de Hann :

```
Magnitude(i) [dBFS] = 20 · log10( |X(i)| / (N/4) )
```

Le facteur `N/4` combine le gain cohérent de la fenêtre de Hann (0,5) et le partage de la
puissance entre raies conjuguées : 0 dBFS en entrée se lit 0 dBFS. Vérifié analytiquement par
test (0, −20, −60 dBFS, raies centrées et décentrées) et figé par un snapshot doré.
La FFT est une transformée **réelle** (`realForward`) sur tampons préalloués — moitié moins
de calcul et aucune allocation par trame.

**Perte de festonnage (scalloping).** Une raie entre deux bins est sous-estimée jusqu'à
1,42 dB. Les bins bruts conservent volontairement cette perte physique ; la **lecture d'ordre**
(celle qui alimente les graphes et les rapports) est corrigée par interpolation parabolique
d'amplitude — erreur résiduelle ≤ 0,32 dB au pire cas.

### 3.2 Matrice des indicateurs (44,1 kHz)

| N | Recouvrement | Pas Δt | Bloc 1/Δf | Cadence | Résolution Δf |
|---|---|---|---|---|---|
| 512 | 50 % | 5,8 ms | 11,6 ms | 172,3 tr/s | 86,1 Hz |
| 1024 | 50 % | 11,6 ms | 23,2 ms | 86,1 tr/s | 43,1 Hz |
| 2048 | 50 % | 23,2 ms | 46,4 ms | 43,1 tr/s | 21,5 Hz |
| 4096 | 50 % | 46,4 ms | 92,9 ms | 21,5 tr/s | 10,8 Hz |

Ces valeurs sont recalculées à l'écran à partir de la fréquence réelle de la source.

---

## 4. Indice d'émergence NVH

### 4.1 Principe

Pour chaque raie candidate, l'indice compare la puissance du ton à la densité de bruit
estimée localement :

- **Bande critique (Terhardt)** `Δfc(f) = 25 + 75 · (1 + 1,4 · (f/1000)²)^0,69` Hz,
  bornée à 350 Hz pour l'estimation du bruit local (au-delà, en habitacle, on intègre du
  bruit aérodynamique large bande hors sujet) et plancher à 150 Hz pour la bande de calcul.
- **Puissance du ton :** pic + fuite adjacente.
- **Puissance de bruit :** densité moyenne sur la fenêtre locale, en excluant la traînée du ton.
- **Sortie :** émergence en dB, bornée `[0 ; 30]`, avec un plancher de détection à 1 dB
  (en dessous, la sortie est **0** — pas une sentinelle négative).

### 4.2 Intégration temporelle

L'intégration se fait en **puissance linéaire**, avec une constante de temps honnête :

```
TTNR_INTEGRATION_TAU_SEC = 0,052 s   (52 ms)
α = 1 − exp(−Δt / τ)                 (Δt = intervalle réel de trame)
```

α est dérivé de l'intervalle réel : changer la taille FFT ne change plus silencieusement la
dynamique. Les anciens commentaires annonçaient τ = 220 ms puis 110 ms ; les deux
contredisaient le calcul (α = 0,36 à 23,2 ms ⇒ τ ≈ 52 ms). Aucune valeur sentinelle
(−100 dB) n'entre plus dans une moyenne.

**Détecteur de choc :** exprimé en **taux** (`258 dB/s`, équivalent à l'historique 6 dB par
trame à 43 tr/s) et non plus par trame, donc indépendant de la taille FFT. La première trame
d'un flux est désormais analysée (elle était systématiquement écartée).

### 4.3 ⚠️ Statut normatif — à lire avant toute publication de résultat

> **L'indice d'émergence NVH est une méthode interne. Il n'implémente PAS la norme
> ECMA-74 ni ISO 1996-2 et ne doit jamais être présenté comme tel.**

Raison technique : les bins sont calibrés en **amplitude** (gain cohérent `N/4`) tandis que
l'estimation de bruit somme des puissances de bins comme une **densité spectrale**, sans
correction ENBW (pour Hann, ENBW = 1,5 bin, soit +1,76 dB). Le ton et le bruit sont donc
exprimés dans deux systèmes d'unités différents et le rapport porte un biais fixe d'environ
1,8 dB que rien ne compense. Comme score de détection interne c'est exploitable ; comme
grandeur normative, non.

Le rapport PDF porte cette mention en pied de page. Une conformité ECMA-74 réelle est une
évolution à valider séparément (décision D5).

---

## 5. Budget d'erreur : vitesse → régime → fréquence d'ordre

*(Artéfact exigé par la porte « DSP / mesure » du plan AAA §1 ; implémenté dans
`OrderSearchPolicy`.)*

### 5.1 Propagation

Vitesse en km/h, `V1000` en km/h pour 1000 tr/min :

```
rpm    = v · 1000 / V1000            σrpm   = σv · 1000 / V1000
f(Hn)  = n · rpm / 60                σf(Hn) = n · σrpm / 60
```

### 5.2 Ordre de grandeur

Avec `V1000 = 10 km/h`, un fix encore classé correct (`σv = 0,5 m/s = 1,8 km/h`) et l'ordre
H18 :

```
σrpm  = 1,8 · 1000 / 10        = 180 tr/min
σf    = 18 · 180 / 60          ≈ 54 Hz
```

À 44,1 kHz et FFT 2048, un bin vaut 21,5 Hz. **L'ancienne recherche fixe de ±1 bin était donc
plus étroite qu'un seul écart-type** : le suivi lisait du bruit au bin projeté pendant que le
voyant GPS restait vert.

### 5.3 Fenêtre de recherche et identifiabilité

```
demi-largeur = k · σf + Δf          avec k = CONFIDENCE_K = 2 (≈ 95 %)
```

Elle est **bornée** à la moitié de l'écart au rang adjacent (soit `h1/2`). Au-delà, la
fenêtre balaierait les raies des ordres voisins : le suivi est alors **suspendu** et l'écran
affiche « Non identifiable » plutôt qu'une valeur ambiguë. Le `k` employé est inscrit dans
les exports et sur le PDF.

Quand σ est inconnu (estimateur α-β, sidecar antérieur à v3), la politique retombe sur le
rayon fixe historique et l'analyse est marquée « incertitude inconnue ».

### 5.4 Ce que ce budget ne couvre pas

**L'incertitude de `V1000` elle-même n'est pas modélisée** — rayon dynamique du pneu (charge,
pression, usure, vitesse), arrondis de rapports. Elle peut **dominer** le terme GNSS. Sa
caractérisation appartient à la campagne de validation terrain (plan 5.3) ; tant qu'elle
n'est pas faite, une bande de confiance affichée ne couvre que la part GNSS.

---

## 6. Chaîne de vitesse GNSS

### 6.1 Source et qualification

`LocationManager.GPS_PROVIDER` est la **seule** source métrologique. Sur API 31+ la
souscription est une `LocationRequest` explicite (haute précision, intervalle nul, sans
batching). Un éventuel repli FUSED/NETWORK est marqué `INFORMATION_ONLY` : il n'alimente
jamais l'estimateur sans provenance GNSS.

Avant d'atteindre l'estimateur, un fix est rejeté (avec motif typé) s'il est non fini ou
négatif, s'il est simulé (`isMock`, sauf configuration de test), ou s'il est périmé à la
livraison. `speedAccuracyMetersPerSecond` est conservée telle quelle — **jamais remplacée par
la précision horizontale**, qui mesure autre chose.

### 6.2 Estimateur

Filtre de Kalman à état `[vitesse, accélération]`, pas de temps variable, mise à jour de
Joseph (covariance symétrique définie positive) :

```
F(dt) = [[1, dt], [0, 1]]        H = [1, 0]
R     = max(σv, plancher)²       Q(dt) = q · [[dt³/3, dt²/2], [dt²/2, dt]]
```

Paramètres actuels (**tous PROVISOIRES** jusqu'à la campagne GPS-5) : densité de jerk
`q = 0,5`, plancher σ `0,1 m/s`, σ par défaut `2,0 m/s` quand l'appareil n'en fournit pas
(l'échantillon est alors `DEGRADED`), seuil NIS `χ²(1) = 9`, accélération plausible
`12 m/s²`, horizon de prédiction `2 s`, re-amorçage après `5 s`.

Un fix incohérent avec son incertitude déclarée est rejeté statistiquement ; la
réacquisition exige deux fixes mutuellement cohérents (ou une soupape après 4 rejets, ou un
trou > 5 s). Deux erreurs multipath successives ne peuvent donc plus être prises pour une
transition réelle.

### 6.3 Validité — la règle qui protège les calculs

`kinematicSpeedMps()` est la seule vitesse que la chaîne cinématique a le droit de consommer.
Elle vaut **null** dès que l'estimation est `INVALID` : pas de fix, au-delà de l'horizon de
2 s, ou σ prédit > 3 m/s. Il n'y a plus de « dernière valeur figée » : l'écran affiche
`--`, et RPM / H1 / suivi d'ordre s'interrompent. Entrer ou sortir du mode direct ouvre une
**nouvelle session** de vitesse : aucune valeur de la session précédente n'est servie avant
le premier fix frais.

### 6.4 Rejeu différé

Le filtre causal reste la vérité du direct. Pour un enregistrement rejoué, un lissage
aller-retour RTS est appliqué **aux fixes horodatés** (jamais aux vitesses déjà
extrapolées trame par trame), puis évalué au temps audio de chaque échantillon. Le statut
(« lissée (RTS) » / « brute (interpolée) » / « causale ») accompagne l'analyse jusqu'au PDF.

---

## 7. Données, exports et traçabilité

- **Enregistrements** (30 s max) : WAV + sidecar JSON, écrits via MediaStore dans
  `Downloads/NVH_Spectro_Exports`, noms horodatés à la milliseconde.
- **Sidecar télémétrie — schéma v3** : `schemaVersion`, `appVersion`, route micro, statut de
  vitesse, `k` de confiance, identité complète de l'estimateur avec ses paramètres ; par
  échantillon : temps monotone, temps audio apparié, vitesse estimée, **σ (null si inconnu,
  jamais 0)**, validité, altitude. Les sidecars v1 et v2 restent lisibles et sont marqués
  « incertitude inconnue ».
- **Réglages** : DataStore — plage dB, taille FFT, fenêtres, détecteur et configuration
  cinématique complète survivent à la mort du processus.
- **Rapport PDF** : cartouche de traçabilité (date/heure, version de build, source avec la
  route micro obtenue, paramètres d'analyse, statut de reconstruction de vitesse et `k`),
  colormaps, ordres validés, commentaires (sauts de ligne préservés), et la note de non-
  conformité normative de l'indice d'émergence.
- **Journal de diagnostic** : local, rotatif, partagé uniquement sur action explicite.

---

## 8. Limites d'emploi connues

1. **Aucune validation terrain n'a encore été faite.** Les constantes de l'estimateur, les
   seuils de validité et les bornes d'identifiabilité sont provisoires jusqu'à la campagne
   GPS-5 (3 téléphones vs vérité terrain synchronisée). Aucune revendication de précision
   ne doit dépasser cette preuve.
2. **Cadence GNSS ~1 Hz** sur la plupart des téléphones : entre deux fixes, toute vitesse
   est une **prédiction de modèle**, pas une mesure.
3. **`speedAccuracy` est un intervalle à 68 %**, pas une borne, et sa calibration varie
   selon le fabricant.
4. **Analyse limitée à 5 minutes** par fichier (bannière explicite au-delà).
5. **Formats WAV** : PCM 16 bits mono/stéréo. 24 bits, flottant et multicanal sont
   explicitement refusés avec un message — jamais décodés en bruit.
6. **dBFS relatif** : pas de calibration acoustique absolue.
7. **Mesure en direct impossible sans le microphone** ; l'analyse de fichiers reste
   disponible.

---

## 9. Qualité, gates et vérification

- **202 tests unitaires** (`:core` + `:app`), couverture `:core` ≥ 90 % imposée par
  `koverVerify` en CI.
- **Snapshot doré** du spectre complet : toute dérive DSP involontaire casse la build.
- **Tests « pinned »** portant un identifiant de constat : ils figent un défaut connu et sont
  remplacés par des tests de comportement corrigé **dans le commit qui corrige le défaut**.
- **Android lint : zéro erreur, sans baseline** ; `HardcodedText`, `ContentDescription`,
  `SetTextI18n`, `StringFormatMatches` promus en erreurs.
- **`ci/checks.sh`** (mêmes gates en local et en CI) : aucun binaire ni script jetable
  versionné, version single-sourcée, aucune fréquence d'échantillonnage littérale hors
  `AudioConfig`, `:core` sans import Android, aucune couleur littérale hors `theme/Color.kt`,
  aucun texte utilisateur en dur dans un fichier Compose, aucune surface WebView/JS, pas de
  permission INTERNET, et non-réapparition du code mort supprimé.
- **Chaque phase se termine par une vérification sur appareil** ; les tests verts ne suffisent
  pas — l'historique du projet l'a prouvé.

---

## 10. Maintenance de ce document

Ce fichier est mis à jour **dans la même session** que tout changement d'architecture,
d'algorithme DSP, de constante métrologique ou de limite d'emploi. Le journal d'exécution
détaillé (étapes, commits, déviations, preuves de gate) vit dans `AAA-TRACKING.md` ; les
constats d'origine dans `V13.1-audit.md` et `audit-gps.md`, gelés comme référence historique.
