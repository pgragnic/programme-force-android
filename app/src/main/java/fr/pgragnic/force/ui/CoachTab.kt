package fr.pgragnic.force.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.pgragnic.force.*
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Onglet Coach (maquette uprep-onglet-coach.png ; Philippe, 15 novembre 10 h 30 : « faire un onglet à part qui conserverait les échanges
// entre l'utilisateur et l'IA, journal « Ce que le coach a changé » et peut-être plus » ; 10 h 42 : « On fait le tout »).

private fun dayTxt(ms: Long): String {
    val z = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    val d = z.toLocalDate(); val h = z.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (d) { LocalDate.now() -> t("Aujourd'hui $h", "Today $h"); LocalDate.now().minusDays(1) -> t("Hier $h", "Yesterday $h")
        else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM", L.loc)) + " · $h" }
}
private fun srcTxt(s: String) = when (s) { "debrief" -> t("debrief", "debrief"); else -> t("discussion", "chat") }

/** Écran plein (journal, archives, ce que sait le coach, données envoyées). */
@Composable
private fun Sheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        Column(Modifier.fillMaxSize().background(C.bg)) {
            Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Retour", "Back"), tint = C.ink) }
                Text(title, color = C.ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
private fun SecHead(title: String, action: String? = null, fold: MutableState<Boolean>? = null, onAction: () -> Unit = {}) = Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    // sections repliables (Philippe, 18 novembre 17 h 43 : « Can we collapse sections ? ») : on touche le titre ; état gardé sur le téléphone
    Row(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).then(if (fold != null) Modifier.clickable { fold.value = !fold.value } else Modifier), verticalAlignment = Alignment.CenterVertically) {
        Cap(title)
        fold?.let { val rot by androidx.compose.animation.core.animateFloatAsState(if (it.value) 180f else 0f, label = "fold")
            Icon(Icons.Default.ExpandMore, if (it.value) t("Replier", "Collapse") else t("Déplier", "Expand"), tint = C.muted, modifier = Modifier.padding(start = 2.dp).size(18.dp).graphicsLayer { rotationZ = rot }) }
    }
    action?.let { Text(it, color = C.brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onAction() }.padding(2.dp)) }
}

@Composable
private fun XChip(label: String, onX: () -> Unit) = Row(Modifier.padding(end = 6.dp, bottom = 6.dp).clip(RoundedCornerShape(16.dp)).background(C.brand.copy(alpha = .1f))
    .clickable { onX() }.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, color = C.brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    Icon(Icons.Default.Close, t("Arrêter", "Stop"), tint = C.brand, modifier = Modifier.padding(start = 4.dp).size(15.dp))
}

/** Une décision du journal : pourquoi, ce que ça change, voir la discussion, annuler. */
@Composable
private fun DecisionCard(e: CDecision, full: Boolean, onSee: (Long) -> Unit) {
    val ctx = LocalContext.current
    val active = CoachData.active(e)
    Column(Modifier.fillMaxWidth().glam().then(if (full) Modifier else Modifier.clickable { onSee(-e.id) }).padding(if (full) 14.dp else 12.dp)) {
        Text(dayTxt(e.id) + " · " + srcTxt(e.src) + when { e.undone -> t(" · annulé", " · undone"); !active -> t(" · terminé", " · ended"); else -> "" }, color = C.muted, fontSize = 12.sp)
        Text(e.title, color = if (active) C.ink else C.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
        if (full) {
            if (e.why.isNotBlank()) Text(t("Pourquoi — tu as dit : « ${e.why} »", "Why — you said: “${e.why}”"), color = C.ink, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
            Text(t("Ce que ça change", "What it changes"), color = C.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            e.changes.forEach { Text("• $it", color = C.ink, fontSize = 13.sp, lineHeight = 18.sp) }
            e.until?.let { Text(t("Jusqu'au ${CoachState.fmt(it)} inclus, puis retour normal", "Until ${CoachState.fmt(it)} included, then back to normal"), color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        } else Text(e.changes.take(2).joinToString(" · "), color = C.muted, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        if (full) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(t("Voir la discussion", "See the chat"), color = C.brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSee(e.id) }.padding(2.dp))
            if (active) Text(t("Annuler", "Undo"), color = C.warm, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { CoachData.undo(ctx, e.id) }.padding(2.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CoachTab(onStart: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("force", android.content.Context.MODE_PRIVATE) }
    @Suppress("UNUSED_VARIABLE") val dv = CoachData.v; val cs = CoachState.v; val av = Adapt.v
    val log by Store.log.collectAsState()
    val d = CoachData.d
    var chat by remember { mutableStateOf<Pair<String, Long?>?>(null) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var addGoal by remember { mutableStateOf(false) }
    chat?.let { (m, at) -> CoachScreen(m, onClose = { chat = null }, onStart = { chat = null; onStart() }, at = at) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) { H1(t("Coach", "Coach"), Modifier.weight(1f)) }
        // sans compte : le coach est réservé aux comptes
        val acct by Store.user.collectAsState()
        if (!Store.coachOk(acct)) { CoachLocked(Modifier.padding(top = 14.dp)); return@Column }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, top = 2.dp)) {
            // « N messages restants » (Philippe, 15 novembre 11 h 56 : « Que veux dire 40/40 messages aujourd'hui ? » → « Ok ») ; orange sous 5
            val left = CoachData.leftToday()
            Text(t("$left message${if (left > 1) "s" else ""} restant${if (left > 1) "s" else ""} aujourd'hui", "$left message${if (left != 1) "s" else ""} left today"),
                color = if (left < 5) C.warm else C.muted, fontSize = 13.sp, fontWeight = if (left < 5) FontWeight.SemiBold else FontWeight.Normal)
        }
        // discussion
        val last = d.msgs.lastOrNull()
        Row(Modifier.padding(top = 12.dp).fillMaxWidth().glam().clickable { chat = "libre" to null }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoachAvatar(36); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (last == null) t("Parler au coach", "Talk to the coach") else t("Reprendre la discussion", "Continue the chat"), color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(last?.let { dayTxt(it.t) + " : « " + it.text.take(80) + (if (it.text.length > 80) "…" else "") + " »" } ?: t("Une gêne, peu de temps, des vacances, une rando… il adapte ton programme.", "Discomfort, little time, holidays, a hike… it adapts your program."),
                    color = C.muted, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = C.brand)
        }
        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
            listOf(t("Avant ma séance", "Before my workout") to "avant", t("Debrief de la dernière", "Debrief the last one") to "apres").forEach { (l, m) ->
                Text(l, color = C.brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(16.dp)).background(C.brand.copy(alpha = .1f))
                    .clickable { chat = m to null }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }

        // en cours
        val pend = CoachState.bias.filter { (id, b) -> log.none { e -> e.d > b.t && e.ex.any { it.id == id } } }
        val any = CoachState.genes.isNotEmpty() || CoachState.noGear != null || Adapt.on || pend.isNotEmpty()
        if (any) {
            val fo = rememberFold("c_cours"); val n = CoachState.genes.size + (if (CoachState.noGear != null) 1 else 0) + (if (Adapt.on) 1 else 0) + pend.size
            SecHead(if (fo.value) t("En cours · touche ✕ pour arrêter", "Active · tap ✕ to stop") else t("En cours ($n)", "Active ($n)"), fold = fo)
            if (fo.value) run {
                androidx.compose.foundation.layout.FlowRow {
                    CoachState.genes.forEach { (z, until) -> XChip(PlanText.zoneOff(z) + " → " + CoachState.fmt(until)) { CoachState.endGene(ctx, z) } }
                    CoachState.noGear?.let { XChip(t("Sans matériel → ${CoachState.fmt(it)}", "No equipment → ${CoachState.fmt(it)}")) { CoachState.endNoGear(ctx) } }
                    if (Adapt.on) XChip(t("Séance du jour adaptée", "Today's workout adapted")) { Adapt.clear(ctx) }
                    pend.forEach { (id, b) -> XChip(tr(Prog.EX[id]?.n ?: id) + if (b.kg != null) " → " + Prog.loadTxt(id, Prog.snap(id, b.kg)) else if (b.dir > 0) t(" +1 cran", " +1 step") else t(" −1 cran", " −1 step")) { CoachState.endBias(ctx, id) } }
                }
            }
        }

        // objectifs
        if (d.goals.isEmpty()) Row(Modifier.padding(top = 10.dp).fillMaxWidth().glam().clickable { addGoal = true }.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("Ajouter un objectif", "Add a goal"), color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(t("Rando, poids, course… le coach en tient compte", "Hike, weight, race… the coach takes it into account"), color = C.muted, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.Add, null, tint = C.brand)
        } else {
        val fg = rememberFold("c_obj")
        SecHead(if (fg.value) t("Objectifs", "Goals") else t("Objectifs (${d.goals.size})", "Goals (${d.goals.size})"), t("+ Ajouter", "+ Add"), fg) { addGoal = true }
        if (fg.value) Column(Modifier.fillMaxWidth().glam().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            d.goals.forEach { g ->
                val end = g.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }; val start = runCatching { LocalDate.parse(g.from) }.getOrDefault(LocalDate.now())
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(g.text + (end?.let { " · " + it.format(DateTimeFormatter.ofPattern("d MMM", L.loc)) } ?: ""), color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            end?.let { val n = ChronoUnit.DAYS.between(LocalDate.now(), it); Text(if (n >= 0) t("J−$n", "D−$n") else t("Date passée", "Date passed"), color = C.muted, fontSize = 12.sp) }
                        }
                        Icon(Icons.Default.Close, t("Supprimer", "Delete"), tint = C.muted, modifier = Modifier.size(20.dp).clickable { CoachData.update { it.copy(goals = it.goals - g, removed = it.removed + g.id) } })
                    }
                    end?.let { val tot = ChronoUnit.DAYS.between(start, it).coerceAtLeast(1); val done = ChronoUnit.DAYS.between(start, LocalDate.now()).coerceIn(0, tot)
                        LinearProgressIndicator(progress = { done.toFloat() / tot }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)), color = C.brand, trackColor = C.chip) }
                }
            }
        }

        }
        // journal : la dernière décision seulement, le reste dans « Tout voir »
        val fj = rememberFold("c_journal")
        SecHead(t("Ce que le coach a changé", "What the coach changed"), if (d.journal.size > 1) t("Tout voir (${d.journal.size})", "See all (${d.journal.size})") else null, fj) { sheet = "journal" }
        if (fj.value) { if (d.journal.isEmpty()) Column(Modifier.fillMaxWidth().glam().padding(14.dp)) {
            Text(t("Rien pour l'instant. Chaque changement accepté apparaîtra ici avec sa raison et un bouton pour l'annuler.", "Nothing yet. Each accepted change will show here with its reason and an undo button."), color = C.muted, fontSize = 13.sp) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { d.journal.take(1).forEach { DecisionCard(it, false) { at -> if (at < 0) sheet = "journal" else chat = "libre" to at } } } }

        // bilans
        d.bilans.maxByOrNull { it.key }?.let { b ->
        val fb = rememberFold("c_bilan")
        SecHead(t("Bilan de la semaine", "Weekly review"), t("Archives", "Archive"), fb) { sheet = "bilans" }
        if (fb.value) Column(Modifier.fillMaxWidth().glam().clickable { sheet = "bilans" }.padding(12.dp)) {
            run { Text(t("Semaine du ", "Week of ") + runCatching { LocalDate.parse(b.key).format(DateTimeFormatter.ofPattern("d MMMM", L.loc)) }.getOrDefault(b.key), color = C.muted, fontSize = 12.sp)
                Text(b.value, color = C.ink, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
        }
        }

        Text(t("Ton, rappels et ce que sait le coach : Réglages → Coach.", "Tone, reminders and what the coach knows: Settings → Coach."), color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp, start = 4.dp))
        if (!prefs.getBoolean("coach_ok", false)) Text(t("Le coach utilise Claude, l'IA d'Anthropic. Il te demandera ton accord au premier échange.", "The coach uses Claude, Anthropic's AI. It will ask for your consent at the first chat."),
            color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
    }

    when (sheet) {
        "journal" -> Sheet(t("Ce que le coach a changé", "What the coach changed"), { sheet = null }) {
            var f by remember { mutableIntStateOf(0) }
            Seg(listOf(t("Tout", "All") to (f == 0), t("En cours", "Active") to (f == 1), t("Terminé", "Ended") to (f == 2)), Modifier.fillMaxWidth(), check = false, small = true) { f = it }
            d.journal.filter { when (f) { 1 -> CoachData.active(it); 2 -> !CoachData.active(it); else -> true } }.forEach { DecisionCard(it, true) { at -> sheet = null; chat = "libre" to at } }
        }
        "bilans" -> Sheet(t("Bilans", "Reviews"), { sheet = null }) {
            d.bilans.entries.sortedByDescending { it.key }.forEach { (k, v) -> Column(Modifier.fillMaxWidth().glam().padding(14.dp)) {
                Text(t("Semaine du ", "Week of ") + runCatching { LocalDate.parse(k).format(DateTimeFormatter.ofPattern("d MMMM yyyy", L.loc)) }.getOrDefault(k), color = C.muted, fontSize = 12.sp)
                Text(v, color = C.ink, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp)) } }
        }
    }

    if (addGoal) {
        var txt by remember { mutableStateOf("") }; var date by remember { mutableStateOf<LocalDate?>(null) }; var pick by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { addGoal = false }, containerColor = C.card1,
            title = { Text(t("Nouvel objectif", "New goal"), color = C.ink, fontWeight = FontWeight.Bold) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(txt, { txt = it.take(80) }, placeholder = { Text(t("Ex. : rando de 20 km", "E.g. 20 km hike")) }, singleLine = true)
                TextButton(onClick = { pick = true }) { Text(date?.let { t("Date : ", "Date: ") + it.format(DateTimeFormatter.ofPattern("d MMMM yyyy", L.loc)) } ?: t("Ajouter une date (facultatif)", "Add a date (optional)"), color = C.brand) }
            } },
            confirmButton = { TextButton(enabled = txt.isNotBlank(), onClick = {
                val g = CGoal(System.currentTimeMillis(), txt.trim(), date?.toString(), LocalDate.now().toString())
                CoachData.update { it.copy(goals = it.goals + g) }; addGoal = false }) { Text(t("Ajouter", "Add"), color = C.brand, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { addGoal = false }) { Text(t("Annuler", "Cancel"), color = C.muted) } })
        if (pick) {
            val st = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis() + 30 * 864e5.toLong())
            DatePickerDialog(onDismissRequest = { pick = false }, confirmButton = { TextButton(onClick = {
                st.selectedDateMillis?.let { date = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate() }; pick = false }) { Text("OK") } }) { DatePicker(st) }
        }
    }
}


/** Réglages du coach, dans Réglages → Coach (Philippe, 15 novembre 12 h 08 : « Déplace les "réglages du coach" dans réglages »). */
@Composable
fun CoachSettingsPage() {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val dv = CoachData.v
    val d = CoachData.d
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Cap(t("Ce que sait le coach", "What the coach knows"), Modifier.padding(start = 4.dp))
            val g = GearStore.g.value
            Text(t("Envoyé à chaque message. Les préférences retenues se suppriment avec ✕ ; le profil, le matériel et la santé se changent dans Réglages → Mon programme.",
                "Sent with each message. Saved preferences can be removed with ✕; profile, equipment and health are changed in Settings → My program."), color = C.muted, fontSize = 13.sp)
            Column(Modifier.fillMaxWidth().glam().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                @Composable fun L1(a: String, b: String) = Row { Text(a, color = C.ink, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text(b, color = C.muted, fontSize = 12.sp) }
                L1(tr(Gear.LEVELS.getOrNull(g.level ?: -1) ?: "—") + t(" · ${g.perWeek} séances / semaine", " · ${g.perWeek} workouts / week"), t("profil", "profile"))
                val names = remember { Prog.raw["EQ"]?.jsonArray?.flatMap { c -> c.jsonArray[1].jsonArray.map { it.jsonArray } }?.associate { it[0].jsonPrimitive.content to it[1].jsonPrimitive.content } ?: emptyMap() }
                L1(g.items.joinToString(", ") { tr(names[it] ?: it) }.ifBlank { t("Poids du corps", "Bodyweight") }, t("matériel", "equipment"))
                L1(Gear.LIMITS.filter { it.first in g.limits }.joinToString(", ") { tr(it.second) }.ifBlank { t("Aucune zone à ménager", "No area to protect") }, t("santé", "health"))
                d.goals.forEach { L1(it.text, t("objectif", "goal")) }
            }
            Cap(t("Préférences retenues", "Saved preferences"), Modifier.padding(top = 8.dp, start = 4.dp))
            Column(Modifier.fillMaxWidth().glam().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (d.memo.isEmpty()) Text(t("Le coach retient ce que tu lui dis de durable (« je n'aime pas les burpees »). Rien pour l'instant.", "The coach keeps what you tell it that lasts (“I don't like burpees”). Nothing yet."), color = C.muted, fontSize = 13.sp)
                d.memo.forEach { m -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("« ${m.text} »", color = C.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Close, t("Retirer", "Remove"), tint = C.muted, modifier = Modifier.size(20.dp).clickable { CoachData.update { it.copy(memo = it.memo - m, removed = it.removed + m.id) } }) } }
                var nm by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(nm, { nm = it.take(120) }, Modifier.weight(1f), placeholder = { Text(t("Ajouter une préférence…", "Add a preference…")) }, singleLine = true)
                    IconButton(onClick = { if (nm.isNotBlank()) { val x = nm.trim(); CoachData.update { it.copy(memo = it.memo + CMemo(System.currentTimeMillis(), x, "moi")) }; nm = "" } }) { Icon(Icons.Default.Add, t("Ajouter", "Add"), tint = C.brand) }
                }
            }
        
        Cap(t("Ton du coach", "Coach's tone"), Modifier.padding(top = 8.dp, start = 4.dp))
        val tons = listOf("encourageant" to t("Encourageant", "Encouraging"), "neutre" to t("Neutre", "Neutral"), "direct" to t("Direct", "Direct"))
        Seg(tons.map { it.second to (d.ton == it.first) }, Modifier.fillMaxWidth(), check = false, small = true) { i -> CoachData.update { it.copy(ton = tons[i].first) } }
        Cap(t("Rappels du coach", "Coach reminders"), Modifier.padding(top = 8.dp, start = 4.dp))
        Column(Modifier.fillMaxWidth().glam().padding(horizontal = 14.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Debrief 30 min après la séance", "Debrief 30 min after the workout"), color = C.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(d.remDebrief, { v -> CoachData.update { it.copy(remDebrief = v) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Suggestion du matin (8 h)", "Morning suggestion (8 am)"), color = C.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(d.remMorning, { v -> CoachData.update { it.copy(remMorning = v) }; CoachRem.schedule(ctx) })
            }
        }
        // « Ce que le coach reçoit », en clair (Philippe, 20 novembre 5 h 59 : « Enlevons le donnés envoyées, qu'est ce que tu en penses ou alors
        // sur un format lisible par un end user ? » ; 6 h 00 : « Ça me va ! ») : le lien du haut de l'onglet Coach est retiré, le détail technique (JSON) est retiré le 25 novembre
        Cap(t("Ce que le coach reçoit", "What the coach receives"), Modifier.padding(top = 8.dp, start = 4.dp))
        Column(Modifier.fillMaxWidth().glam().padding(14.dp)) {
            val o = remember { runCatching { CoachData.lastSent?.let { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject } }.getOrNull() ?: runCatching { CoachApi.context() }.getOrNull() }
            Text(t("Avec chacun de tes messages, le coach reçoit ces informations pour adapter ses réponses :", "With each of your messages, the coach receives this information to tailor its answers:"), color = C.muted, fontSize = 13.sp)
            o?.let { SentReadable(it) }
            Text(t("Jamais envoyé : ton nom, ton e-mail, ta position, tes contacts.", "Never sent: your name, e-mail, location, contacts."), color = C.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            Text(t("Les échanges passent par Anthropic (Claude), qui ne s'en sert pas pour entraîner ses modèles.", "Messages go through Anthropic (Claude), which does not use them to train its models."), color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Contexte envoyé au coach, en phrases simples (une ligne par rubrique). */
@Composable
private fun SentReadable(o: kotlinx.serialization.json.JsonObject) {
    fun ob(k: String) = o[k] as? kotlinx.serialization.json.JsonObject
    fun ar(k: String, src: kotlinx.serialization.json.JsonObject? = o) = (src?.get(k) as? kotlinx.serialization.json.JsonArray) ?: kotlinx.serialization.json.JsonArray(emptyList())
    fun st(src: kotlinx.serialization.json.JsonObject?, k: String) = (src?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString || it.toString() != "null" }?.content
    fun dm(d: String?) = d?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d/MM")) }.getOrNull() } ?: ""
    val pr = ob("profil"); val pg = ob("programme"); val wk = ob("semaine_du_programme_en_cours")
    val rest = ar("restrictions_sante", pr).size
    val lines = buildList {
        add(t("Ton profil", "Your profile") to listOfNotNull(st(pr, "objectif")?.let { t("objectif ", "goal ") + it }, st(pr, "niveau")?.let { t("niveau ", "level ") + it },
            if (rest == 0) t("aucune restriction de santé", "no health restriction") else t("$rest restriction(s) de santé", "$rest health restriction(s)")).joinToString(" · "))
        add(t("Ton matériel", "Your equipment") to t("${ar("materiel").size} élément(s) coché(s)", "${ar("materiel").size} item(s) selected"))
        if (pg != null) add(t("Ton programme", "Your program") to listOfNotNull(t("cycle ${st(pg, "cycle")}, semaine ${st(pg, "semaine")}/8", "cycle ${st(pg, "cycle")}, week ${st(pg, "semaine")}/8"),
            st(o, "seance_proposee")?.let { t("prochaine séance $it", "next workout $it") }, st(pg, "duree_max_habituelle")?.let { t("$it min au maximum", "$it min max") }).joinToString(" · "))
        if (wk != null) add(t("Ta semaine", "Your week") to (t("faites : ", "done: ") + ar("seances_faites", wk).joinToString(", ") { val e = it as kotlinx.serialization.json.JsonObject; "${st(e, "seance")} (${dm(st(e, "date"))})" }.ifBlank { "—" }
            + t(" · restantes : ", " · left: ") + ar("seances_restantes", wk).joinToString(", ") { (it as kotlinx.serialization.json.JsonPrimitive).content }.ifBlank { "—" }))
        ar("dernieres_seances").let { add(t("Tes dernières séances", "Your last workouts") to t("${it.size} séance(s) : exercices, charges, répétitions, ressenti", "${it.size} workout(s): exercises, loads, reps, how it felt")) }
        add(t("Ton corps", "Your body") to t("récupération de chaque muscle, équilibre des 4 dernières semaines", "recovery of each muscle, balance over the last 4 weeks"))
        ar("activite_3_jours").takeIf { it.isNotEmpty() }?.let { add(t("Ton activité", "Your activity") to t("pas des ${it.size} derniers jours", "steps over the last ${it.size} days")) }
        add(t("Vos échanges", "Your conversations") to t("${ar("echanges_30_derniers_jours").size} message(s) et ${ar("decisions_30_derniers_jours").size} décision(s) des 30 derniers jours",
            "${ar("echanges_30_derniers_jours").size} message(s) and ${ar("decisions_30_derniers_jours").size} decision(s) from the last 30 days"))
        add(t("Ce que tu lui as confié", "What you told it") to t("${ar("objectifs").size} objectif(s), ${ar("ce_que_tu_sais_de_l_utilisateur").size} préférence(s) retenue(s)",
            "${ar("objectifs").size} goal(s), ${ar("ce_que_tu_sais_de_l_utilisateur").size} remembered preference(s)"))
    }
    lines.forEach { (k, v) ->
        Column(Modifier.padding(top = 10.dp)) {
            Text(k, color = C.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(v, color = C.muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
