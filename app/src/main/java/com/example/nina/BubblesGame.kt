package com.example.nina

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * Bulles à éclater (cause-effet) : de grosses bulles montent lentement, on les éclate au doigt ou du regard.
 * Pas d'échec possible. Le nombre de bulles est fixe (des « places ») : une bulle éclatée ou sortie par le haut
 * renaît aussitôt en bas, dans la même place. Pour la commande oculaire, la liste des cibles ne change donc
 * jamais, et l'anneau de fixation n'est pas remis à zéro à chaque apparition.
 */

/** Une bulle : centre en fractions de l'aire de jeu (x en largeur, y en hauteur), rayon en fraction de la hauteur. */
data class Bubble(
    val slot: Int, val x: Double, val y: Double, val radius: Double,
    val colorIndex: Int, val speed: Double, val phase: Double, val age: Double = 0.0
) {
    /** Rayon en fraction de la largeur (la bulle reste ronde). */
    fun radiusX(aspect: Double) = radius / aspect
    /** Petite oscillation horizontale : la bulle « flotte ». */
    fun displayX(aspect: Double) = x + 0.015 * sin(age * 1.4 + phase) / aspect
    /** Apparition en 0,4 s. */
    val scale: Double get() = (age / 0.4).coerceIn(0.0, 1.0)
    /** S'efface en approchant du haut. */
    val alpha: Double get() = ((y - radius) / 0.12).coerceIn(0.0, 1.0)
}

/**
 * Les bulles et leur mouvement, sans dessin (testable). aspect = largeur / hauteur de l'aire de jeu.
 * Vitesse en hauteurs par seconde : 0,05 à 0,08, soit 12 à 20 s pour traverser l'écran (suivi du regard possible).
 */
class BubbleField(
    private val random: Random,
    val aspect: Double,
    val slots: Int = 5,
    private val minRadius: Double = 0.10,
    private val maxRadius: Double = 0.14,
    private val minSpeed: Double = 0.05,
    private val maxSpeed: Double = 0.08,
    private val colorCount: Int = GameColor.entries.size
) {
    private val bubbles = ArrayList<Bubble>(slots).also { list -> repeat(slots) { list.add(spawn(it, initial = true, others = list)) } }

    val current: List<Bubble> get() = bubbles.toList()

    private fun spawn(slot: Int, initial: Boolean, others: List<Bubble>): Bubble {
        val r = minRadius + random.nextDouble() * (maxRadius - minRadius)
        val rx = r / aspect
        // Au départ, les bulles sont réparties sur toute la hauteur ; ensuite elles naissent en bas
        val y = if (initial) r + 0.15 + random.nextDouble() * (1 - 2 * r - 0.15).coerceAtLeast(0.0) else 1 - r
        // Place la plus éloignée des bulles voisines (même hauteur)
        val near = others.filter { abs(it.y - y) < it.radius + r + 0.05 }
        val x = (0 until 12).map { rx + random.nextDouble() * (1 - 2 * rx).coerceAtLeast(0.0) }
            .maxBy { cx -> near.minOfOrNull { abs(it.x - cx) - it.radiusX(aspect) - rx } ?: 1.0 }
        return Bubble(
            slot, x, y, r, random.nextInt(colorCount),
            minSpeed + random.nextDouble() * (maxSpeed - minSpeed), random.nextDouble() * 2 * PI,
            age = if (initial) 1.0 else 0.0
        )
    }

    /** Avance de dt secondes ; une bulle qui touche le haut renaît en bas. */
    fun step(dt: Double) {
        for (i in bubbles.indices) {
            val b = bubbles[i]
            val moved = b.copy(y = b.y - b.speed * dt, age = b.age + dt)
            bubbles[i] = if (moved.y - moved.radius <= 0) spawn(i, initial = false, others = bubbles) else moved
        }
    }

    /** Éclate la bulle de cette place (sauf si elle est encore en train d'apparaître) ; elle renaît en bas. */
    fun pop(slot: Int): Bubble? {
        val b = bubbles.getOrNull(slot) ?: return null
        if (b.scale < 0.5) return null
        bubbles[slot] = spawn(slot, initial = false, others = bubbles - b)
        return b
    }
}

private data class BubbleBurst(val x: Double, val y: Double, val radius: Double, val colorIndex: Int, val startNanos: Long)
private const val BURST_NANOS = 450_000_000L
private val bubbleColors = GameColor.entries.map { Color(it.argb) }

@Composable
fun BubblesGame(fx: GameFx) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        if (w <= 0f || h <= 0f) return@BoxWithConstraints
        val aspect = (w / h).toDouble()
        val field = remember(aspect) { BubbleField(Random(System.nanoTime()), aspect) }
        var bubbles by remember(field) { mutableStateOf(field.current) }
        var clock by remember { mutableLongStateOf(0L) }
        val bursts = remember { mutableStateListOf<BubbleBurst>() }
        var popped by remember { mutableIntStateOf(0) }

        LaunchedEffect(field) {
            var last = withFrameNanos { it }
            while (true) {
                withFrameNanos { t ->
                    field.step(((t - last) / 1e9).coerceIn(0.0, 0.05))
                    last = t; clock = t
                    bubbles = field.current
                    bursts.removeAll { t - it.startNanos > BURST_NANOS }
                }
            }
        }

        val pop: (Int) -> Unit = { slot ->
            field.pop(slot)?.let { b ->
                bursts.add(BubbleBurst(b.displayX(aspect), b.y, b.radius, b.colorIndex, clock))
                bubbles = field.current
                fx.sounds.play(GameSound.POP)
                popped++
                if (popped % 10 == 0) fx.celebrate("Bravo !") // Encouragement toutes les 10 bulles
            }
        }

        val density = LocalDensity.current
        bubbles.forEach { b ->
            key(b.slot) {
                val diameter = with(density) { (b.radius * 2 * h).toFloat().toDp() }
                Box(
                    modifier = Modifier
                        .offset { IntOffset(((b.displayX(aspect) - b.radiusX(aspect)) * w).roundToInt(), ((b.y - b.radius) * h).roundToInt()) }
                        .size(diameter)
                        .graphicsLayer { scaleX = b.scale.toFloat(); scaleY = b.scale.toFloat(); alpha = b.alpha.toFloat() }
                        .gameTarget("bulle_${b.slot}") { pop(b.slot) }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) { drawBubble(bubbleColors[b.colorIndex]) }
                }
            }
        }
        // Éclats (anneau et gouttelettes qui s'écartent)
        Canvas(modifier = Modifier.fillMaxSize()) { bursts.forEach { drawBurst(it, clock, w, h) } }
    }
}

private fun DrawScope.drawBubble(color: Color) {
    val r = size.minDimension / 2
    val c = center
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.9f), color.copy(alpha = 0.8f), color),
            center = Offset(c.x - r * 0.35f, c.y - r * 0.35f), radius = r * 1.4f
        ),
        r, c
    )
    drawCircle(Color.White.copy(alpha = 0.6f), r, c, style = Stroke(r * 0.06f))
    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.16f, Offset(c.x - r * 0.4f, c.y - r * 0.42f))
}

private fun DrawScope.drawBurst(b: BubbleBurst, now: Long, w: Float, h: Float) {
    val t = ((now - b.startNanos).toFloat() / BURST_NANOS).coerceIn(0f, 1f)
    val c = Offset((b.x * w).toFloat(), (b.y * h).toFloat())
    val r = (b.radius * h).toFloat()
    val color = bubbleColors[b.colorIndex].copy(alpha = 1 - t)
    drawCircle(color, r * (1 + 0.6f * t), c, style = Stroke(r * 0.12f * (1 - t) + 1f))
    repeat(8) { i ->
        val angle = i * PI / 4
        val d = r * (0.6f + t)
        drawCircle(color, r * 0.12f * (1 - t) + 1f, Offset(c.x + (d * cos(angle)).toFloat(), c.y + (d * sin(angle)).toFloat()))
    }
}
