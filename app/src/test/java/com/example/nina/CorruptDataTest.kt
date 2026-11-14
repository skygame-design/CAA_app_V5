package com.example.nina

import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Données abîmées ou d'une autre version : lues sans planter. */
class CorruptDataTest {
    @Test
    fun grille_pictoNulOuSansLibelle_ignoreOuLibelleVide() {
        val cells: List<AACCell> = GsonInstance.gson.fromJson(
            """[null, {"colorLong":1}, {"label":"Dossier","colorLong":2,"isFolder":true,"content":[null,{"label":"Eau","colorLong":3}]}]""",
            object : TypeToken<List<AACCell>>() {}.type
        )
        val clean = normalizeCells(cells)
        assertEquals(2, clean.size)
        assertEquals("", clean[0].label)
        assertEquals(listOf("Eau"), clean[1].content.map { it.label })
        assertTrue(clean.all { it.id.isNotBlank() })
    }

    @Test
    fun sauvegarde_objetVide_grilleVide() {
        val backup = parseBackup("{}")
        assertTrue(backup.buttons.isEmpty())
        assertNull(backup.coreWords)
    }

    private fun profiles(json: String): List<GazeProfile> =
        GsonInstance.gson.fromJson<List<GazeProfile>>(json, object : TypeToken<List<GazeProfile>>() {}.type).mapNotNull { it.validated() }

    @Test
    fun calibration_abimee_ecarteeOuNettoyee() {
        val list = profiles(
            """[
              {"updatedAt":1},
              {"name":"Sans points","updatedAt":1},
              {"name":"Principal","updatedAt":1,
               "samples":[{"features":[1.0,2.0],"targetX":0.1,"targetY":0.2}, null, {"targetX":0.3,"targetY":0.4},
                          {"features":[1.0],"targetX":0.5,"targetY":0.6}],
               "headCompensation":{"yaw0":0.0,"pitch0":0.0}}
            ]"""
        )
        assertEquals(1, list.size)
        val p = list[0]
        assertEquals("Principal", p.name)
        assertEquals(1, p.samples.size) // Point nul, point sans mesures et point de taille différente retirés
        assertNull(p.headCompensation) // Compensation incomplète ignorée
        assertNull(p.model()) // Trop peu de points : pas de calibration, mais pas de plantage
    }

    @Test
    fun calibration_valide_inchangee() {
        val samples = List(6) { GazeSample(doubleArrayOf(it.toDouble(), 1.0, 2.0), it / 10.0, it / 20.0) }
        val profile = GazeProfile("Principal", samples, 1L, null)
        val back = profiles(GsonInstance.gson.toJson(listOf(profile)))
        assertEquals(6, back.single().samples.size)
        assertNotNull(back.single().samples[5].features)
    }
}
