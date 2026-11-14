package fr.pgragnic.force.ui

import androidx.compose.material.icons.automirrored.filled.HelpOutline
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pgragnic.force.*
import fr.pgragnic.force.gl.Body3D
import fr.pgragnic.force.gl.BodyState
import kotlin.math.floor
import kotlin.math.roundToInt

// Aide contextuelle (Philippe, 11 novembre : « Est-ce qu'on ne rajouterait pas une aide contextuelle ? », maquettes validées :
// « Tout me semble bien et ok pour le raccourci "Remplacer". […] ok pour les 3 niveaux 6,7 et 8 ») :
// ⑥ fiche de l'exercice (« i »), ⑦ explication de chaque écran (« ? »), ⑧ bulles au premier lancement.

// ---------------- ⑦ explication de l'écran ----------------
private class HelpItem(val h: String, val t: String)

private fun helpOf(screen: String): Pair<String, List<HelpItem>> = when (screen) {
    "seance" -> t("Comment lire cet écran", "How to read this screen") to listOf(
        HelpItem(t("La prochaine séance", "Your next workout"), t("L'appli propose la séance qui ménage le mieux tes muscles encore fatigués. Tu peux en choisir une autre avec A, B, C, D.", "The app suggests the workout that best spares your muscles that are still tired. You can pick another one with A, B, C, D.")),
        HelpItem(t("La semaine", "The week"), t("Le programme dure 8 semaines. La semaine avance chaque lundi si tu as fait au moins une séance.", "The program lasts 8 weeks. The week moves on every Monday if you did at least one workout.")),
        HelpItem(t("Les exercices", "The exercises"), t("Touche un exercice pour voir le mouvement en 3D, les consignes, et le remplacer s'il ne te convient pas.", "Tap an exercise to see the move in 3D, the cues, and to replace it if it doesn't suit you.")),
        HelpItem(t("Les charges", "The loads"), t("Elles s'ajustent toutes seules : elles montent quand la série est réussie plusieurs fois, et baissent après deux échecs.", "They adjust on their own: they go up when the set is completed several times, and down after two misses.")),
        HelpItem(t("L'alerte de récupération", "The recovery warning"), t("Si la séance sollicite surtout des muscles pas encore récupérés, l'appli te prévient avant de démarrer et propose une autre séance.", "If the workout mostly uses muscles that haven't recovered yet, the app warns you before starting and suggests another workout.")))
    "corps" -> t("Comment lire cet écran", "How to read this screen") to listOf(
        HelpItem(t("Le personnage", "The character"), t("Fais-le tourner avec le doigt, ou touche Face ou Dos.", "Rotate it with your finger, or tap Front or Back.")),
        HelpItem(t("Les couleurs", "The colors"), t("Vert : prêt. Orange : en récupération. Rouge : fatigué.", "Green: ready. Orange: recovering. Red: tired.")),
        HelpItem(t("Le pourcentage", "The percentage"), t("C'est la récupération du muscle depuis tes dernières séances. À 100 %, il est prêt à retravailler.", "It is the muscle's recovery since your last workouts. At 100%, it is ready to work again.")),
        HelpItem(t("« Prêt dans ~1 j 6 h »", "\"Ready in ~1 d 6 h\""), t("Le temps estimé avant que le muscle soit de nouveau prêt.", "The estimated time before the muscle is ready again.")),
        HelpItem(t("Toucher un muscle", "Tapping a muscle"), t("Ouvre son détail : les exercices qui le font travailler et ton volume de la semaine.", "Opens its detail: the exercises that work it and your volume for the week.")),
        HelpItem(t("Pourquoi c'est utile", "Why it matters"), t("L'appli choisit ta prochaine séance pour ménager les muscles encore fatigués.", "The app picks your next workout to spare the muscles that are still tired.")))
    "progres" -> t("Comment lire cet écran", "How to read this screen") to listOf(
        HelpItem(t("Activité", "Activity"), t("Tes pas de chaque jour et tes sorties (marche, randonnée, trail). Touche un jour pour le corriger.", "Your daily steps and your outings (walk, hike, trail run). Tap a day to edit it.")),
        HelpItem(t("Courbes", "Charts"), t("L'évolution de tes charges et de ton volume d'entraînement, semaine après semaine.", "How your loads and training volume change, week after week.")),
        HelpItem(t("Semaine", "Week"), t("Le bilan de la semaine : séances faites, volume par muscle et conseils.", "The week's summary: workouts done, volume per muscle and advice.")))
    else -> t("Comment lire cet écran", "How to read this screen") to listOf(
        HelpItem(t("Mon programme", "My program"), t("Tout ce qui change tes séances : matériel, charges, santé, profil, nombre de séances par semaine et durée maximale.", "Everything that changes your workouts: equipment, loads, health, profile, workouts per week and maximum length.")),
        HelpItem(t("Rappels", "Reminders"), t("Les jours et l'heure où le téléphone te rappelle ta séance.", "The days and time your phone reminds you of your workout.")),
        HelpItem(t("Compte et sauvegarde", "Account and backup"), t("Un compte garde tes séances en ligne et les retrouve sur un autre téléphone.", "An account keeps your workouts online and restores them on another phone.")),
        HelpItem(t("Affichage", "Display"), t("Thème clair ou sombre, et langue. Ces réglages sont propres à ce téléphone.", "Light or dark theme, and language. These settings belong to this phone.")))
}

/** Bouton « ? » en haut d'un écran : ouvre l'explication de l'écran. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpButton(screen: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.size(40.dp).tourTarget(if (screen == "seance") "help" else null).clip(CircleShape).clickable { open = true }, contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.HelpOutline, fr.pgragnic.force.tr("Aide"), tint = C.muted, modifier = Modifier.size(24.dp))
    }
    if (open) {
        val (title, items) = helpOf(screen)
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.surface) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, color = C.ink, fontSize = 24.sp, fontWeight = FontWeight.Normal)
                items.forEach {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(it.h, color = C.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(it.t, color = C.muted, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
                Button(onClick = { open = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.brand, contentColor = C.onBrand)) { Text(t("Compris", "Got it"), fontWeight = FontWeight.Medium, fontSize = 16.sp) }
            }
        }
    }
}

// ---------------- ⑥ fiche de l'exercice ----------------
/** Fiche d'un exercice : animation 3D en boucle, muscles travaillés, consignes, erreurs à éviter, remplacement. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExSheet(id: String, sub: String?, inSess: List<String>, swap: Boolean, onClose: () -> Unit) {
    val e = Prog.EX[id] ?: return
    val ctx = LocalContext.current
    val gear by GearStore.g.collectAsState()
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr(e.n), color = C.ink, fontSize = 22.sp, fontWeight = FontWeight.Normal, lineHeight = 28.sp)
                    if (!sub.isNullOrBlank()) Text(sub, color = C.muted, fontSize = 13.sp)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, t("Fermer", "Close"), tint = C.muted) }
            }
            // animation en boucle (une répétition sur deux en miroir pour les exercices alternés)
            var tt by remember { mutableStateOf(0.0) }
            LaunchedEffect(id) { val t0 = System.nanoTime(); while (true) { withFrameNanos { }; tt = (System.nanoTime() - t0) / 1e9 } }
            // tempo réel (Philippe, « Partons sur 2,3,5 et 7 », point 3) : montée 1,5 s, courte tenue, descente lente 2,5 s, courte pause ;
            // les mouvements qui commencent par la descente (squat, pompes, fentes…) démarrent par la phase lente
            val base = listOf(1.5, .5, 2.5, .4)
            val tp = e.tp ?: if (e.inv && e.m != "alt" && e.m != "time") listOf(base[2], base[1], base[0], base[3]) else base
            val k = if (e.m == "time") 0.0 else Prog.cycleK(tt, tp)
            val mir = e.m == "alt" && floor(tt / tp.sum().coerceAtLeast(.1)).toInt() % 2 == 1
            Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(18.dp)).background(C.stage)) {
                Body3D(BodyState(pose = e.pose, ex = id, hl = id, k = k, mirror = mir).th(), Modifier.fillMaxSize())
            }
            Text(t("Fais tourner le personnage avec le doigt.", "Rotate the character with your finger."), color = C.muted, fontSize = 12.sp)
            val mus = (Prog.MAP[id] ?: emptyMap()).entries.filter { it.value >= .25 }.sortedByDescending { it.value }
            if (mus.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Head(t("MUSCLES TRAVAILLÉS", "MUSCLES WORKED"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    mus.forEach { (m, v) ->
                        Text(tr(m), color = C.ink, fontSize = 12.sp, fontWeight = if (v >= .7) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (v >= .7) C.mhot.copy(alpha = .38f) else C.bg).padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
            }
            val how = e.cues.ifEmpty { listOf(e.tip).filter { it.isNotBlank() } }
            if (how.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Head(t("COMMENT FAIRE", "HOW TO DO IT")); how.forEach { Line("✓", C.effort, tr(it)) }
            }
            if (e.avoid.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Head(t("À ÉVITER", "AVOID")); e.avoid.forEach { Line("✕", C.danger, tr(it)) }
            }
            if (swap) {
                val base = Prog.origin(id, gear); val alts = remember(id, gear, inSess) { Prog.alternatives(id, inSess, gear) }
                var open by remember { mutableStateOf(false) }
                if (alts.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.bg)) {
                    Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t("Trop dur ou pas le matériel ?", "Too hard or missing the equipment?"), color = C.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(if (open) t("Fermer", "Close") else t("Remplacer", "Replace"), color = C.brand, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    if (open) alts.forEach { a ->
                        val x = Prog.EX[a]!!
                        HorizontalDivider(color = C.line)
                        Row(Modifier.fillMaxWidth().clickable { Prog.swap(ctx, id, a); onClose() }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr(x.n), color = C.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (a == base) t("Exercice d'origine", "Original exercise") else tr(x.l), color = C.muted, fontSize = 12.sp)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = C.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Head(s: String) = Text(s, color = C.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
@Composable private fun Line(mark: String, c: Color, s: String) = Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(mark, color = c, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.width(14.dp))
    Text(s, color = C.ink, fontSize = 14.sp, lineHeight = 19.sp)
}

// ---------------- ⑧ bulles au premier lancement ----------------
object Tour {
    /** étape en cours, -1 = pas de visite */
    val step = mutableStateOf(-1)
    val rects = mutableStateMapOf<String, Rect>()
    val KEYS = listOf("start", "info", "corps", "help")
    fun seen(ctx: Context) = ctx.getSharedPreferences("force", Context.MODE_PRIVATE).getBoolean("tour", false)
    fun start() { step.value = 0 }
    fun end(ctx: Context) { step.value = -1; ctx.getSharedPreferences("force", Context.MODE_PRIVATE).edit().putBoolean("tour", true).apply() }
    fun text(k: String): Pair<String, String> = when (k) {
        "start" -> t("Lance ta séance", "Start your workout") to t("L'appli te guide exercice par exercice : mouvement en 3D, compte des répétitions et temps de repos.", "The app guides you exercise by exercise: 3D move, rep count and rest time.")
        "info" -> t("Le détail de chaque exercice", "Each exercise in detail") to t("Touche un exercice pour voir le mouvement, les consignes, et le remplacer s'il ne te convient pas.", "Tap an exercise to see the move, the cues, and to replace it if it doesn't suit you.")
        "corps" -> t("Tes muscles", "Your muscles") to t("L'onglet Corps montre quels muscles sont récupérés et lesquels laisser reposer.", "The Body tab shows which muscles have recovered and which to rest.")
        else -> t("Besoin d'aide ?", "Need help?") to t("Le « ? » explique chaque écran.", "The \"?\" explains each screen.")
    }
}

/** Repère un élément que la visite guidée met en évidence. */
fun Modifier.tourTarget(k: String?): Modifier = if (k == null) this else this.onGloballyPositioned { val r = it.boundsInRoot(); if (Tour.rects[k] != r) Tour.rects[k] = r }

/** Visite guidée : fond assombri, élément mis en évidence, bulle avec « Passer » et « Suivant ». À poser au-dessus de tout l'écran. */
@Composable
fun TourOverlay() {
    val i = Tour.step.value; if (i < 0) return
    val ctx = LocalContext.current; val den = LocalDensity.current
    if (i >= Tour.KEYS.size) { LaunchedEffect(i) { Tour.end(ctx) }; return }
    val key = Tour.KEYS[i]
    var size by remember { mutableStateOf(Size.Zero) }; var bh by remember { mutableStateOf(0) }
    val r = Tour.rects[key]
    // élément absent ou hors de l'écran (petit téléphone) : on passe à la bulle suivante
    val visible = r != null && size.height > 0 && r.top >= 0 && r.bottom <= size.height && r.height > 4
    LaunchedEffect(i, visible, size) { if (size.height > 0) { kotlinx.coroutines.delay(600); if (!visible && Tour.step.value == i) Tour.step.value = i + 1 } }
    Box(Modifier.fillMaxSize().onSizeChanged { size = Size(it.width.toFloat(), it.height.toFloat()) }.pointerInput(Unit) { detectTapGestures { } }) {
        if (!visible || r == null) return@Box
        val pad = with(den) { 6.dp.toPx() }; val rad = with(den) { 16.dp.toPx() }
        val green = C.brand
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color(0xB305080A))
            val tl = Offset(r.left - pad, r.top - pad); val sz = Size(r.width + 2 * pad, r.height + 2 * pad)
            drawRoundRect(Color.Black, tl, sz, CornerRadius(rad, rad), blendMode = BlendMode.Clear)
            drawRoundRect(green, tl, sz, CornerRadius(rad, rad), style = Stroke(with(den) { 2.dp.toPx() }))
        }
        val gap = with(den) { 14.dp.toPx() }
        val below = r.center.y < size.height / 2
        val y = if (below) r.bottom + pad + gap else r.top - pad - gap - bh
        val (title, body) = Tour.text(key)
        Column(Modifier.offset { IntOffset(0, y.roundToInt().coerceAtLeast(0)) }.padding(horizontal = 16.dp).fillMaxWidth().onSizeChanged { bh = it.height }
            .clip(RoundedCornerShape(16.dp)).background(Color(0xFFE7EDEF)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color(0xFF0E1317), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(body, color = Color(0xFF2A353D), fontSize = 14.sp, lineHeight = 20.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Tour.KEYS.indices.forEach { j -> Box(Modifier.size(width = 18.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(if (j == i) Color(0xFF0E1317) else Color(0xFF9AA7AF))) }
                }
                TextButton(onClick = { Tour.end(ctx) }) { Text(t("Passer", "Skip"), color = Color(0xFF5B6770), fontWeight = FontWeight.SemiBold) }
                Button(onClick = { Tour.step.value = i + 1 }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E1317), contentColor = Color(0xFFE7EDEF))) {
                    Text(if (i == Tour.KEYS.size - 1) t("Terminer", "Done") else t("Suivant", "Next"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
