package com.example.nina

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/*
 * Outils de l'adulte : cadenas discret (maintenir 2 s), barre du mode adulte, et ouverture des fenêtres.
 */

/**
 * Cadenas discret en haut à droite : petit, gris, sans couleur vive pour ne pas attirer l'enfant.
 * Il faut le maintenir 2 secondes (un anneau se remplit) pour ouvrir le calcul du mode adulte.
 * Un simple appui affiche seulement « Maintenir 2 s ». Cadenas ouvert, un appui le referme.
 */
@Composable
fun HoldLockButton(isAdminMode: Boolean, onHoldComplete: () -> Unit, onLock: () -> Unit, warning: Boolean = false) {
    if (isAdminMode) {
        IconButton(onClick = onLock, modifier = Modifier.size(48.dp).background(Color(0xFFFFEB3B), CircleShape)) {
            Icon(Icons.Default.LockOpen, contentDescription = "Verrouiller")
        }
        return
    }

    val scope = rememberCoroutineScope()
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(showHint) { if (showHint) { kotlinx.coroutines.delay(2000); showHint = false } }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        val hold = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, androidx.compose.animation.core.tween(2000, easing = androidx.compose.animation.core.LinearEasing))
                            onHoldComplete()
                            progress.snapTo(0f)
                        }
                        tryAwaitRelease()
                        if (hold.isActive) {
                            // Relâché trop tôt : on n'ouvre rien
                            hold.cancel()
                            scope.launch { progress.snapTo(0f) }
                            showHint = true
                        }
                    })
                }
        ) {
            CircularProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF4A5D91),
                trackColor = Color.Transparent,
                strokeWidth = 4.dp
            )
            Icon(Icons.Default.Lock, contentDescription = "Mode adulte (maintenir 2 secondes)", tint = Color.Gray.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
            // Point orange discret : un réglage demande l'attention d'un adulte (voir la barre du mode adulte)
            if (warning) Box(modifier = Modifier.align(Alignment.TopEnd).size(10.dp).background(Color(0xFFFF6D00), CircleShape))
        }
        if (showHint) Text("Maintenir 2 s", fontSize = 11.sp, color = Color.Gray)
    }
}

/**
 * Barre du mode adulte, en haut de l'écran, rangée en groupes :
 * [Mode adulte] | Ajouter · Édition · Disposition ▾ | Jeux | … | Réglages ▾ · Aide · Verrouiller.
 * Les actions fréquentes sont visibles ; le reste est dans deux menus :
 * Disposition (taille de la grille, colonnes, grilles prêtes) et Réglages (paramètres, outils, sauvegardes).
 */
@Composable
fun AdminToolbar(
    showMore: Boolean, onMoreToggle: (Boolean) -> Unit, // Menu Réglages ouvert
    isEditMode: Boolean, onEditModeToggle: (Boolean) -> Unit,
    columnCount: Int, onColumnCountChange: (Int) -> Unit,
    sideColumnsHidden: Boolean, onSideColumnsToggle: () -> Unit,
    onAddToGrid: () -> Unit, onAddToLeft: () -> Unit, onAddToQuick: () -> Unit, onSettings: () -> Unit, onStats: () -> Unit, onHelp: () -> Unit,
    onCalibration: () -> Unit, isKioskMode: Boolean, onKioskToggle: () -> Unit,
    onBackup: () -> Unit, onRestore: () -> Unit,
    onRecommendedLayout: () -> Unit, onRestorePreviousLayout: (() -> Unit)?, // null : rien à restaurer
    onAutoBackups: () -> Unit,
    onEyeGaze: () -> Unit,
    onGames: () -> Unit,
    onPrintBoard: () -> Unit,
    voiceWarning: String?, onVoiceFix: () -> Unit, // null : la voix fonctionne hors ligne
    onLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFF4A5D91)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF3C4))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color(0xFF8D6E00))
        Text("Mode adulte", fontWeight = FontWeight.Bold, color = Color(0xFF5D4600))
        // Problème de voix : bouton orange bien visible pour l'adulte, qui explique comment installer la voix
        voiceWarning?.let { message ->
            Button(
                onClick = onVoiceFix,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(message)
            }
        }

        ToolbarSeparator()

        // --- Groupe « grille » : ajouter, modifier, disposition ---
        // Ajouter : on choisit la zone (les colonnes n'ont pas de case « + » pour ne pas changer de disposition)
        Box {
            var showAddMenu by remember { mutableStateOf(false) }
            AdminToolButton(Icons.Default.Add, "Ajouter", { showAddMenu = true })
            DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                DropdownMenuItem(text = { Text("Dans la grille (dossier ouvert)") }, leadingIcon = { Icon(Icons.Default.GridView, null) }, onClick = { showAddMenu = false; onAddToGrid() })
                DropdownMenuItem(text = { Text("Dans la colonne de gauche (dossiers)") }, leadingIcon = { Icon(Icons.Default.Folder, null) }, onClick = { showAddMenu = false; onAddToLeft() })
                DropdownMenuItem(text = { Text("Dans les phrases rapides") }, leadingIcon = { Icon(Icons.Default.Bolt, null) }, onClick = { showAddMenu = false; onAddToQuick() })
            }
        }
        AdminToolButton(Icons.Default.Edit, if (isEditMode) "Édition : oui" else "Édition", { onEditModeToggle(!isEditMode) }, selected = isEditMode)
        // Disposition : taille de la grille, colonnes latérales, grilles prêtes (le menu reste ouvert pendant les réglages)
        Box {
            var showLayoutMenu by remember { mutableStateOf(false) }
            AdminToolButton(Icons.Default.Dashboard, "Disposition", { showLayoutMenu = true }, dropdown = true)
            DropdownMenu(expanded = showLayoutMenu, onDismissRequest = { showLayoutMenu = false }) {
                MenuSection("Grille")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text("Pictos par rangée", modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp))
                    IconButton(onClick = { if (columnCount > 1) onColumnCountChange(columnCount - 1) }, enabled = columnCount > 1) {
                        Icon(Icons.Default.Remove, contentDescription = "Moins de pictos par rangée")
                    }
                    Text("$columnCount", fontWeight = FontWeight.Bold, color = accent, fontSize = 18.sp)
                    IconButton(onClick = { if (columnCount < 8) onColumnCountChange(columnCount + 1) }, enabled = columnCount < 8) {
                        Icon(Icons.Default.Add, contentDescription = "Plus de pictos par rangée")
                    }
                }
                // Colonnes latérales (dossiers, phrases rapides) : masquées, la grille seule occupe l'écran
                DropdownMenuItem(
                    text = { Text("Colonnes de gauche et de droite") },
                    leadingIcon = { Icon(Icons.Default.ViewColumn, null) },
                    trailingIcon = { Switch(checked = !sideColumnsHidden, onCheckedChange = { onSideColumnsToggle() }) },
                    onClick = onSideColumnsToggle
                )
                HorizontalDivider()
                MenuSection("Grilles")
                DropdownMenuItem(text = { Text("Grilles prêtes…") }, leadingIcon = { Icon(Icons.Default.Extension, null) }, onClick = { showLayoutMenu = false; onRecommendedLayout() })
                onRestorePreviousLayout?.let { restore ->
                    DropdownMenuItem(text = { Text("Revenir à la disposition précédente") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, null) }, onClick = { showLayoutMenu = false; restore() })
                }
            }
        }

        ToolbarSeparator()

        // --- Pour l'enfant ---
        Button(
            onClick = onGames,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E57C2)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Jeux")
        }

        Spacer(modifier = Modifier.weight(1f))

        // --- Réglages et outils plus rares ---
        Box {
            AdminToolButton(Icons.Default.Settings, "Réglages", { onMoreToggle(true) }, dropdown = true)
            DropdownMenu(expanded = showMore, onDismissRequest = { onMoreToggle(false) }) {
                MenuSection("Réglages")
                DropdownMenuItem(text = { Text("Paramètres (couleurs, voix, tactile)") }, leadingIcon = { Icon(Icons.Default.Tune, null) }, onClick = { onMoreToggle(false); onSettings() })
                DropdownMenuItem(text = { Text("Commande oculaire…") }, leadingIcon = { Icon(Icons.Default.Visibility, null) }, onClick = { onMoreToggle(false); onEyeGaze() })
                DropdownMenuItem(text = { Text("Calibrer le tactile") }, leadingIcon = { Icon(Icons.Default.TouchApp, null) }, onClick = { onMoreToggle(false); onCalibration() })
                DropdownMenuItem(
                    text = { Text(if (isKioskMode) "Désactiver le mode Kiosque" else "Activer le mode Kiosque") },
                    leadingIcon = { Icon(if (isKioskMode) Icons.Default.LockOpen else Icons.Default.Lock, null) },
                    onClick = { onMoreToggle(false); onKioskToggle() }
                )
                HorizontalDivider()
                MenuSection("Suivi")
                DropdownMenuItem(text = { Text("Statistiques") }, leadingIcon = { Icon(Icons.Default.BarChart, null) }, onClick = { onMoreToggle(false); onStats() })
                DropdownMenuItem(text = { Text("Planche imprimable…") }, leadingIcon = { Icon(Icons.Default.Print, null) }, onClick = { onMoreToggle(false); onPrintBoard() })
                HorizontalDivider()
                MenuSection("Sauvegarde")
                DropdownMenuItem(text = { Text("Partager / Sauvegarder") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { onMoreToggle(false); onBackup() })
                DropdownMenuItem(text = { Text(stringResource(R.string.btn_restore)) }, leadingIcon = { Icon(Icons.Default.Restore, null) }, onClick = { onMoreToggle(false); onRestore() })
                DropdownMenuItem(text = { Text("Sauvegardes automatiques…") }, leadingIcon = { Icon(Icons.Default.History, null) }, onClick = { onMoreToggle(false); onAutoBackups() })
            }
        }
        IconButton(onClick = onHelp) {
            Icon(Icons.AutoMirrored.Filled.Help, contentDescription = "Aide", tint = accent)
        }
        Button(onClick = onLock, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Verrouiller")
        }
    }
}

/** Trait vertical entre deux groupes de la barre. */
@Composable
private fun ToolbarSeparator() {
    Box(modifier = Modifier.padding(horizontal = 4.dp).width(1.dp).height(28.dp).background(Color(0xFFBFA75A)))
}

/** Titre de section dans un menu déroulant. */
@Composable
private fun MenuSection(title: String) {
    Text(
        title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun AdminToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit,
    selected: Boolean = false, dropdown: Boolean = false // dropdown : ouvre un menu (petite flèche ▾)
) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (selected) Color(0xFF4A5D91) else Color.White, contentColor = if (selected) Color.White else Color(0xFF4A5D91))
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label)
        if (dropdown) Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun DialogManager(
    showKeyboardDialog: Boolean, onKeyboardDismiss: () -> Unit, onKeyboardValidate: (AACCell) -> Unit,
    showAdminChallenge: Boolean, onAdminSuccess: () -> Unit, onAdminDismiss: () -> Unit,
    showAddDialog: Boolean, onAddDismiss: () -> Unit, onAddConfirm: (AACCell) -> Unit,
    showSettings: Boolean, currentBg: Long, currentPitch: Float, currentRate: Float, currentDebounce: Long, currentCviMode: Boolean, currentAutoReturn: Boolean,
    onSettingsDismiss: () -> Unit, onSettingsSave: (Long, Float, Float, Long, Boolean, Boolean) -> Unit,
    showHelp: Boolean, onHelpDismiss: () -> Unit,
    showCalibration: Boolean, onCalibrationFinished: (Float, Float) -> Unit,
    showWordFinder: Boolean, coreWords: List<AACCell>, quickFires: List<AACCell>, onWordFinderShow: (WordGuide) -> Unit, onWordFinderDismiss: () -> Unit,
    showStats: Boolean, usageStats: Map<String, Int>, onStatsDismiss: () -> Unit, onStatsReset: () -> Unit,
    showEditDialog: Boolean, cellToEdit: AACCell?, allButtons: List<AACCell>,
    onEditDelete: (AACCell) -> Unit, onEditSave: (AACCell, Boolean, AACCell?) -> Unit, onEditDismiss: () -> Unit
) {
    if (showKeyboardDialog) KeyboardInputDialog(onDismiss = onKeyboardDismiss, onValidate = onKeyboardValidate)
    if (showAdminChallenge) AdminChallengeDialog(onDismiss = onAdminDismiss, onSuccess = onAdminSuccess)
    if (showAddDialog) AddButtonDialog(onDismiss = onAddDismiss, onConfirm = onAddConfirm)
    if (showSettings) SettingsDialog(currentBg, currentPitch, currentRate, currentDebounce, currentCviMode, currentAutoReturn, onSettingsDismiss, onSettingsSave)
    if (showHelp) HelpDialog(onDismiss = onHelpDismiss)
    if (showCalibration) CalibrationTest(onFinished = onCalibrationFinished)
    if (showWordFinder) WordFinderDialog(allButtons = allButtons, coreWords = coreWords, quickFires = quickFires, onShow = onWordFinderShow, onDismiss = onWordFinderDismiss)
    if (showStats) StatsDialog(stats = usageStats, onDismiss = onStatsDismiss, onReset = onStatsReset)
    if (showEditDialog && cellToEdit != null) EditDialog(cellToEdit, allFolders(allButtons, coreWords), onDelete = onEditDelete, onSave = onEditSave, onDismiss = onEditDismiss)
}
