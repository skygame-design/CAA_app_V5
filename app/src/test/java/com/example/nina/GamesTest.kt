package com.example.nina

import androidx.compose.runtime.mutableStateListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

class GamesSessionTest {
    @Test
    fun minuterie_tempsRestantEtFin() {
        val s = GamesSession.start(10, now = 1_000L)
        assertEquals(600_000L, s.remainingMs(1_000L))
        assertEquals(0.5f, s.fractionLeft(301_000L)!!, 1e-4f)
        assertFalse(s.isOver(600_999L))
        assertTrue(s.isOver(601_000L))
        assertEquals(0L, s.remainingMs(10_000_000L)) // Jamais négatif
    }

    @Test
    fun sansLimite_neFinitJamais() {
        val s = GamesSession.start(0, now = 0L)
        assertNull(s.durationMs)
        assertNull(s.fractionLeft(1_000_000L))
        assertFalse(s.isOver(Long.MAX_VALUE / 2))
        assertFalse(s.shouldWarn(1_000_000L))
    }

    @Test
    fun encoreUneMinute_seulementDansLaDerniereMinute() {
        val s = GamesSession.start(5, now = 0L)
        assertFalse(s.shouldWarn(3 * 60_000L)) // Il reste 2 minutes
        assertTrue(s.shouldWarn(4 * 60_000L + 1)) // Il reste moins d'une minute
        assertFalse(s.shouldWarn(5 * 60_000L)) // C'est fini : plus d'avertissement
        // Séance de 2 minutes ou moins : pas d'avertissement (on vient de commencer)
        assertFalse(GamesSession(0L, 2 * 60_000L).shouldWarn(90_000L))
    }
}

class GameTonesTest {
    @Test
    fun chaqueSon_aUneDureeRaisonnableEtFinitEnDouceur() {
        GameSound.entries.forEach { sound ->
            val pcm = GameTones.samples(sound)
            val seconds = pcm.size.toDouble() / GameTones.SAMPLE_RATE
            assertTrue("$sound : $seconds s", seconds in 0.05..1.0)
            assertTrue("$sound : pas de silence complet", pcm.any { kotlin.math.abs(it.toInt()) > 1000 })
            // Pas de clic : le son commence et finit près de zéro
            assertTrue("$sound début", kotlin.math.abs(pcm.first().toInt()) < 500)
            assertTrue("$sound fin", kotlin.math.abs(pcm.last().toInt()) < 500)
        }
    }

    @Test
    fun wav_enTeteCorrect() {
        val pcm = shortArrayOf(0, 1000, -1000, 0)
        val wav = GameTones.wav(pcm, 22050)
        assertEquals(44 + 8, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + 8, b.getInt(4))
        assertEquals(1.toShort(), b.getShort(22)) // mono
        assertEquals(22050, b.getInt(24))
        assertEquals(16.toShort(), b.getShort(34))
        assertEquals(8, b.getInt(40))
        assertEquals(1000.toShort(), b.getShort(46))
    }
}

class BubbleFieldTest {
    @Test
    fun nombreDeBullesConstant_etToujoursDansLEcran() {
        val field = BubbleField(Random(1), aspect = 1.6)
        repeat(3000) { // 60 s à 50 images par seconde
            field.step(0.02)
            val bubbles = field.current
            assertEquals(5, bubbles.size)
            assertEquals((0 until 5).toList(), bubbles.map { it.slot }) // Les places ne changent jamais
            bubbles.forEach { b ->
                assertTrue("haut", b.y - b.radius > 0)
                assertTrue("bas", b.y + b.radius <= 1.0 + 1e-9)
                assertTrue("gauche", b.x - b.radiusX(1.6) >= -1e-9)
                assertTrue("droite", b.x + b.radiusX(1.6) <= 1.0 + 1e-9)
            }
        }
    }

    @Test
    fun lesBullesMontent_lentement() {
        val field = BubbleField(Random(2), aspect = 1.6)
        val before = field.current
        field.step(1.0)
        field.current.zip(before).forEach { (after, b) ->
            if (after.age > b.age) { // Pas renée entre-temps
                val climbed = b.y - after.y
                assertTrue("vitesse $climbed", climbed in 0.04..0.09) // 11 à 25 s pour traverser l'écran
            }
        }
    }

    @Test
    fun eclater_faitRenaitreEnBas_etPasPendantLApparition() {
        val field = BubbleField(Random(3), aspect = 1.6)
        val popped = field.pop(2)!!
        assertEquals(2, popped.slot)
        val reborn = field.current[2]
        assertEquals(1.0 - reborn.radius, reborn.y, 1e-9) // En bas de l'écran
        assertEquals(0.0, reborn.scale, 1e-9)
        assertNull(field.pop(2)) // Encore en train d'apparaître : on ne peut pas l'éclater tout de suite
        field.step(0.3)
        assertTrue(field.pop(2) != null)
        assertNull(field.pop(9))
    }
}

class MemoryGameTest {
    @Test
    fun paires_etCartesMelangees() {
        val board = MemoryBoard(4, Random(1))
        assertEquals(8, board.cards.size)
        assertEquals(List(4) { 2 }, board.cards.groupBy { it.pairId }.values.map { it.size })
        assertFalse(board.isWon)
    }

    private fun MemoryBoard.indicesOf(pair: Int) = cards.filter { it.pairId == pair }.map { it.index }

    @Test
    fun pairesTrouvees_jusquALaVictoire() {
        val board = MemoryBoard(2, Random(5))
        val (a0, a1) = board.indicesOf(0)
        assertEquals(MemoryBoard.Flip.FIRST, board.flip(a0))
        assertEquals(MemoryBoard.Flip.IGNORED, board.flip(a0)) // Déjà retournée
        assertEquals(MemoryBoard.Flip.MATCH, board.flip(a1))
        val (b0, b1) = board.indicesOf(1)
        assertEquals(MemoryBoard.Flip.FIRST, board.flip(b0))
        assertEquals(MemoryBoard.Flip.WON, board.flip(b1))
        assertTrue(board.isWon)
        assertEquals(MemoryBoard.Flip.IGNORED, board.flip(a0)) // Carte trouvée
    }

    @Test
    fun erreur_onAttendQueLesCartesSeRecachent() {
        val board = MemoryBoard(3, Random(9))
        val a = board.indicesOf(0).first(); val b = board.indicesOf(1).first(); val c = board.indicesOf(2).first()
        board.flip(a)
        assertEquals(MemoryBoard.Flip.MISMATCH, board.flip(b))
        assertTrue(board.waitingToHide)
        assertEquals(MemoryBoard.Flip.IGNORED, board.flip(c)) // Pas de troisième carte
        board.hideMismatched()
        assertFalse(board.waitingToHide)
        assertTrue(board.cards.none { it.faceUp })
        assertEquals(MemoryBoard.Flip.FIRST, board.flip(c))
    }

    @Test
    fun cartesTrouvees_restentVisiblesApresUneErreur() {
        val board = MemoryBoard(3, Random(4))
        board.indicesOf(0).forEach { board.flip(it) }
        board.flip(board.indicesOf(1).first()); board.flip(board.indicesOf(2).first())
        board.hideMismatched()
        assertTrue(board.cards.filter { it.pairId == 0 }.all { it.faceUp && it.matched })
    }

    @Test
    fun colonnes_lesCartesTiennentToutes() {
        for (n in listOf(4, 6, 8, 10, 12)) {
            val cols = MemoryBoard.bestColumns(n, 1.6)
            val rows = (n + cols - 1) / cols
            assertTrue("$n cartes : $cols colonnes", rows * cols >= n && rows <= 3)
        }
        assertEquals(4, MemoryBoard.bestColumns(8, 1.6)) // 4 × 2 sur une tablette en paysage
    }

    private val chat = AACCell("Chat", 0L, imageUrl = "/photos/chat.png")
    private val chien = AACCell("Chien", 0L, imageUrl = "https://exemple/chien.svg")
    private val sansImage = AACCell("Encore", 0L)
    private val enAttente = AACCell("Pomme", 0L, imageUrl = "FETCH")
    private val cache = AACCell("Secret", 0L, imageUrl = "/photos/secret.png", isHidden = true)
    private val doublon = AACCell("chat ", 0L, imageUrl = "/photos/chat2.png")
    private val dossier = AACCell("Animaux", 0L, imageUrl = "/photos/animaux.png", isFolder = true, content = mutableStateListOf(chien, doublon))

    @Test
    fun pictosJouables_avecImage_visibles_dansLesDossiers_sansDoublon() {
        val pictos = playablePictos(listOf(chat, sansImage, enAttente, cache, dossier))
        assertEquals(listOf("Chat", "Chien"), pictos.map { it.label })
    }

    @Test
    fun faces_pictosSiAssez_sinonFormes() {
        val pictos = listOf(chat, chien)
        val faces = memoryFaces(pictos, 2, Random(1))
        assertTrue(faces.all { it is MemoryFace.Picto })
        assertEquals(setOf("Chat", "Chien"), faces.map { it.spoken }.toSet())

        val formes = memoryFaces(pictos, 6, Random(1))
        assertEquals(6, formes.size)
        assertTrue(formes.all { it is MemoryFace.Forme })
        assertEquals(6, formes.toSet().size) // Toutes différentes
    }
}

class FindGameTest {
    @Test
    fun manche_cibleUnique_etOptionsDifferentes() {
        val random = Random(11)
        repeat(200) {
            for (kind in FindKind.entries) for (n in 2..4) {
                val round = FindRounds.next(kind, n, random)
                assertEquals(n, round.options.size)
                if (kind == FindKind.COULEUR) {
                    assertEquals(n, round.options.map { it.color }.toSet().size)
                    assertEquals(1, round.options.map { it.shape }.toSet().size)
                } else {
                    assertEquals(n, round.options.map { it.shape }.toSet().size)
                    assertEquals(1, round.options.map { it.color }.toSet().size)
                }
            }
        }
    }

    @Test
    fun jamaisDeuxFoisLaMemeReponse() {
        val random = Random(3)
        var previous = FindRounds.next(FindKind.COULEUR, 2, random)
        repeat(300) {
            val round = FindRounds.next(FindKind.COULEUR, 2, random, previous)
            assertNotEquals(previous.target.color, round.target.color)
            previous = round
        }
    }

    @Test
    fun consignesParlees() {
        val couleur = FindRound(FindKind.COULEUR, listOf(FindOption(GameShape.ROND, GameColor.ROUGE), FindOption(GameShape.ROND, GameColor.BLEU)), 0)
        assertEquals("Montre le rouge", couleur.instruction)
        assertEquals("Bravo, c'est le rouge !", couleur.praise)
        val forme = FindRound(FindKind.FORME, listOf(FindOption(GameShape.CARRE, GameColor.VERT), FindOption(GameShape.ETOILE, GameColor.VERT)), 1)
        assertEquals("Montre l'étoile", forme.instruction)
    }

    @Test
    fun difficulte_uneCibleDePlusApres3Reussites_maximum4() {
        val p = FindProgress()
        assertEquals(2, p.optionCount)
        p.success(); p.success(); p.error(); p.success(); p.success()
        assertEquals(2, p.optionCount) // L'erreur a remis la série à zéro
        p.success()
        assertEquals(3, p.optionCount)
        repeat(20) { p.success() }
        assertEquals(4, p.optionCount)
    }
}

class ColoringTest {
    @Test
    fun dessins_partiesDansLeCadre_etAssezGrandesPourLeRegard() {
        ColoringPictures.all.forEach { picture ->
            assertTrue(picture.name, picture.regions.size >= 5)
            assertEquals(picture.regions.size, picture.regions.map { it.id }.toSet().size)
            picture.regions.forEach { region ->
                val b = region.shape.bounds
                assertTrue("${region.id} dans le cadre", b.left >= 0 && b.top >= 0 && b.right <= 1 && b.bottom <= 1)
                assertTrue("${region.id} assez grande", b.width >= 0.07 && b.height >= 0.07)
            }
        }
    }

    @Test
    fun dessins_ciblesQuiNeSeChevauchentPas() {
        ColoringPictures.all.forEach { picture ->
            val regions = picture.regions
            for (i in regions.indices) for (j in i + 1 until regions.size) {
                val a = regions[i].shape.bounds; val b = regions[j].shape.bounds
                val overlapW = minOf(a.right, b.right) - maxOf(a.left, b.left)
                val overlapH = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
                assertFalse("${regions[i].id} / ${regions[j].id}", overlapW > 1e-6 && overlapH > 1e-6)
            }
        }
    }

    @Test
    fun identifiantsUniquesEntreLesDessins() {
        val ids = ColoringPictures.all.flatMap { p -> p.regions.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
    }
}

class GamesSettingsTest {
    @Test
    fun enregistrementPuisLecture_redonneLesMemesReglages() {
        val settings = GamesSettings(
            games = setOf(GameKind.BULLES, GameKind.COLORIAGE),
            bubbleSpeed = BubbleSpeed.LENTE, bubbleSize = BubbleSize.TRES_GRANDE, bubbleCount = 3,
            memoryStartPairs = 4, memoryProgressive = false, memoryUsePictos = false,
            findStartChoices = 3, findProgressive = false,
            findColors = setOf(GameColor.ROUGE, GameColor.BLEU), findShapes = setOf(GameShape.ROND, GameShape.ETOILE),
            coloringColors = setOf("rouge", "jaune"), coloringChooseColor = true, coloringPictures = setOf("poisson")
        )
        val stored = settings.toMap()
        assertEquals(settings, GamesSettings.fromMap { stored[it] })
    }

    @Test
    fun reglagesAbsentsOuIllisibles_valeursParDefaut() {
        assertEquals(GamesSettings(), GamesSettings.fromMap { null })
        val broken = mapOf(
            "games_enabled" to "INCONNU", "games_bubble_speed" to "TURBO", "games_bubble_count" to "beaucoup",
            "games_memory_progressive" to "peut-être", "games_find_colors" to "ROUGE", "games_coloring_pictures" to "dragon"
        )
        val read = GamesSettings.fromMap { broken[it] }
        assertEquals(GameKind.entries.toSet(), read.games) // Aucun jeu : tous
        assertEquals(BubbleSpeed.NORMALE, read.bubbleSpeed)
        assertEquals(5, read.bubbleCount)
        assertTrue(read.memoryProgressive)
        assertEquals(GameColor.entries.toSet(), read.findColors) // Une seule couleur : pas jouable, toutes
        assertEquals(ColoringPictures.all.map { it.name }.toSet(), read.coloringPictures)
    }

    @Test
    fun valeursRameneesDansLesLimites() {
        val s = GamesSettings(bubbleCount = 50, memoryStartPairs = 1, findStartChoices = 9, coloringColors = emptySet()).normalized()
        assertEquals(8, s.bubbleCount)
        assertEquals(2, s.memoryStartPairs)
        assertEquals(4, s.findStartChoices)
        assertEquals(ColoringPalette, s.coloringPalette)
    }

    @Test
    fun progression_desactivee_resteAuDepart() {
        assertEquals(6, GamesSettings(memoryStartPairs = 3).memoryMaxPairs)
        assertEquals(3, GamesSettings(memoryStartPairs = 3, memoryProgressive = false).memoryMaxPairs)
        assertEquals(2, GamesSettings(findProgressive = false).findMaxChoices)
        val p = FindProgress(3, maxCount = 3)
        repeat(10) { p.success() }
        assertEquals(3, p.optionCount)
    }

    @Test
    fun menu_seulementLesJeuxChoisis_dansLOrdre() {
        assertEquals(listOf(GameKind.BULLES, GameKind.COLORIAGE), GamesSettings(games = setOf(GameKind.COLORIAGE, GameKind.BULLES)).menuGames)
    }

    @Test
    fun couleursEtFormes_seulementCellesChoisies() {
        val colors = listOf(GameColor.ROUGE, GameColor.BLEU)
        val shapes = listOf(GameShape.ROND, GameShape.COEUR, GameShape.CARRE)
        val random = Random(8)
        repeat(100) {
            val c = FindRounds.next(FindKind.COULEUR, 4, random, colors = colors, shapes = shapes)
            assertEquals(2, c.options.size) // 4 demandés, mais seulement 2 couleurs choisies
            assertTrue(c.options.all { it.color in colors && it.shape in shapes })
            val f = FindRounds.next(FindKind.FORME, 4, random, colors = colors, shapes = shapes)
            assertEquals(3, f.options.size)
            assertTrue(f.options.all { it.color in colors && it.shape in shapes })
        }
    }

    @Test
    fun bulles_nombreTailleEtVitesseReglables() {
        val size = BubbleSize.TRES_GRANDE
        val field = BubbleField(Random(4), aspect = 1.6, slots = 3, minRadius = size.minRadius, maxRadius = size.maxRadius,
            minSpeed = 0.05 * BubbleSpeed.LENTE.factor, maxSpeed = 0.08 * BubbleSpeed.LENTE.factor)
        repeat(500) { field.step(0.02) }
        assertEquals(3, field.current.size)
        assertTrue(field.current.all { it.radius in size.minRadius..size.maxRadius && it.speed <= 0.08 * 0.6 + 1e-9 })
    }
}
