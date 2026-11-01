# CLAUDE.md — Nina (CAA_app_V5)

## Le projet
**Nina** est une application Android de **communication alternative et améliorée (CAA)** pour un enfant : grilles de pictogrammes, construction de phrases, synthèse vocale, mode adulte protégé par un cadenas (maintenir 2 s), commande oculaire expérimentale (caméra frontale).
Cible principale : tablette **Redmi Pad Pro 2** (ARM 64 bits). Le développement a commencé sur PC Windows.

- Dépôt de sauvegarde de la dernière version stable : `CAA_app_V4` (ne pas y toucher).
- Ce dépôt (`CAA_app_V5`) : `main` = copie exacte de V4 ; le travail se fait sur la branche **`dev`**.

## Stack
- Kotlin + **Jetpack Compose** (Material 3), un seul module `:app`
- Package / applicationId : `com.example.nina`
- compileSdk / targetSdk 35, minSdk 26, **JDK 21**
- Coil (images + SVG), Retrofit + Gson, CameraX, MediaPipe Tasks Vision (modèle `app/src/main/assets/face_landmarker.task`)
- Dépendances déclarées dans `app/build.gradle.kts` et le catalogue `gradle/libs.versions.toml`

## Organisation du code (`app/src/main/java/com/example/nina/`)
- `MainActivity.kt`, `MainScreenParts.kt`, `UIComponents.kt`, `DialogParts.kt` : écran principal et composants
- `AACCell.kt`, `CellOperations.kt`, `DragDropLogic.kt` : cellules et grilles
- `NinaStorage.kt`, `AutoBackup.kt`, `BackupDialogs.kt` : stockage et sauvegardes
- `LocalGrammarEngine.kt`, `FrenchMorphology.kt` : grammaire et formes des verbes
- `Gaze*.kt`, `EyeGaze.kt` : commande oculaire
- `PictoSearch*.kt`, `VocabularyPacks.kt` : recherche de pictos et grilles prêtes
- `PrintBoard.kt`, `StatsDialog.kt`, `UsageLog.kt` : planche imprimable, statistiques
- Tests unitaires : `app/src/test/java/com/example/nina/`
- Documentation utilisateur : `docs/`

## Commandes
```bash
./gradlew testDebugUnitTest   # tests unitaires (à lancer avant chaque commit)
./gradlew assembleDebug       # APK de développement
./gradlew assembleRelease     # APK final (signé avec la clé de la tablette)
```
Le SDK Android peut manquer dans l'environnement cloud : si le build échoue pour cette raison, le signaler plutôt que de modifier la configuration Gradle.

## Règles importantes
1. **Ne jamais casser les données existantes** de l'enfant (grilles, photos, calibration, réglages) : toute modification du format de stockage (Gson) doit rester rétro-compatible et être couverte par un test (voir `BackupMigrationTest`).
2. **Ne pas changer** `applicationId`, le nom du projet, ni la configuration de signature (`signingConfigs.tablette`) : sans cela la mise à jour ne s'installe plus par-dessus la version existante.
3. Incrémenter `versionCode` / `versionName` seulement à la préparation d'une version.
4. Pas de réduction de code (R8) pour l'instant : elle peut casser la lecture des données Gson.
5. Aucune donnée personnelle de l'enfant ne doit sortir de la tablette (pas de nouvel envoi réseau sans demande explicite).
6. Interface, textes et commentaires en **français**, avec un vocabulaire simple pour les adultes accompagnants.
7. Faire des changements petits et ciblés ; ajouter ou mettre à jour les tests correspondants.

## Workflow Git
- Travailler sur `dev` (ou une branche dérivée), ouvrir une pull request vers `main` seulement quand la version est stable.
- Messages de commit courts, en français.
