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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.pgragnic.force.*
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
private fun SecHead(title: String, action: String? = null, onAction: () -> Unit = {}) = Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Cap(title, Modifier.weight(1f))
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
    Column(Modifier.fillMaxWidth().glam().padding(14.dp)) {
        Text(dayTxt(e.id) + " · " + srcTxt(e.src) + when { e.undone -> t(" · annulé", " · undone"); !active -> t(" · terminé", " · ended"); else -> "" }, color = C.muted, fontSize = 12.sp)
        Text(e.title, color = if (active) C.ink else C.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
        if (full) {
            if (e.why.isNotBlank()) Text(t("Pourquoi — tu as dit : « ${e.why} »", "Why — you said: “${e.why}”"), color = C.ink, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
            Text(t("Ce que ça change", "What it changes"), color = C.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            e.changes.forEach { Text("• $it", color = C.ink, fontSize = 13.sp, lineHeight = 18.sp) }
            e.until?.let { Text(t("Jusqu'au ${CoachState.fmt(it)} inclus, puis retour normal", "Until ${CoachState.fmt(it)} included, then back to normal"), color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        } else Text(e.changes.take(2).joinToString(" · "), color = C.muted, fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, top = 2.dp)) {
            Text(t("${CoachData.leftToday()} / 40 messages aujourd'hui · ", "${CoachData.leftToday()} / 40 messages today · "), color = C.muted, fontSize = 13.sp)
            Text(t("Données envoyées", "Data sent"), color = C.brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { sheet = "data" })
        }
        // discussion
        val last = d.msgs.lastOrNull()
        Row(Modifier.padding(top = 14.dp).fillMaxWidth().glam().clickable { chat = "libre" to null }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CoachAvatar(36); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (last == null) t("Parler au coach", "Talk to the coach") else t("Reprendre la discussion", "Continue the chat"), color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(last?.let { dayTxt(it.t) + " : « " + it.text.take(80) + (if (it.text.length > 80) "…" else "") + " »" } ?: t("Une gêne, peu de temps, des vacances, une rando… il adapte ton programme.", "Discomfort, little time, holidays, a hike… it adapts your program."),
                    color = C.muted, fontSize = 13.sp, maxLines = 2)
            }
            Icon(Icons.Default.ChevronRight, null, tint = C.brand)
        }
        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
            listOf(t("Faire le point avant ma séance", "Pre-workout check-in") to "avant", t("Debrief de ma dernière séance", "Debrief my last workout") to "apres").forEach { (l, m) ->
                Text(l, color = C.brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(16.dp)).background(C.brand.copy(alpha = .1f))
                    .clickable { chat = m to null }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }

        // en cours
        val pend = CoachState.bias.filter { (id, b) -> log.none { e -> e.d > b.t && e.ex.any { it.id == id } } }
        val any = CoachState.genes.isNotEmpty() || CoachState.noGear != null || Adapt.on || pend.isNotEmpty()
        SecHead(t("En cours", "Active"))
        Column(Modifier.fillMaxWidth().glam().padding(14.dp)) {
            if (!any) Text(t("Rien en ce moment : ton programme suit son cours normal.", "Nothing right now: your program runs as usual."), color = C.muted, fontSize = 13.sp)
            else {
                androidx.compose.foundation.layout.FlowRow {
                    CoachState.genes.forEach { (z, until) -> XChip(PlanText.zoneOff(z) + " → " + CoachState.fmt(until)) { CoachState.endGene(ctx, z) } }
                    CoachState.noGear?.let { XChip(t("Sans matériel → ${CoachState.fmt(it)}", "No equipment → ${CoachState.fmt(it)}")) { CoachState.endNoGear(ctx) } }
                    if (Adapt.on) XChip(t("Séance du jour adaptée", "Today's workout adapted")) { Adapt.clear(ctx) }
                    pend.forEach { (id, b) -> XChip(tr(Prog.EX[id]?.n ?: id) + if (b.dir > 0) t(" +1 cran", " +1 step") else t(" −1 cran", " −1 step")) { CoachState.endBias(ctx, id) } }
                }
                Text(t("Touche une puce pour l'arrêter. Le détail est dans « Ce que le coach a changé ».", "Tap a chip to stop it. Details are in “What the coach changed”."), color = C.muted, fontSize = 12.sp)
            }
        }

        // objectifs
        SecHead(t("Objectifs", "Goals"), t("+ Ajouter", "+ Add")) { addGoal = true }
        Column(Modifier.fillMaxWidth().glam().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (d.goals.isEmpty()) Text(t("Une rando, un poids, une course… Le coach en tient compte dans ses conseils.", "A hike, a weight, a race… The coach takes it into account."), color = C.muted, fontSize = 13.sp)
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

        // journal
        SecHead(t("Ce que le coach a changé", "What the coach changed"), if (d.journal.size > 2) t("Tout voir", "See all") else null) { sheet = "journal" }
        if (d.journal.isEmpty()) Column(Modifier.fillMaxWidth().glam().padding(14.dp)) {
            Text(t("Rien pour l'instant. Chaque changement accepté apparaîtra ici avec sa raison et un bouton pour l'annuler.", "Nothing yet. Each accepted change will show here with its reason and an undo button."), color = C.muted, fontSize = 13.sp) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { d.journal.take(2).forEach { DecisionCard(it, false) { at -> chat = "libre" to at } } }

        // bilans
        SecHead(t("Bilans", "Reviews"), if (d.bilans.size > 1) t("Archives", "Archive") else null) { sheet = "bilans" }
        Column(Modifier.fillMaxWidth().glam().padding(14.dp)) {
            val b = d.bilans.maxByOrNull { it.key }
            if (b == null) Text(t("Le mot du coach sur ta semaine : demande-le dans Progrès → Semaine.", "The coach's word on your week: ask for it in Progress → Week."), color = C.muted, fontSize = 13.sp)
            else { Text(t("Semaine du ", "Week of ") + runCatching { LocalDate.parse(b.key).format(DateTimeFormatter.ofPattern("d MMMM", L.loc)) }.getOrDefault(b.key), color = C.muted, fontSize = 12.sp)
                Text(b.value, color = C.ink, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp)) }
        }

        // réglages du coach
        SecHead(t("Réglages du coach", "Coach settings"))
        Column(Modifier.fillMaxWidth().glam().padding(14.dp)) {
            Row(Modifier.fillMaxWidth().clickable { sheet = "sait" }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("Ce que sait le coach", "What the coach knows"), color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(t("Profil, matériel, santé et ${d.memo.size} préférence(s) retenue(s)", "Profile, equipment, health and ${d.memo.size} saved preference(s)"), color = C.muted, fontSize = 12.sp)
                }
                Icon(Icons.Default.ChevronRight, null, tint = C.muted)
            }
            Text(t("Ton du coach", "Coach's tone"), color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            val tons = listOf("encourageant" to t("Encourageant", "Encouraging"), "neutre" to t("Neutre", "Neutral"), "direct" to t("Direct", "Direct"))
            Seg(tons.map { it.second to (d.ton == it.first) }, Modifier.fillMaxWidth(), check = false, small = true) { i -> CoachData.update { it.copy(ton = tons[i].first) } }
            Text(t("Rappels", "Reminders"), color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Debrief 30 min après la séance", "Debrief 30 min after the workout"), color = C.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(d.remDebrief, { v -> CoachData.update { it.copy(remDebrief = v) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Suggestion du matin (8 h)", "Morning suggestion (8 am)"), color = C.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(d.remMorning, { v -> CoachData.update { it.copy(remMorning = v) }; CoachRem.schedule(ctx) })
            }
        }
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
        "sait" -> Sheet(t("Ce que sait le coach", "What the coach knows"), { sheet = null }) {
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
        }
        "data" -> Sheet(t("Données envoyées", "Data sent"), { sheet = null }) {
            Text(t("Voici, en clair, le dernier contexte envoyé à Anthropic avec ton message. Rien d'autre n'est transmis (ni ton e-mail, ni ton nom).",
                "Here, in plain text, is the last context sent to Anthropic with your message. Nothing else is sent (no e-mail, no name)."), color = C.muted, fontSize = 13.sp)
            Text(CoachData.lastSent ?: t("Aucun échange pour l'instant.", "No exchange yet."), color = C.ink, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card1).horizontalScroll(rememberScrollState()).padding(12.dp))
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
