package com.example.nina

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * Section Jeux : un moment de détente après une séance de travail, lancé par l'adulte (mode adulte › Jeux).
 * Chaque jeu se joue au doigt ou du regard (les cibles sont des zones de la commande oculaire).
 * On en sort par la fin de la minuterie, ou en maintenant le cadenas 2 secondes.
 */

/** Durées proposées au lancement, en minutes (0 = sans limite). */
val GAMES_DURATIONS = listOf(5, 10, 15, 20, 0)
const val GAMES_MINUTES_PREF = "games_minutes"

/** Séance de jeux : début (horloge SystemClock.elapsedRealtime) et durée (null = sans limite). */
data class GamesSession(val startedAt: Long, val durationMs: Long?) {
    fun remainingMs(now: Long): Long? = durationMs?.let { (startedAt + it - now).coerceAtLeast(0L) }
    fun fractionLeft(now: Long): Float? = durationMs?.let { remainingMs(now)!!.toFloat() / it }
    fun isOver(now: Long): Boolean = remainingMs(now) == 0L

    /** Prévenir « encore une minute » : seulement pour les séances de plus de 2 minutes, dans la dernière minute. */
    fun shouldWarn(now: Long): Boolean {
        val left = remainingMs(now) ?: return false
        return durationMs!! > 2 * 60_000L && left in 1..60_000L
    }

    companion object {
        fun start(minutes: Int, now: Long = SystemClock.elapsedRealtime()) =
            GamesSession(now, if (minutes <= 0) null else minutes * 60_000L)
    }
}

enum class GameKind(val title: String, val spoken: String, val color: Long) {
    BULLES("Bulles", "Les bulles", 0xFF29B6F6),
    MEMORY("Memory", "Le memory", 0xFF7E57C2),
    COULEURS("Couleurs", "Les couleurs", 0xFFEF5350),
    FORMES("Formes", "Les formes", 0xFF26A69A),
    COLORIAGE("Coloriage", "Le coloriage", 0xFFFFA726)
}

/** Ce que les jeux peuvent faire : bruitages, parole, félicitations (étoiles + « Bravo »). */
class GameFx(val sounds: GameSounds, private val speakState: State<(String) -> Unit>, private val onCelebrate: (String) -> Unit) {
    fun speak(text: String) = speakState.value(text)
    fun celebrate(text: String) = onCelebrate(text)
}

/**
 * Cible d'un jeu : se choisit au doigt (toucher) ou du regard (fixation, comme les pictos).
 * enabled = false : ni l'un ni l'autre (carte déjà trouvée…).
 */
fun Modifier.gameTarget(key: String, enabled: Boolean = true, onActivate: () -> Unit): Modifier = composed {
    val action by rememberUpdatedState(onActivate)
    this
        .gazeZone(key, enabled = enabled) { action() }
        .then(if (enabled) Modifier.pointerInput(key) { detectTapGestures(onTap = { action() }) } else Modifier)
}

private data class Celebration(val text: String, val id: Long)

@Composable
fun GamesScreen(
    session: GamesSession,
    pictos: List<AACCell>, // Pictos de l'enfant (avec image), pour le memory
    isCviMode: Boolean,
    speak: (String) -> Unit,
    onExit: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sounds = remember { GameSounds(context) }
    DisposableEffect(Unit) { onDispose { sounds.release() } }
    val speakLatest = rememberUpdatedState(speak)
    var celebration by remember { mutableStateOf<Celebration?>(null) }
    val fx = remember {
        GameFx(sounds, speakLatest) { text ->
            celebration = Celebration(text, System.nanoTime())
            sounds.play(GameSound.SUCCESS)
            speakLatest.value(text)
        }
    }
    var current by remember { mutableStateOf<GameKind?>(null) }

    // Minuterie : « encore une minute », puis fin douce et retour à la grille
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(session) {
        var warned = false
        while (true) {
            now = SystemClock.elapsedRealtime()
            if (!warned && session.shouldWarn(now)) { warned = true; speakLatest.value("Encore une minute de jeu.") }
            if (session.isOver(now)) break
            delay(500)
        }
    }
    val over = session.isOver(now)
    val currentOnExit by rememberUpdatedState(onExit)
    LaunchedEffect(over) {
        if (over) {
            celebration = null
            speakLatest.value("C'est fini ! À bientôt.")
            delay(5000)
            currentOnExit()
        }
    }

    val background = if (isCviMode) Color.Black else Color(0xFFE3F2FD)
    val textColor = if (isCviMode) Color.White else Color(0xFF263238)
    Box(modifier = Modifier.fillMaxSize().background(background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Barre du haut : menu des jeux, temps restant, cadenas (maintenir 2 s : retour à la grille)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(96.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Box(modifier = Modifier.size(88.dp)) {
                    if (current != null && !over) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(4.dp, Color(0xFF4A5D91), CircleShape)
                                .gameTarget("jeux_menu") { current = null; celebration = null; speakLatest.value("Les jeux") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Apps, contentDescription = "Menu des jeux", tint = Color(0xFF4A5D91), modifier = Modifier.size(52.dp))
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    session.fractionLeft(now)?.let { GamesTimerBar(it, Modifier.fillMaxWidth()) }
                }
                HoldLockButton(isAdminMode = false, onHoldComplete = onExit, onLock = {})
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    over -> GamesEndScreen(textColor)
                    else -> when (val kind = current) {
                        null -> GamesMenu(textColor) { picked -> current = picked; speakLatest.value(picked.spoken) }
                        else -> key(kind) {
                            when (kind) {
                                GameKind.BULLES -> BubblesGame(fx)
                                GameKind.MEMORY -> MemoryGame(pictos, fx)
                                GameKind.COULEURS -> FindGame(FindKind.COULEUR, fx, textColor)
                                GameKind.FORMES -> FindGame(FindKind.FORME, fx, textColor)
                                GameKind.COLORIAGE -> ColoringGame(fx, textColor)
                            }
                        }
                    }
                }
            }
        }
        celebration?.let { c -> key(c.id) { CelebrationOverlay(c.text) { if (celebration?.id == c.id) celebration = null } } }
    }
}

/** Temps restant : une barre qui se vide (verte, puis orange dans le dernier quart). */
@Composable
private fun GamesTimerBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(18.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.7f))) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(9.dp))
                .background(if (fraction > 0.25f) Color(0xFF66BB6A) else Color(0xFFFFA726))
        )
    }
}

@Composable
private fun GamesMenu(textColor: Color, onPick: (GameKind) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            "On joue !", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = textColor,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            GameKind.entries.forEach { kind ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(kind.color))
                        .gameTarget("jeu_${kind.name}") { onPick(kind) }
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GameKindIcon(kind, Modifier.weight(1f).fillMaxWidth())
                    Text(kind.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}

/** Petite illustration de chaque jeu, dessinée (pas d'image à fournir). */
@Composable
private fun GameKindIcon(kind: GameKind, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val ox = (size.width - s) / 2; val oy = (size.height - s) / 2
        fun at(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
        // Grille 2 × 2 de petites formes
        fun cell(i: Int) = at(0.28f + 0.44f * (i % 2), 0.28f + 0.44f * (i / 2))
        val white = Color.White
        when (kind) {
            GameKind.BULLES -> listOf(Triple(0.36f, 0.62f, 0.24f), Triple(0.70f, 0.40f, 0.17f), Triple(0.40f, 0.20f, 0.11f)).forEach { (x, y, r) ->
                drawCircle(white.copy(alpha = 0.3f), r * s, at(x, y))
                drawCircle(white, r * s, at(x, y), style = Stroke(s * 0.025f))
                drawCircle(white, r * s * 0.2f, at(x - r * 0.4f, y - r * 0.4f))
            }
            GameKind.MEMORY -> {
                drawRoundRect(white, at(0.10f, 0.18f), Size(0.38f * s, 0.56f * s), androidx.compose.ui.geometry.CornerRadius(0.05f * s))
                drawRoundRect(white, at(0.52f, 0.26f), Size(0.38f * s, 0.56f * s), androidx.compose.ui.geometry.CornerRadius(0.05f * s))
                val star = at(0.29f, 0.46f); drawPath(gameShapePath(GameShape.ETOILE, star.x, star.y, 0.13f * s), Color(0xFF7E57C2))
                val heart = at(0.71f, 0.54f); drawPath(gameShapePath(GameShape.COEUR, heart.x, heart.y, 0.12f * s), Color(0xFFE53935))
            }
            GameKind.COULEURS -> listOf(GameColor.JAUNE, GameColor.BLEU, GameColor.VERT, GameColor.VIOLET).forEachIndexed { i, c ->
                drawCircle(Color(c.argb), 0.19f * s, cell(i)); drawCircle(white, 0.19f * s, cell(i), style = Stroke(s * 0.02f))
            }
            GameKind.FORMES -> listOf(GameShape.ETOILE, GameShape.TRIANGLE, GameShape.CARRE, GameShape.COEUR).forEachIndexed { i, shape ->
                val c = cell(i); drawPath(gameShapePath(shape, c.x, c.y, 0.18f * s), white)
            }
            GameKind.COLORIAGE -> translate(ox, oy) {
                drawColoringPicture(ColoringPictures.MAISON, mapOf("maison_toit" to 0, "maison_soleil" to 2, "maison_porte" to 1), side = s)
            }
        }
    }
}

/** Félicitations : des étoiles jaillissent du centre, et le message s'affiche (2,2 s). */
@Composable
private fun CelebrationOverlay(text: String, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val stars = remember { List(16) { i -> Triple(i * 2 * PI / 16 + Random.nextDouble(-0.2, 0.2), 0.6 + Random.nextDouble() * 0.4, GameColor.entries[i % GameColor.entries.size]) } }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(2200, easing = LinearEasing))
        onDone()
    }
    val t = progress.value
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val spread = 1 - (1 - t) * (1 - t) // Rapide au début, puis ralentit
            val maxDistance = size.minDimension * 0.55f
            stars.forEach { (angle, speed, color) ->
                val d = (maxDistance * speed * spread).toFloat()
                val x = center.x + d * cos(angle).toFloat(); val y = center.y + d * sin(angle).toFloat()
                drawPath(gameShapePath(GameShape.ETOILE, x, y, size.minDimension * 0.04f * (1 - t * 0.4f)), Color(color.argb).copy(alpha = 1 - t))
            }
        }
        val textAlpha = when { t < 0.12f -> t / 0.12f; t > 0.85f -> (1 - t) / 0.15f; else -> 1f }
        Text(
            text, fontSize = 52.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4A5D91), textAlign = TextAlign.Center,
            modifier = Modifier
                .graphicsLayer { alpha = textAlpha.coerceIn(0f, 1f) }
                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(32.dp))
                .padding(horizontal = 40.dp, vertical = 20.dp)
        )
    }
}

/** Fin de la séance : un soleil souriant, « C'est fini ! À bientôt ». */
@Composable
private fun GamesEndScreen(textColor: Color) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val r = size.minDimension / 2
            drawCircle(Color(0xFFFDD835), r, center)
            drawCircle(Color(0xFF5D4037), r * 0.09f, Offset(center.x - r * 0.35f, center.y - r * 0.2f))
            drawCircle(Color(0xFF5D4037), r * 0.09f, Offset(center.x + r * 0.35f, center.y - r * 0.2f))
            drawArc(
                Color(0xFF5D4037), startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(center.x - r * 0.5f, center.y - r * 0.4f), size = Size(r, r * 0.9f),
                style = Stroke(width = r * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("C'est fini !", fontSize = 60.sp, fontWeight = FontWeight.Bold, color = textColor)
        Text("À bientôt", fontSize = 40.sp, color = textColor)
    }
}

/** Mode adulte › Jeux : durée de la séance, puis lancement. */
@Composable
fun GamesLaunchDialog(initialMinutes: Int, onDismiss: () -> Unit, onLaunch: (Int) -> Unit) {
    var minutes by remember { mutableIntStateOf(initialMinutes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Moment de jeux", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Durée avant le retour automatique à la grille :")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GAMES_DURATIONS.forEach { m ->
                        FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text(if (m == 0) "Sans limite" else "$m min") })
                    }
                }
                Text(
                    "Bulles, memory (avec les pictos de l'enfant), couleurs, formes et coloriage. " +
                        "L'enfant joue au doigt, ou du regard si la commande oculaire est active.\n" +
                        "Une minute avant la fin, Nina prévient. Pour revenir à la grille plus tôt : maintenir le cadenas 2 secondes.",
                    fontSize = 14.sp, color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(onClick = { onLaunch(minutes) }) {
                Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Lancer les jeux")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
