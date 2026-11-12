package com.example.nina

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Coloriage magique : toucher (ou fixer) une partie du dessin la remplit de couleur, et le nom de la couleur
 * est dit. Un nouvel appui change la couleur. Pas d'échec possible ; dessin entièrement colorié : bravo.
 * Pour la commande oculaire, les rectangles qui entourent les parties d'un dessin ne se chevauchent jamais
 * (vérifié par les tests) : chaque partie est une cible distincte.
 */

/** Rectangle en fractions du dessin (carré de côté 1). */
data class NormBox(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width get() = right - left
    val height get() = bottom - top
}

sealed class RegionShape {
    abstract val bounds: NormBox
    abstract fun toPath(side: Float): Path

    data class Polygon(val points: List<Pair<Double, Double>>) : RegionShape() {
        override val bounds get() = NormBox(points.minOf { it.first }, points.minOf { it.second }, points.maxOf { it.first }, points.maxOf { it.second })
        override fun toPath(side: Float) = Path().apply {
            points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo((x * side).toFloat(), (y * side).toFloat()) else lineTo((x * side).toFloat(), (y * side).toFloat()) }
            close()
        }
    }

    data class Ellipse(val cx: Double, val cy: Double, val rx: Double, val ry: Double) : RegionShape() {
        override val bounds get() = NormBox(cx - rx, cy - ry, cx + rx, cy + ry)
        override fun toPath(side: Float) = Path().apply {
            addOval(Rect(((cx - rx) * side).toFloat(), ((cy - ry) * side).toFloat(), ((cx + rx) * side).toFloat(), ((cy + ry) * side).toFloat()))
        }
    }
}

/** Traits du dessin qui ne se colorient pas (yeux, fenêtres, antennes…). */
sealed class Deco {
    data class Dot(val cx: Double, val cy: Double, val r: Double) : Deco()
    data class Ring(val cx: Double, val cy: Double, val r: Double) : Deco()
    data class Line(val x1: Double, val y1: Double, val x2: Double, val y2: Double) : Deco()
    data class Frame(val left: Double, val top: Double, val right: Double, val bottom: Double) : Deco()
}

data class ColoringRegion(val id: String, val shape: RegionShape)
data class ColoringPicture(val name: String, val regions: List<ColoringRegion>, val decorations: List<Deco> = emptyList())

/** Couleurs du coloriage, dans l'ordre où elles sont posées. */
data class PaintColor(val label: String, val argb: Long)
val ColoringPalette = listOf(
    PaintColor("rouge", 0xFFE53935), PaintColor("bleu", 0xFF1E88E5), PaintColor("jaune", 0xFFFDD835),
    PaintColor("vert", 0xFF43A047), PaintColor("orange", 0xFFFB8C00), PaintColor("violet", 0xFF8E24AA),
    PaintColor("rose", 0xFFF06292), PaintColor("turquoise", 0xFF26C6DA)
)

object ColoringPictures {
    private fun rect(l: Double, t: Double, r: Double, b: Double) = RegionShape.Polygon(listOf(l to t, r to t, r to b, l to b))

    private fun star(cx: Double, cy: Double, r: Double) = RegionShape.Polygon(List(10) { i ->
        val angle = -PI / 2 + i * PI / 5
        val radius = if (i % 2 == 0) r else r * 0.45
        (cx + radius * cos(angle)) to (cy + radius * sin(angle))
    })

    /** Tranche verticale d'une ellipse, entre x0 et x1 (pour découper le corps du poisson). */
    private fun ellipseSlice(cx: Double, cy: Double, rx: Double, ry: Double, x0: Double, x1: Double, steps: Int = 12): RegionShape.Polygon {
        fun half(x: Double) = ry * sqrt((1 - ((x - cx) / rx).let { it * it }).coerceAtLeast(0.0))
        val xs = List(steps + 1) { x0 + (x1 - x0) * it / steps }
        return RegionShape.Polygon(xs.map { it to cy - half(it) } + xs.reversed().map { it to cy + half(it) })
    }

    val MAISON = ColoringPicture(
        "maison",
        listOf(
            ColoringRegion("maison_toit", RegionShape.Polygon(listOf(0.18 to 0.40, 0.46 to 0.12, 0.74 to 0.40))),
            ColoringRegion("maison_soleil", RegionShape.Ellipse(0.87, 0.15, 0.09, 0.09)),
            ColoringRegion("maison_nuage", RegionShape.Ellipse(0.10, 0.10, 0.07, 0.045)),
            ColoringRegion("maison_mur_gauche", rect(0.22, 0.40, 0.38, 0.84)),
            ColoringRegion("maison_porte", rect(0.38, 0.40, 0.54, 0.84)),
            ColoringRegion("maison_mur_droit", rect(0.54, 0.40, 0.70, 0.84)),
            ColoringRegion("maison_herbe", rect(0.04, 0.84, 0.96, 0.95))
        ),
        listOf(
            Deco.Frame(0.25, 0.50, 0.35, 0.62), Deco.Line(0.30, 0.50, 0.30, 0.62), // fenêtres
            Deco.Frame(0.57, 0.50, 0.67, 0.62), Deco.Line(0.62, 0.50, 0.62, 0.62),
            Deco.Dot(0.51, 0.65, 0.012) // poignée de la porte
        )
    )

    val FUSEE = ColoringPicture(
        "fusée",
        listOf(
            ColoringRegion("fusee_nez", RegionShape.Polygon(listOf(0.38 to 0.30, 0.50 to 0.06, 0.62 to 0.30))),
            ColoringRegion("fusee_haut", rect(0.38, 0.30, 0.62, 0.52)),
            ColoringRegion("fusee_bas", rect(0.38, 0.52, 0.62, 0.74)),
            ColoringRegion("fusee_aileron_gauche", RegionShape.Polygon(listOf(0.38 to 0.56, 0.24 to 0.80, 0.38 to 0.74))),
            ColoringRegion("fusee_aileron_droit", RegionShape.Polygon(listOf(0.62 to 0.56, 0.76 to 0.80, 0.62 to 0.74))),
            ColoringRegion("fusee_flamme", RegionShape.Polygon(listOf(0.42 to 0.74, 0.58 to 0.74, 0.55 to 0.84, 0.50 to 0.95, 0.45 to 0.84))),
            ColoringRegion("fusee_etoile", star(0.83, 0.18, 0.08)),
            ColoringRegion("fusee_lune", RegionShape.Ellipse(0.16, 0.20, 0.08, 0.08))
        ),
        listOf(Deco.Ring(0.50, 0.41, 0.06)) // hublot
    )

    val POISSON = ColoringPicture(
        "poisson",
        listOf(
            ColoringRegion("poisson_tete", ellipseSlice(0.45, 0.50, 0.28, 0.18, 0.17, 0.36)),
            ColoringRegion("poisson_milieu", ellipseSlice(0.45, 0.50, 0.28, 0.18, 0.36, 0.55)),
            ColoringRegion("poisson_arriere", ellipseSlice(0.45, 0.50, 0.28, 0.18, 0.55, 0.73)),
            ColoringRegion("poisson_queue", RegionShape.Polygon(listOf(0.73 to 0.50, 0.92 to 0.32, 0.92 to 0.68))),
            ColoringRegion("poisson_nageoire_haut", RegionShape.Polygon(listOf(0.38 to 0.32, 0.48 to 0.17, 0.58 to 0.32))),
            ColoringRegion("poisson_nageoire_bas", RegionShape.Polygon(listOf(0.38 to 0.68, 0.48 to 0.83, 0.58 to 0.68))),
            ColoringRegion("poisson_bulle_1", RegionShape.Ellipse(0.10, 0.20, 0.05, 0.05)),
            ColoringRegion("poisson_bulle_2", RegionShape.Ellipse(0.18, 0.10, 0.04, 0.04))
        ),
        listOf(Deco.Dot(0.26, 0.46, 0.022), Deco.Line(0.19, 0.54, 0.24, 0.54)) // œil, bouche
    )

    val CHENILLE = ColoringPicture(
        "chenille",
        listOf(
            ColoringRegion("chenille_1", RegionShape.Ellipse(0.13, 0.50, 0.09, 0.09)),
            ColoringRegion("chenille_2", RegionShape.Ellipse(0.31, 0.45, 0.09, 0.09)),
            ColoringRegion("chenille_3", RegionShape.Ellipse(0.49, 0.50, 0.09, 0.09)),
            ColoringRegion("chenille_4", RegionShape.Ellipse(0.67, 0.45, 0.09, 0.09)),
            ColoringRegion("chenille_tete", RegionShape.Ellipse(0.87, 0.42, 0.10, 0.10)),
            ColoringRegion("chenille_feuille", RegionShape.Polygon(listOf(0.06 to 0.80, 0.30 to 0.68, 0.70 to 0.68, 0.94 to 0.80, 0.70 to 0.92, 0.30 to 0.92))),
            ColoringRegion("chenille_soleil", RegionShape.Ellipse(0.12, 0.14, 0.08, 0.08))
        ),
        listOf(
            Deco.Dot(0.84, 0.39, 0.014), Deco.Dot(0.91, 0.39, 0.014), Deco.Line(0.84, 0.46, 0.90, 0.46), // yeux, sourire
            Deco.Line(0.83, 0.33, 0.79, 0.21), Deco.Line(0.91, 0.33, 0.95, 0.21), Deco.Dot(0.79, 0.21, 0.014), Deco.Dot(0.95, 0.21, 0.014), // antennes
            Deco.Line(0.13, 0.59, 0.13, 0.64), Deco.Line(0.31, 0.54, 0.31, 0.62), Deco.Line(0.49, 0.59, 0.49, 0.64), Deco.Line(0.67, 0.54, 0.67, 0.62), // pattes
            Deco.Line(0.10, 0.80, 0.90, 0.80) // nervure de la feuille
        )
    )

    val all = listOf(MAISON, FUSEE, POISSON, CHENILLE)
}

/** Dessine le coloriage dans un carré de côté side, en haut à gauche de la zone de dessin. */
fun DrawScope.drawColoringPicture(picture: ColoringPicture, fills: Map<String, Int>, side: Float = size.minDimension, outline: Color = Color(0xFF263238)) {
    val stroke = Stroke(width = side * 0.008f, join = StrokeJoin.Round)
    fun at(x: Double, y: Double) = Offset((x * side).toFloat(), (y * side).toFloat())
    picture.regions.forEach { region ->
        val path = region.shape.toPath(side)
        val fill = fills[region.id]?.let { Color(ColoringPalette[it % ColoringPalette.size].argb) } ?: Color.White
        drawPath(path, fill)
        drawPath(path, outline, style = stroke)
    }
    picture.decorations.forEach { d ->
        when (d) {
            is Deco.Dot -> drawCircle(outline, (d.r * side).toFloat(), at(d.cx, d.cy))
            is Deco.Ring -> drawCircle(outline, (d.r * side).toFloat(), at(d.cx, d.cy), style = stroke)
            is Deco.Line -> drawLine(outline, at(d.x1, d.y1), at(d.x2, d.y2), strokeWidth = side * 0.008f, cap = StrokeCap.Round)
            is Deco.Frame -> drawRect(outline, at(d.left, d.top), Size(((d.right - d.left) * side).toFloat(), ((d.bottom - d.top) * side).toFloat()), style = stroke)
        }
    }
}

@Composable
fun ColoringGame(fx: GameFx, textColor: Color, settings: GamesSettings = GamesSettings()) {
    val pictures = remember { settings.pictures.ifEmpty { ColoringPictures.all } }
    // Couleurs choisies dans les réglages, repérées par leur place dans la palette complète
    val palette = remember { settings.coloringPalette.ifEmpty { ColoringPalette }.map { ColoringPalette.indexOf(it) } }
    var pictureIndex by remember { mutableIntStateOf(0) }
    val picture = pictures[pictureIndex % pictures.size]
    val fills = remember(pictureIndex) { mutableStateMapOf<String, Int>() }
    var nextColor by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableIntStateOf(palette.first()) } // Couleur choisie (mode palette)
    var done by remember(pictureIndex) { mutableStateOf(false) }

    val paint: (ColoringRegion) -> Unit = { region ->
        val color = if (settings.coloringChooseColor) chosen else palette[nextColor++ % palette.size]
        fills[region.id] = color
        fx.sounds.play(GameSound.TAP)
        // Mode palette : la couleur a été dite en la choisissant
        if (!settings.coloringChooseColor) fx.speak(ColoringPalette[color].label)
        if (!done && fills.size == picture.regions.size) { done = true; fx.celebrate("Bravo, c'est magnifique !") }
    }

    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Palette : l'enfant choisit sa couleur (au doigt ou du regard)
        if (settings.coloringChooseColor) {
            Column(
                modifier = Modifier.width(110.dp).fillMaxHeight().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                palette.forEach { index ->
                    val color = ColoringPalette[index]
                    val selected = index == chosen
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 96.dp)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(Color(color.argb))
                            .border(if (selected) 8.dp else 2.dp, if (selected) textColor else Color.White, CircleShape)
                            .gameTarget("couleur_$index") { chosen = index; fx.sounds.play(GameSound.TAP); fx.speak(color.label) }
                    )
                }
            }
        }
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight().padding(bottom = 16.dp)) {
            val side = minOf(maxWidth, maxHeight)
            Box(modifier = Modifier.offset((maxWidth - side) / 2, (maxHeight - side) / 2).size(side)) {
                Canvas(modifier = Modifier.fillMaxSize()) { drawColoringPicture(picture, fills) }
                // Une cible par partie du dessin (au doigt ou du regard)
                picture.regions.forEach { region ->
                    key(region.id) {
                        val b = region.shape.bounds
                        Box(
                            modifier = Modifier
                                .offset(side * b.left.toFloat(), side * b.top.toFloat())
                                .size(side * b.width.toFloat(), side * b.height.toFloat())
                                .gameTarget("dessin_${region.id}") { paint(region) }
                        )
                    }
                }
            }
        }
        // Autre dessin (seulement s'il y en a plusieurs)
        if (pictures.size > 1) {
            Column(
                modifier = Modifier.width(170.dp).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF8F00))
                        .gameTarget("dessin_suivant") { pictureIndex++; fx.speak("Un autre dessin") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Autre dessin", tint = Color.White, modifier = Modifier.size(72.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Autre dessin", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor)
            }
        }
    }
}
