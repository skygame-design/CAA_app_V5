# Règles de réduction du code (R8), en plus de celles livrées par les bibliothèques (Gson, Retrofit, OkHttp, CameraX…).

# Données enregistrées en JSON : Gson lit et écrit les champs par réflexion, leurs noms ne doivent pas changer.
# Les classes concernées sont marquées @Keep dans le code (AACCell, NinaBackup, GazeProfile, réponses des banques d'images…).
# Le contenu d'un dossier (SnapshotStateList<AACCell>) passe par CellContentAdapter (marqué @Keep) : R8 efface la
# signature générique de ce champ, et Gson ne saurait plus que les éléments sont des AACCell.

# Détection du visage (MediaPipe) : appelée depuis le code natif, et ses messages Protobuf sont lus par réflexion.
# On garde tout : la commande oculaire ne peut pas se tester sans tablette, et ces classes pèsent peu à côté
# de la bibliothèque native (14 Mo).
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**

# Numéros de ligne conservés : un rapport de plantage reste lisible (avec le fichier app/build/outputs/mapping/…/mapping.txt).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
