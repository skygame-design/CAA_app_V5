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
}
