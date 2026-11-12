package com.example.nina

import androidx.compose.runtime.mutableStateListOf
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contenu des dossiers en JSON : lu et écrit par CellContentAdapter (voir AACCell). */
class CellJsonTest {
    private fun parseList(json: String): List<AACCell> =
        normalizeCells(GsonInstance.gson.fromJson(json, object : TypeToken<List<AACCell>>() {}.type))

    @Test
    fun dossier_allerRetour_lesElementsSontDesPictos() {
        val eau = AACCell("Eau", 1L, imageUrl = "/photos/eau.png", spokenText = "de l'eau")
        val boissons = AACCell("Boissons", 2L, isFolder = true, content = mutableStateListOf(eau))
        val nourriture = AACCell("Nourriture", 3L, isFolder = true, content = mutableStateListOf(boissons))
        val json = GsonInstance.gson.toJson(listOf(nourriture))
        assertTrue(json, json.contains("\"content\":[{")) // Même format qu'avant : un tableau de pictos

        val back = parseList(json)
        val folder = back[0].content[0]
        assertEquals("Boissons", folder.label)
        assertEquals(boissons.id, folder.id)
        val cell = folder.content[0]
        assertEquals("Eau", cell.label)
        assertEquals(eau.id, cell.id)
        assertEquals("de l'eau", cell.spokenText)
        assertEquals("/photos/eau.png", cell.imageUrl)
    }

    @Test
    fun contenuAbsentOuNul_dossierVide() {
        val back = parseList("""[{"label":"A","colorLong":1},{"label":"B","colorLong":2,"isFolder":true,"content":null},{"label":"C","colorLong":3,"content":[]}]""")
        assertEquals(3, back.size)
        assertTrue(back.all { it.content.isEmpty() })
        assertTrue(back.all { it.id.isNotBlank() })
    }
}
