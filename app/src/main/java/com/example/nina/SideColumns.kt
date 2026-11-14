package com.example.nina

/**
 * Colonnes latérales masquables (dossiers à gauche, phrases rapides à droite) : la grille seule occupe l'écran,
 * par exemple pour la commande oculaire sur 2 ou 4 grands pictos. Le cadenas reste accessible en haut à droite.
 */
object SideColumns {
    /** Réglage enregistré dans les préférences « nina_prefs ». */
    const val PREF_KEY = "hide_side_columns"

    /**
     * Bouton Retour de la barre du haut. Colonnes affichées : seulement dans un sous-dossier (🏠 Accueil suffit au
     * premier niveau). Colonnes masquées : 🏠 Accueil n'est plus là, il faut aussi pouvoir sortir d'un dossier.
     */
    fun showBackButton(folderDepth: Int, columnsHidden: Boolean): Boolean =
        if (columnsHidden) folderDepth >= 1 else folderDepth >= 2

    /** Le guidage (loupe 🔍) passe par une case des colonnes : dossier de gauche ou phrase rapide. */
    fun guideNeedsColumns(steps: List<AACCell>, coreWords: List<AACCell>, quickFires: List<AACCell>): Boolean {
        val columnIds = (coreWords + quickFires).mapTo(HashSet()) { it.id }
        return steps.any { it.id in columnIds }
    }

    /**
     * Colonnes réellement masquées à l'écran. Si le guidage passe par une colonne, elles réapparaissent
     * le temps du guidage (toutes ses étapes, pour que la grille ne change pas de largeur en cours de route),
     * puis se masquent à nouveau. Le réglage enregistré, lui, ne change pas.
     */
    fun hiddenOnScreen(hiddenSetting: Boolean, steps: List<AACCell>, coreWords: List<AACCell>, quickFires: List<AACCell>): Boolean =
        hiddenSetting && !guideNeedsColumns(steps, coreWords, quickFires)
}
