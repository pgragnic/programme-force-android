package fr.pgragnic.force.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pgragnic.force.*
import fr.pgragnic.force.gl.Body3D
import fr.pgragnic.force.gl.BodyState
import kotlin.math.PI
import kotlin.math.roundToInt

private fun Int.col() = Color(0xFF000000 or this.toLong())

/** Onglet Corps : récupération des muscles, de face et de dos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CorpsScreen() {
    val log by Store.log.collectAsState(); val days by Store.days.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(10 * 60_000L); tick++ } }
    val MU = remember(log, days, tick) { Recovery.muscles(log, days) }
    // muscle choisi dans la liste : détail, et seul ce muscle en couleur sur les deux vues (demande de Philippe :
    // « Détails pour chaque muscle, en cliquant dessus »)
    var sel by remember { mutableStateOf(Demo.muscle) }
    val colors = remember(MU, C.dark, sel) { Recovery.MUS.filter { sel == null || it == sel }.associateWith { if (MU.over(it)) Recovery.overCol(C.rgb(C.danger)) else Recovery.recCol(MU.R[it]!!, 0xFF4D4D, 0xFFA21A, 0x16C26A) } }   // muscle choisi seul en couleur, les autres gris clair
    val st by Store.st.collectAsState(); val gear by GearStore.g.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            H1(t("Corps", "Body"))
            Spacer(Modifier.weight(1f))
            HelpButton("corps")
        }
        // un seul personnage, qu'on fait tourner au doigt ou avec Face / Dos (Philippe, capture du 11 novembre : « Un seul corps suffit
        // puisqu'on peut le tourner »)
        var back by remember { mutableStateOf(false) }
        // bouton allumé selon le côté réellement visible, y compris après une rotation au doigt (capture de Philippe, 13 novembre 20 h 37)
        var shownBack by remember { mutableStateOf(false) }; var viewKey by remember { mutableIntStateOf(0) }
        val onView = remember { { b: Boolean -> shownBack = b } }
        // muscle touché sur le personnage : bulle avec % et temps avant d'être prêt (Philippe, 12 novembre : « Est-ce qu'on pourrait avoir
        // plus de détails que les 3 niveaux actuels des récupération ? », maquette A, « J'aime bien l'option A »)
        var pick by remember { mutableStateOf<Triple<String, Float, Float>?>(null) }
        LaunchedEffect(pick) { if (pick != null) { kotlinx.coroutines.delay(4000); pick = null } }
        val onPick = remember { { n: String?, x: Float, y: Float -> pick = n?.let { Triple(it, x, y) } } }
        val dens = androidx.compose.ui.platform.LocalDensity.current
        // style épuré : le personnage dans une tuile, Face / Dos dessous, explications et légende dans l'aide « ? »
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.stage), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(330.dp)) {
                Body3D(BodyState(pose = "calf", k = 0.0, nodb = true, colors = colors, az = if (back) PI.toFloat() else 0f, map = true, onPick = onPick, onView = onView, viewKey = viewKey).th(), Modifier.fillMaxSize())
                pick?.let { (m, x, y) ->
                    val r = MU.R[m] ?: 100; val h = MU.ready[m] ?: 0
                    val wPx = with(dens) { 150.dp.toPx() }; val maxX = with(dens) { maxWidth.toPx() } - wPx
                    val ox = (x + with(dens) { 14.dp.toPx() }).let { if (it > maxX) x - wPx - with(dens) { 14.dp.toPx() } else it }.coerceAtLeast(0f)
                    val oy = (y - with(dens) { 24.dp.toPx() }).coerceAtLeast(0f)
                    Column(Modifier.offset { androidx.compose.ui.unit.IntOffset(ox.toInt(), oy.toInt()) }.width(150.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(fr.pgragnic.force.tr(m) + " · $r %", color = Color(0xFF111111), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (MU.over(m)) Text(t("surchargé, +${Prog.fk(Prog.r1(MU.ex[m]!!))} séries", "overloaded, +${Prog.fk(Prog.r1(MU.ex[m]!!))} sets"), color = Color(0xFFE3141E), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(if (r >= 90) t("prêt à travailler", "ready to train") else t("prêt dans ~", "ready in ~") + if (h >= 24) t("${h / 24} j ${h % 24} h", "${h / 24} d ${h % 24} h") else "$h h", color = Color(0xFF555555), fontSize = 12.sp)
                    }
                }
            }
        }
        // Face / Dos sous la tuile (dans la tuile, la zone sous la vue 3D sortait noire sur téléphone virtuel)
        Seg(listOf(t("Face", "Front") to !shownBack, t("Dos", "Back") to shownBack), Modifier.align(Alignment.CenterHorizontally).width(180.dp)) { i -> back = i == 1; viewKey++ }
        // liste complète (Philippe : « Montre tous les exercices y compris ceux à 100% ») : muscles à laisser reposer d'abord,
        // puis les muscles prêts avec la même ligne détaillée ; toucher un muscle ouvre son détail
        val tired = Recovery.MUS.filter { MU.R[it]!! < 90 }.sortedBy { MU.R[it]!! }; val ready = Recovery.MUS.filter { MU.R[it]!! >= 90 }.sortedBy { MU.R[it]!! }
        @Composable fun row(m: String) {
            val r = MU.R[m]!!; val ov = MU.over(m); val c = (if (ov) Recovery.overCol(C.rgb(C.danger)) else Recovery.recCol(r, C.rgb(C.danger), C.rgb(C.warm), C.rgb(C.effort))).col(); val h = MU.ready[m] ?: 0
            Column(Modifier.fillMaxWidth().glam().clickable { sel = if (sel == m) null else m }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // anneau de récupération façon Apple Fitness, dans la couleur exacte du niveau (plein et rouge vif si surchargé)
                    Box(contentAlignment = Alignment.Center) { Ring(if (ov) 1f else r / 100f, c, 40.dp, 5.dp); Text("$r", color = C.ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(fr.pgragnic.force.tr(m), color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text((if (ov) t("Surchargé (+${Prog.fk(Prog.r1(MU.ex[m]!!))} séries) · ", "Overloaded (+${Prog.fk(Prog.r1(MU.ex[m]!!))} sets) · ") else "") +
                            (if (r >= 90) t("Prêt à travailler", "Ready to train") else t("Prêt dans ~", "Ready in ~") + if (h >= 24) t("${h / 24} j ${h % 24} h", "${h / 24} d ${h % 24} h") else "$h h"), color = C.muted, fontSize = 12.sp)
                    }
                    Icon(if (sel == m) androidx.compose.material.icons.Icons.Default.ExpandLess else androidx.compose.material.icons.Icons.Default.ExpandMore, null, tint = C.muted, modifier = Modifier.padding(start = 6.dp))
                }
                if (sel == m) MuscleDetail(m, log, st, gear, MU.V[m]!!, MU.tot)
            }
        }
        // à ménager : les 3 muscles les moins récupérés en tuiles ; la liste complète derrière « Voir les … »
        var all by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Cap(if (tired.isNotEmpty()) t("À ménager", "Go easy on") else t("Tous tes muscles sont prêts", "All your muscles are ready"), Modifier.weight(1f))
            Text(if (all) t("Réduire", "Show less") else t("Voir les ${Recovery.MUS.size}", "See all ${Recovery.MUS.size}"), color = C.brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { all = !all }.padding(4.dp))
        }
        if (!all && tired.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            tired.take(3).forEach { m ->
                val r = MU.R[m]!!; val ov = MU.over(m); val h = MU.ready[m] ?: 0
                Column(Modifier.weight(1f).glam().clickable { all = true; sel = m }.padding(12.dp)) {
                    Text(fr.pgragnic.force.tr(m), color = C.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                        Text("$r", color = if (ov) Color(0xFFD92D20) else C.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
                        Text(" %", color = if (ov) Color(0xFFD92D20) else C.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    Text(t("prêt ~", "ready ~") + if (h >= 24) t("${h / 24} j", "${h / 24} d") else "$h h", color = C.muted, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        if (all) { tired.forEach { row(it) }; ready.forEach { row(it) } }
    }
}

/** Couleurs de la scène 3D selon le thème. */
fun BodyState.th() = copy(bg = C.rgb(C.stage), ground = C.rgb(C.stage2), mat = C.rgb(C.stage3), db = C.rgb(C.effort), base = C.rgb(C.mrest), acc = C.rgb(C.mhot))

/** Détail d'un muscle : dernier travail, exercices des 7 derniers jours, exercices prévus cette semaine. */
@Composable
private fun MuscleDetail(m: String, log: List<Entry>, st: Settings, gear: Gear, v: Double, tot: Double) {
    val now = System.currentTimeMillis(); val fmt = java.time.format.DateTimeFormatter.ofPattern("EEE d", fr.pgragnic.force.L.loc)
    fun role(id: String) = Prog.MAP[id]?.get(m) ?: 0.0
    val recent = log.filter { now - it.d <= 7 * 864e5 }.sortedByDescending { it.d }.flatMap { e -> e.ex.filter { role(it.id) > 0 }.map { e to it } }
    val last = log.filter { e -> e.ex.any { role(it.id) >= .3 } }.maxByOrNull { it.d }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(C.bg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (v > 0) Text(t("${Prog.fk(v)} séries ces 7 derniers jours · ${(100 * v / tot).roundToInt()} % du volume", "${Prog.fk(v)} sets in the last 7 days · ${(100 * v / tot).roundToInt()}% of volume"), color = C.muted, fontSize = 12.sp)
        Text(last?.let { e -> val d = ((now - e.d) / 864e5).toInt(); t("Dernier travail : ", "Last worked: ") + (if (d == 0) t("aujourd'hui", "today") else if (d == 1) t("hier", "yesterday") else t("il y a $d jours", "$d days ago")) + t(" (séance ${e.s})", " (workout ${e.s})") }
            ?: t("Pas encore travaillé.", "Not worked yet."), color = C.ink, fontSize = 13.sp)
        if (recent.isNotEmpty()) {
            Text(t("7 derniers jours", "Last 7 days"), color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            recent.forEach { (e, x) ->
                val n = if (x.sets.isNotEmpty()) x.sets.size else 3
                val d = java.time.Instant.ofEpochMilli(e.d).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(fmt)
                Text("$d · ${fr.pgragnic.force.tr(Prog.EX[x.id]?.n ?: x.n)} · $n × ${x.done}" + (if (x.kg > 0) " · ${Prog.fk(x.kg)} kg" else "") +
                    (if (role(x.id) >= .7) "" else t(" · secondaire", " · secondary")), color = C.ink, fontSize = 13.sp)
            }
        }
        val wk = Prog.week(st.c, gear)
        val todo = wk.keys.sorted().mapNotNull { s -> wk[s]!!.filter { role(it) >= .3 }.takeIf { it.isNotEmpty() }?.let { s to it } }
        if (todo.isNotEmpty()) {
            Text(t("Au programme", "In your program"), color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            todo.forEach { (s, ids) ->
                Text(t("Séance $s : ", "Workout $s: ") + ids.joinToString(", ") { id -> fr.pgragnic.force.tr(Prog.EX[id]?.n ?: id) + if (role(id) >= .7) "" else t(" (secondaire)", " (secondary)") }, color = C.ink, fontSize = 13.sp)
            }
        }
    }
}
