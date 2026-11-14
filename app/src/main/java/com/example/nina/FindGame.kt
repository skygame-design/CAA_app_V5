package com.example.nina

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

/*
 * Trouve la couleur / la forme : « Montre le rouge », « Montre l'étoile ». De 2 à 4 grandes cibles.
 * Une erreur n'est jamais punie : petit son doux, « Essaie encore », et après deux erreurs la bonne réponse
 * se met à pulser (apprentissage sans échec). Trois réussites d'affilée ajoutent une cible.
 * « Montre » convient au doigt comme au regard.
 */

enum class FindKind { COULEUR, FORME }

data class FindOption(val shape: GameShape, val color: GameColor)

data class FindRound(val kind: FindKind, val options: List<FindOption>, val answer: Int) {
    val target: FindOption get() = options[answer]
    private val targetName: String get() = if (kind == FindKind.COULEUR) target.color.spoken else target.shape.spoken
    val instruction: String get() = "Montre $targetName"
    val praise: String get() = "Bravo, c'est $targetName !"
}

object FindRounds {
    /**
     * Nouvelle manche : couleurs = même forme en couleurs différentes ; formes = même couleur, formes différentes.
     * La réponse n'est jamais la même que celle de la manche précédente.
     */
    fun next(
        kind: FindKind, optionCount: Int, random: Random, previous: FindRound? = null,
        colors: List<GameColor> = GameColor.entries, shapes: List<GameShape> = GameShape.entries // Choisies dans les réglages
    ): FindRound {
        val options = when (kind) {
            FindKind.COULEUR -> {
                val shape = shapes.random(random)
                colors.shuffled(random).take(optionCount.coerceIn(2, 4).coerceAtMost(colors.size)).map { FindOption(shape, it) }
            }
            FindKind.FORME -> {
                val color = colors.random(random)
                shapes.shuffled(random).take(optionCount.coerceIn(2, 4).coerceAtMost(shapes.size)).map { FindOption(it, color) }
            }
        }
        val n = options.size
        var answer = random.nextInt(n)
        if (previous != null && previous.kind == kind && sameTarget(kind, options[answer], previous.target)) answer = (answer + 1) % n
        return FindRound(kind, options, answer)
    }

    private fun sameTarget(kind: FindKind, a: FindOption, b: FindOption) =
        if (kind == FindKind.COULEUR) a.color == b.color else a.shape == b.shape
}

/** Difficulté : 2 cibles au départ, une de plus après 3 réussites d'affilée (jusqu'à maxCount, 4 au plus). */
class FindProgress(optionCount: Int = 2, private val maxCount: Int = 4) {
    var optionCount = optionCount
        private set
    private var streak = 0

    fun success() {
        streak++
        if (streak >= 3 && optionCount < maxCount.coerceAtMost(4)) { optionCount++; streak = 0 }
    }

    fun error() { streak = 0 }
}

@Composable
fun FindGame(kind: FindKind, fx: GameFx, textColor: Color, settings: GamesSettings = GamesSettings()) {
    val random = remember { Random(System.nanoTime()) }
    val progress = remember { FindProgress(settings.findStartChoices, settings.findMaxChoices) }
    val colors = remember { settings.findColors.sortedBy { it.ordinal } }
    val shapes = remember { settings.findShapes.sortedBy { it.ordinal } }
    var round by remember { mutableStateOf(FindRounds.next(kind, progress.optionCount, random, colors = colors, shapes = shapes)) }
    var errors by remember(round) { mutableIntStateOf(0) }
    var solved by remember(round) { mutableStateOf(false) }
    var shakeIndex by remember(round) { mutableStateOf<Int?>(null) }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(round) { delay(500); fx.speak(round.instruction) }
    LaunchedEffect(solved) {
        if (solved) { delay(2600); round = FindRounds.next(kind, progress.optionCount, random, round, colors, shapes) }
    }
    // La forme choisie à tort tremble un peu
    LaunchedEffect(shakeIndex, errors) {
        if (shakeIndex != null) {
            shake.snapTo(0f)
            listOf(18f, -18f, 12f, -12f, 6f, 0f).forEach { shake.animateTo(it, tween(50)) }
        }
    }
    val pulse by rememberInfiniteTransition(label = "indice")
        .animateFloat(1f, 1.12f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulsation")

    val choose: (Int) -> Unit = { i ->
        if (!solved) {
            if (i == round.answer) {
                solved = true
                progress.success()
                fx.celebrate(round.praise)
            } else {
                errors++
                progress.error()
                shakeIndex = i
                fx.sounds.play(GameSound.TRY_AGAIN)
                fx.speak("Essaie encore. ${round.instruction}")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                round.instruction, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = textColor,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
            )
            // Réécouter la consigne
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4A5D91))
                    .gameTarget("consigne") { fx.speak(round.instruction) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Réécouter la consigne", tint = Color.White, modifier = Modifier.size(52.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            round.options.forEachIndexed { i, option ->
                val hint = errors >= 2 && i == round.answer && !solved
                val dimmed = solved && i != round.answer
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.85f)
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .graphicsLayer {
                                translationX = if (shakeIndex == i) shake.value * density else 0f
                                val s = if (hint) pulse else 1f
                                scaleX = s; scaleY = s
                                alpha = if (dimmed) 0.25f else 1f
                            }
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White)
                            .gameTarget("choix_$i") { choose(i) }
                            .padding(16.dp)
                    ) {
                        ShapeIcon(option.shape, Color(option.color.argb), Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
