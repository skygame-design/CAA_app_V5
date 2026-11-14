package com.example.nina

import androidx.compose.runtime.mutableStateListOf
import org.junit.Assert.assertEquals
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

    private val eau = AACCell("Eau", 0L)
    private val boissons = AACCell("Boissons", 0L, isFolder = true, content = mutableStateListOf(eau))
    private val manger = AACCell("Manger", 0L)
    private val merci = AACCell("Merci", 0L, isQuickPhrase = true)
    private val coreWords = listOf(boissons)
    private val quickFires = listOf(merci)

    @Test
    fun guidageParLaColonneDeGauche_colonnesReaffichees() {
        val steps = listOf(boissons, eau)
        assertTrue(SideColumns.guideNeedsColumns(steps, coreWords, quickFires))
        assertFalse(SideColumns.hiddenOnScreen(true, steps, coreWords, quickFires))
    }

    @Test
    fun guidageVersUnePhraseRapide_colonnesReaffichees() {
        assertFalse(SideColumns.hiddenOnScreen(true, listOf(merci), coreWords, quickFires))
    }

    @Test
    fun guidageDansLaGrilleSeulement_colonnesRestentMasquees() {
        assertFalse(SideColumns.guideNeedsColumns(listOf(manger), coreWords, quickFires))
        assertTrue(SideColumns.hiddenOnScreen(true, listOf(manger), coreWords, quickFires))
    }

    @Test
    fun sansGuidage_leReglageSApplique() {
        assertTrue(SideColumns.hiddenOnScreen(true, emptyList(), coreWords, quickFires))
        assertFalse(SideColumns.hiddenOnScreen(false, emptyList(), coreWords, quickFires))
        // Colonnes affichées : le guidage ne les masque jamais
        assertFalse(SideColumns.hiddenOnScreen(false, listOf(manger), coreWords, quickFires))
    }
}

class TouchCorrectionTest {
    @Test
    fun medianeDesEcarts_unTouchRateNeFaussePasTout() {
        // 4 touchers 10 dp à droite, un toucher complètement à côté
        assertEquals(10f, touchCorrection(listOf(10f, 9f, 300f, 11f, 10f)), 0.001f)
    }

    @Test
    fun ecartBorne_etAucuneMesure() {
        assertEquals(48f, touchCorrection(listOf(100f, 120f, 90f)), 0.001f)
        assertEquals(-48f, touchCorrection(listOf(-100f, -120f, -90f)), 0.001f)
        assertEquals(0f, touchCorrection(emptyList()), 0.001f)
    }
}
