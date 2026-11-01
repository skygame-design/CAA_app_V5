package com.example.nina

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SideColumnsTest {
    @Test
    fun colonnesAffichees_retourSeulementDansUnSousDossier() {
        assertFalse(SideColumns.showBackButton(folderDepth = 0, columnsHidden = false))
        assertFalse(SideColumns.showBackButton(folderDepth = 1, columnsHidden = false)) // 🏠 Accueil suffit
        assertTrue(SideColumns.showBackButton(folderDepth = 2, columnsHidden = false))
    }

    @Test
    fun colonnesMasquees_retourDesQuOnEstDansUnDossier() {
        assertFalse(SideColumns.showBackButton(folderDepth = 0, columnsHidden = true)) // Déjà à l'accueil
        assertTrue(SideColumns.showBackButton(folderDepth = 1, columnsHidden = true)) // Plus de 🏠 Accueil : seul moyen de revenir
        assertTrue(SideColumns.showBackButton(folderDepth = 3, columnsHidden = true))
    }
}
