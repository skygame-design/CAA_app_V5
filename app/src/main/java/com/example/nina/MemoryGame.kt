package com.example.nina

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.random.Random

/*
 * Memory : retrouver les paires. Les cartes montrent les pictos de l'enfant (le mot est dit à chaque carte
 * retournée), ou des formes de couleur s'il n'y a pas assez de pictos avec image.
 * On commence avec 2 paires ; chaque partie gagnée en ajoute une (jusqu'à 6).
 */

/** Ce qu'il y a sur une carte. */
sealed class MemoryFace {
    abstract val spoken: String

    data class Picto(val cell: AACCell) : MemoryFace() {
        override val spoken get() = cell.label
        val imageModel: Any? get() = cell.imageUrl?.takeIf { it.isNotEmpty() && it != "FETCH" } ?: cell.imageRes
    }

    data class Forme(val shape: GameShape, val color: GameColor) : MemoryFace() {
        override val spoken get() = "${shape.label} ${color.label}"
    }
}

/** Pictos utilisables dans les jeux : avec une image, visibles, pas des dossiers (leur contenu est parcouru), sans doublon. */
fun playablePictos(cells: List<AACCell>): List<AACCell> {
    val result = ArrayList<AACCell>()
    fun visit(list: List<AACCell>) {
        list.forEach { cell ->
            if (cell.isFolder) visit(cell.content)
            else if (!cell.isHidden && cell.label.isNotBlank() &&
                ((!cell.imageUrl.isNullOrEmpty() && cell.imageUrl != "FETCH") || cell.imageRes != null)) result.add(cell)
        }
    }
    visit(cells)
    return result.distinctBy { it.label.trim().lowercase() }
}

/** Faces d'une partie : des pictos s'il y en a assez, sinon des formes de couleur (toutes différentes). */
fun memoryFaces(pictos: List<AACCell>, pairs: Int, random: Random): List<MemoryFace> =
    if (pictos.size >= pairs) pictos.shuffled(random).take(pairs).map { MemoryFace.Picto(it) }
    else GameShape.entries.flatMap { s -> GameColor.entries.map { c -> MemoryFace.Forme(s, c) } }.shuffled(random).take(pairs)

data class MemoryCard(val index: Int, val pairId: Int, val faceUp: Boolean = false, val matched: Boolean = false)

/** Règles du memory, sans dessin (testable). */
class MemoryBoard(val pairs: Int, random: Random) {
    enum class Flip { IGNORED, FIRST, MATCH, MISMATCH, WON }

    private val _cards = (0 until pairs).flatMap { listOf(it, it) }.shuffled(random)
        .mapIndexed { i, pair -> MemoryCard(i, pair) }.toMutableList()

    val cards: List<MemoryCard> get() = _cards.toList()
    val isWon: Boolean get() = _cards.all { it.matched }

    /** Deux cartes différentes sont retournées : on attend qu'elles se recachent avant d'en retourner une autre. */
    val waitingToHide: Boolean get() = _cards.count { it.faceUp && !it.matched } >= 2

    fun flip(index: Int): Flip {
        val card = _cards.getOrNull(index) ?: return Flip.IGNORED
        if (card.faceUp || card.matched || waitingToHide) return Flip.IGNORED
        _cards[index] = card.copy(faceUp = true)
        val open = _cards.filter { it.faceUp && !it.matched }
        if (open.size < 2) return Flip.FIRST
        val (a, b) = open
        if (a.pairId != b.pairId) return Flip.MISMATCH
        listOf(a, b).forEach { _cards[it.index] = it.copy(matched = true) }
        return if (isWon) Flip.WON else Flip.MATCH
    }

    fun hideMismatched() {
        _cards.replaceAll { if (it.faceUp && !it.matched) it.copy(faceUp = false) else it }
    }

    companion object {
        const val MAX_PAIRS = 6
        private const val CARD_RATIO = 1.2 // hauteur / largeur d'une carte

        /** Nombre de colonnes qui donne les plus grandes cartes (aspect = largeur / hauteur de l'aire de jeu). */
        fun bestColumns(cardCount: Int, aspect: Double): Int = (1..cardCount).maxBy { cols ->
            val rows = (cardCount + cols - 1) / cols
            minOf(aspect / cols, 1.0 / rows / CARD_RATIO)
        }
    }
}

@Composable
fun MemoryGame(pictos: List<AACCell>, fx: GameFx, settings: GamesSettings = GamesSettings()) {
    val random = remember { Random(System.nanoTime()) }
    var pairs by remember { mutableIntStateOf(settings.memoryStartPairs) }
    // Réglage « formes de couleur » : pas de pictos
    val faceSource = remember { if (settings.memoryUsePictos) pictos else emptyList() }
    var round by remember { mutableIntStateOf(0) }
    val faces = remember(round) { memoryFaces(faceSource, pairs, random) }
    val board = remember(round) { MemoryBoard(pairs, random) }
    var cards by remember(round) { mutableStateOf(board.cards) }
    var won by remember(round) { mutableStateOf(false) }

    // Deux cartes différentes : on les laisse voir un moment, puis elles se recachent
    LaunchedEffect(cards) {
        if (board.waitingToHide) { delay(1400); board.hideMismatched(); cards = board.cards }
    }
    // Partie gagnée : bravo, puis une paire de plus
    LaunchedEffect(won) {
        if (won) {
            delay(900)
            fx.celebrate("Bravo !")
            delay(2600)
            pairs = (pairs + 1).coerceAtMost(settings.memoryMaxPairs)
            round++
        }
    }

    val flip: (Int) -> Unit = { i ->
        val face = faces[cards[i].pairId]
        when (board.flip(i)) {
            MemoryBoard.Flip.IGNORED -> {}
            MemoryBoard.Flip.FIRST, MemoryBoard.Flip.MISMATCH -> { fx.sounds.play(GameSound.TAP); fx.speak(face.spoken) }
            MemoryBoard.Flip.MATCH -> { fx.sounds.play(GameSound.SUCCESS); fx.speak(face.spoken) }
            MemoryBoard.Flip.WON -> { fx.sounds.play(GameSound.SUCCESS); fx.speak(face.spoken); won = true }
        }
        cards = board.cards
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        val count = cards.size
        val cols = MemoryBoard.bestColumns(count, (maxWidth / maxHeight).toDouble())
        val rows = (count + cols - 1) / cols
        Column(modifier = Modifier.fillMaxSize().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            for (r in 0 until rows) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    for (c in 0 until cols) {
                        val i = r * cols + c
                        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            if (i < count) {
                                val card = cards[i]
                                MemoryCardView(
                                    card, faces[card.pairId],
                                    Modifier
                                        .fillMaxHeight()
                                        .aspectRatio(0.83f, matchHeightConstraintsFirst = true)
                                        .gameTarget("carte_$i", enabled = !card.matched) { flip(i) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryCardView(card: MemoryCard, face: MemoryFace, modifier: Modifier) {
    val rotation by animateFloatAsState(if (card.faceUp) 180f else 0f, tween(350), label = "carte")
    val shape = RoundedCornerShape(20.dp)
    Box(modifier = modifier.graphicsLayer { rotationY = rotation; cameraDistance = 16f * density }) {
        if (rotation <= 90f) {
            // Dos de la carte
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(Color(0xFF7986CB), Color(0xFF3949AB))))
                    .border(4.dp, Color.White, shape),
                contentAlignment = Alignment.Center
            ) {
                ShapeIcon(GameShape.ETOILE, Color(0xFFFFD54F), Modifier.fillMaxSize(0.45f))
            }
        } else {
            // Face (retournée à nouveau pour ne pas être vue en miroir)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .clip(shape)
                    .background(Color.White)
                    .border(if (card.matched) 6.dp else 4.dp, if (card.matched) Color(0xFF43A047) else Color(0xFFB0BEC5), shape)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                when (face) {
                    is MemoryFace.Picto -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            model = face.imageModel, contentDescription = face.cell.label, contentScale = ContentScale.Fit,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                        Text(
                            face.cell.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF263238),
                            maxLines = 1, textAlign = TextAlign.Center
                        )
                    }
                    is MemoryFace.Forme -> ShapeIcon(face.shape, Color(face.color.argb), Modifier.fillMaxSize(0.8f))
                }
            }
        }
    }
}
