package com.example.nina

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Couleurs des jeux, avec leur nom tel qu'il est affiché et prononcé. */
enum class GameColor(val label: String, val spoken: String, val argb: Long) {
    ROUGE("rouge", "le rouge", 0xFFE53935),
    BLEU("bleu", "le bleu", 0xFF1E88E5),
    JAUNE("jaune", "le jaune", 0xFFFDD835),
    VERT("vert", "le vert", 0xFF43A047),
    ORANGE("orange", "l'orange", 0xFFFB8C00),
    VIOLET("violet", "le violet", 0xFF8E24AA)
}

/** Formes des jeux. */
enum class GameShape(val label: String, val spoken: String) {
    ROND("rond", "le rond"),
    CARRE("carré", "le carré"),
    TRIANGLE("triangle", "le triangle"),
    ETOILE("étoile", "l'étoile"),
    COEUR("cœur", "le cœur")
}

/** Contour d'une forme centrée sur (cx, cy), de rayon r (pixels). */
fun gameShapePath(shape: GameShape, cx: Float, cy: Float, r: Float): Path = Path().apply {
    when (shape) {
        GameShape.ROND -> addOval(Rect(cx - r, cy - r, cx + r, cy + r))
        GameShape.CARRE -> addRoundRect(RoundRect(Rect(cx - r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy + r * 0.85f), CornerRadius(r * 0.15f)))
        GameShape.TRIANGLE -> {
            moveTo(cx, cy - r); lineTo(cx + r * 0.95f, cy + r * 0.75f); lineTo(cx - r * 0.95f, cy + r * 0.75f); close()
        }
        GameShape.ETOILE -> {
            for (i in 0 until 10) {
                val angle = -PI / 2 + i * PI / 5
                val radius = if (i % 2 == 0) r else r * 0.45f
                val x = cx + (radius * cos(angle)).toFloat(); val y = cy + (radius * sin(angle)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        GameShape.COEUR -> {
            moveTo(cx, cy + r * 0.85f)
            cubicTo(cx - r * 1.3f, cy - r * 0.1f, cx - r * 0.6f, cy - r * 1.05f, cx, cy - r * 0.45f)
            cubicTo(cx + r * 0.6f, cy - r * 1.05f, cx + r * 1.3f, cy - r * 0.1f, cx, cy + r * 0.85f)
            close()
        }
    }
}

/** Dessine la forme au centre de la zone de dessin, aussi grande que possible. */
fun DrawScope.drawGameShape(shape: GameShape, color: Color, outline: Color? = null) {
    val s = min(size.width, size.height)
    val path = gameShapePath(shape, size.width / 2, size.height / 2, s / 2 * 0.92f)
    drawPath(path, color)
    outline?.let { drawPath(path, it, style = Stroke(width = s * 0.03f)) }
}

@Composable
fun ShapeIcon(shape: GameShape, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawGameShape(shape, color, outline = Color.Black.copy(alpha = 0.25f)) }
}
