package com.example.nina

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Réglages des jeux (mode adulte › Jeux › Réglages des jeux) : jeux proposés, vitesse et taille des bulles,
 * nombre de cartes du memory, couleurs et formes utilisées, couleurs et dessins du coloriage.
 * Enregistrés dans les préférences de l'app.
 */

enum class BubbleSpeed(val label: String, val factor: Double) {
    LENTE("Lente", 0.6), NORMALE("Normale", 1.0), RAPIDE("Rapide", 1.6)
}

/** Rayon des bulles, en fraction de la hauteur de l'aire de jeu. */
enum class BubbleSize(val label: String, val minRadius: Double, val maxRadius: Double) {
    MOYENNE("Moyennes", 0.07, 0.10), GRANDE("Grandes", 0.10, 0.14), TRES_GRANDE("Très grandes", 0.14, 0.18)
}

data class GamesSettings(
    val games: Set<GameKind> = GameKind.entries.toSet(),
    val bubbleSpeed: BubbleSpeed = BubbleSpeed.NORMALE,
    val bubbleSize: BubbleSize = BubbleSize.GRANDE,
    val bubbleCount: Int = 5,
    val memoryStartPairs: Int = 2,
    val memoryProgressive: Boolean = true, // Une paire de plus à chaque partie gagnée
    val memoryUsePictos: Boolean = true, // Sinon : formes de couleur
    val findStartChoices: Int = 2,
    val findProgressive: Boolean = true, // Un choix de plus après 3 réussites d'affilée
    val findColors: Set<GameColor> = GameColor.entries.toSet(),
    val findShapes: Set<GameShape> = GameShape.entries.toSet(),
    val coloringColors: Set<String> = ColoringPalette.map { it.label }.toSet(),
    val coloringChooseColor: Boolean = false, // L'enfant choisit sa couleur dans une palette
    val coloringPictures: Set<String> = ColoringPictures.all.map { it.name }.toSet()
) {
    /** Valeurs ramenées dans les limites, et listes jamais trop courtes pour jouer. */
    fun normalized(): GamesSettings = copy(
        games = games.ifEmpty { GameKind.entries.toSet() },
        bubbleCount = bubbleCount.coerceIn(BUBBLE_COUNTS),
        memoryStartPairs = memoryStartPairs.coerceIn(MEMORY_PAIRS),
        findStartChoices = findStartChoices.coerceIn(FIND_CHOICES),
        findColors = findColors.takeIf { it.size >= 2 } ?: GameColor.entries.toSet(),
        findShapes = findShapes.takeIf { it.size >= 2 } ?: GameShape.entries.toSet(),
        coloringColors = coloringColors.filter { label -> ColoringPalette.any { it.label == label } }.toSet()
            .ifEmpty { ColoringPalette.map { it.label }.toSet() },
        coloringPictures = coloringPictures.filter { name -> ColoringPictures.all.any { it.name == name } }.toSet()
            .ifEmpty { ColoringPictures.all.map { it.name }.toSet() }
    )

    /** Jeux du menu, dans l'ordre habituel. */
    val menuGames: List<GameKind> get() = GameKind.entries.filter { it in games }
    val memoryMaxPairs: Int get() = if (memoryProgressive) MEMORY_PAIRS.last else memoryStartPairs
    val findMaxChoices: Int get() = if (findProgressive) FIND_CHOICES.last else findStartChoices
    val coloringPalette: List<PaintColor> get() = ColoringPalette.filter { it.label in coloringColors }
    val pictures: List<ColoringPicture> get() = ColoringPictures.all.filter { it.name in coloringPictures }

    /** Enregistrement : une valeur texte par réglage. */
    fun toMap(): Map<String, String> = mapOf(
        "games_enabled" to games.joinToString(",") { it.name },
        "games_bubble_speed" to bubbleSpeed.name,
        "games_bubble_size" to bubbleSize.name,
        "games_bubble_count" to bubbleCount.toString(),
        "games_memory_pairs" to memoryStartPairs.toString(),
        "games_memory_progressive" to memoryProgressive.toString(),
        "games_memory_pictos" to memoryUsePictos.toString(),
        "games_find_choices" to findStartChoices.toString(),
        "games_find_progressive" to findProgressive.toString(),
        "games_find_colors" to findColors.joinToString(",") { it.name },
        "games_find_shapes" to findShapes.joinToString(",") { it.name },
        "games_coloring_colors" to coloringColors.joinToString(","),
        "games_coloring_choose" to coloringChooseColor.toString(),
        "games_coloring_pictures" to coloringPictures.joinToString(",")
    )

    companion object {
        val BUBBLE_COUNTS = 3..8
        val MEMORY_PAIRS = 2..6
        val FIND_CHOICES = 2..4

        private inline fun <reified T : Enum<T>> names(text: String?): Set<T>? =
            text?.split(",")?.mapNotNull { name -> enumValues<T>().firstOrNull { it.name == name } }?.toSet()

        /** Lecture : un réglage absent ou illisible garde sa valeur par défaut. */
        fun fromMap(get: (String) -> String?): GamesSettings {
            val d = GamesSettings()
            fun text(key: String) = get(key)?.split(",")?.filter { it.isNotBlank() }?.toSet()
            return GamesSettings(
                games = names<GameKind>(get("games_enabled")) ?: d.games,
                bubbleSpeed = names<BubbleSpeed>(get("games_bubble_speed"))?.firstOrNull() ?: d.bubbleSpeed,
                bubbleSize = names<BubbleSize>(get("games_bubble_size"))?.firstOrNull() ?: d.bubbleSize,
                bubbleCount = get("games_bubble_count")?.toIntOrNull() ?: d.bubbleCount,
                memoryStartPairs = get("games_memory_pairs")?.toIntOrNull() ?: d.memoryStartPairs,
                memoryProgressive = get("games_memory_progressive")?.toBooleanStrictOrNull() ?: d.memoryProgressive,
                memoryUsePictos = get("games_memory_pictos")?.toBooleanStrictOrNull() ?: d.memoryUsePictos,
                findStartChoices = get("games_find_choices")?.toIntOrNull() ?: d.findStartChoices,
                findProgressive = get("games_find_progressive")?.toBooleanStrictOrNull() ?: d.findProgressive,
                findColors = names<GameColor>(get("games_find_colors")) ?: d.findColors,
                findShapes = names<GameShape>(get("games_find_shapes")) ?: d.findShapes,
                coloringColors = text("games_coloring_colors") ?: d.coloringColors,
                coloringChooseColor = get("games_coloring_choose")?.toBooleanStrictOrNull() ?: d.coloringChooseColor,
                coloringPictures = text("games_coloring_pictures") ?: d.coloringPictures
            ).normalized()
        }

        fun load(prefs: SharedPreferences) = fromMap { prefs.getString(it, null) }

        fun save(prefs: SharedPreferences, settings: GamesSettings) {
            prefs.edit().apply { settings.normalized().toMap().forEach { (k, v) -> putString(k, v) } }.apply()
        }
    }
}

/** Fenêtre des réglages, un onglet par jeu. */
@Composable
fun GamesSettingsDialog(initial: GamesSettings, onDismiss: () -> Unit, onSave: (GamesSettings) -> Unit) {
    var s by remember { mutableStateOf(initial) }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Jeux", "Bulles", "Memory", "Couleurs / formes", "Coloriage")

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFFF0F0F5),
        title = { Text("Réglages des jeux", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                PrimaryScrollableTabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = Color(0xFF4A5D91), edgePadding = 0.dp) {
                    tabs.forEachIndexed { i, title -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) }) }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (tab) {
                        0 -> SettingsCard {
                            SettingsSectionTitle("Jeux proposés à l'enfant")
                            ToggleChips(GameKind.entries, s.games, { it.title }) { s = s.copy(games = it) }
                            TextButton(onClick = { s = GamesSettings() }) { Text("Remettre tous les réglages des jeux par défaut") }
                        }
                        1 -> SettingsCard {
                            SettingsSectionTitle("Vitesse")
                            ChoiceChips(BubbleSpeed.entries, s.bubbleSpeed, { it.label }) { s = s.copy(bubbleSpeed = it) }
                            SettingsSectionTitle("Taille")
                            ChoiceChips(BubbleSize.entries, s.bubbleSize, { it.label }) { s = s.copy(bubbleSize = it) }
                            StepperRow("Bulles à l'écran", s.bubbleCount, GamesSettings.BUBBLE_COUNTS) { s = s.copy(bubbleCount = it) }
                            Hint("Avec la commande oculaire, préférez une vitesse lente, de grandes bulles et peu de bulles.")
                        }
                        2 -> SettingsCard {
                            StepperRow("Paires au départ", s.memoryStartPairs, GamesSettings.MEMORY_PAIRS, { "$it paires (${it * 2} cartes)" }) { s = s.copy(memoryStartPairs = it) }
                            SwitchRow("Une paire de plus à chaque partie gagnée (jusqu'à 6)", s.memoryProgressive) { s = s.copy(memoryProgressive = it) }
                            SwitchRow("Cartes avec les pictos de l'enfant (sinon : formes de couleur)", s.memoryUsePictos) { s = s.copy(memoryUsePictos = it) }
                            Hint("Seuls les pictos avec une image sont utilisés. S'il n'y en a pas assez, les formes de couleur les remplacent.")
                        }
                        3 -> SettingsCard {
                            StepperRow("Choix au départ", s.findStartChoices, GamesSettings.FIND_CHOICES) { s = s.copy(findStartChoices = it) }
                            SwitchRow("Un choix de plus après 3 réussites d'affilée (jusqu'à 4)", s.findProgressive) { s = s.copy(findProgressive = it) }
                            SettingsSectionTitle("Couleurs utilisées (au moins 2)")
                            ColorToggles(GameColor.entries.map { it.label to it.argb }, s.findColors.map { it.label }.toSet(), min = 2) { labels ->
                                s = s.copy(findColors = GameColor.entries.filter { it.label in labels }.toSet())
                            }
                            SettingsSectionTitle("Formes utilisées (au moins 2)")
                            ShapeToggles(s.findShapes) { s = s.copy(findShapes = it) }
                        }
                        else -> SettingsCard {
                            SwitchRow("L'enfant choisit sa couleur dans une palette (sinon : une couleur différente à chaque fois)", s.coloringChooseColor) { s = s.copy(coloringChooseColor = it) }
                            SettingsSectionTitle("Couleurs du coloriage")
                            ColorToggles(ColoringPalette.map { it.label to it.argb }, s.coloringColors, min = 1) { s = s.copy(coloringColors = it) }
                            SettingsSectionTitle("Dessins")
                            ToggleChips(ColoringPictures.all.map { it.name }, s.coloringPictures, { it.replaceFirstChar(Char::uppercase) }) { s = s.copy(coloringPictures = it) }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(s.normalized()) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun Hint(text: String) = Text(text, fontSize = 13.sp, color = Color.Gray)

@Composable
private fun StepperRow(label: String, value: Int, range: IntRange, format: (Int) -> String = { "$it" }, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) { Icon(Icons.Default.Remove, contentDescription = "Moins") }
        Text(format(value), fontWeight = FontWeight.Bold, color = Color(0xFF4A5D91))
        IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) { Icon(Icons.Default.Add, contentDescription = "Plus") }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) }) }
    }
}

/** Choix multiple ; le dernier choix restant ne peut pas être retiré. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ToggleChips(options: List<T>, selected: Set<T>, label: (T) -> String, min: Int = 1, onChange: (Set<T>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val on = option in selected
            FilterChip(
                selected = on,
                onClick = { if (!on) onChange(selected + option) else if (selected.size > min) onChange(selected - option) },
                label = { Text(label(option)) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorToggles(colors: List<Pair<String, Long>>, selected: Set<String>, min: Int, onChange: (Set<String>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { (label, argb) ->
            val on = label in selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .graphicsLayer { alpha = if (on) 1f else 0.35f }
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(if (on) 4.dp else 1.dp, if (on) Color(0xFF263238) else Color.LightGray, CircleShape)
                        .clickable { if (!on) onChange(selected + label) else if (selected.size > min) onChange(selected - label) },
                    contentAlignment = Alignment.Center
                ) {
                    if (on) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                }
                Text(label, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShapeToggles(selected: Set<GameShape>, onChange: (Set<GameShape>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GameShape.entries.forEach { shape ->
            val on = shape in selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .graphicsLayer { alpha = if (on) 1f else 0.35f }
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(if (on) 3.dp else 1.dp, if (on) Color(0xFF4A5D91) else Color.LightGray, RoundedCornerShape(12.dp))
                        .clickable { if (!on) onChange(selected + shape) else if (selected.size > 2) onChange(selected - shape) }
                        .padding(8.dp)
                ) {
                    ShapeIcon(shape, Color(0xFF4A5D91), Modifier.fillMaxSize())
                }
                Text(shape.label, fontSize = 12.sp)
            }
        }
    }
}
