package fr.pgragnic.force.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.pgragnic.force.*
import kotlinx.coroutines.launch

// Écran du coach (maquette du parcours, Philippe : « Ok pour maquette du parcours ») : faire le point avant la séance
// (trois questions à réponses rapides), puis Claude propose la séance adaptée ; la conversation peut continuer librement.

private class Msg(val coach: Boolean, val text: String, val chips: List<String> = emptyList(), val plan: CoachPlan? = null)

/** questions posées par l'appli (sans appel à l'API) selon le moment ; « libre » : aucune */
private fun scripted(mode: String) = when (mode) {
    "avant" -> QUESTIONS
    "apres" -> listOf(t("Bravo pour ta séance ! C'était comment ?", "Well done! How was it?") to listOf(t("Trop facile", "Too easy"), t("Juste bien", "Just right"), t("Trop dur", "Too hard"), t("Une gêne", "Some discomfort")))
    else -> emptyList()
}
private fun opener(mode: String) = when (mode) {
    "avant" -> t("Je fais le point avant ma séance.", "Quick check-in before my workout.")
    "apres" -> t("Je viens de finir ma séance.", "I just finished my workout.")
    else -> null
}
private val QUESTIONS get() = listOf(
    t("Comment tu te sens aujourd'hui ?", "How do you feel today?") to listOf(t("En forme", "Great"), t("Un peu fatigué", "A bit tired"), t("Courbatures", "Sore"), t("Mal dormi", "Slept badly")),
    t("Tu as combien de temps ?", "How much time do you have?") to listOf("20 min", "30 min", "45 min", t("Plus", "More")),
    t("Une gêne quelque part ?", "Any discomfort?") to listOf(t("Aucune", "None"), t("Genou", "Knee"), t("Dos", "Back"), t("Épaule", "Shoulder")))

/** Pastille ronde du coach. */
@Composable
fun CoachAvatar(size: Int = 34) = Box(Modifier.size(size.dp).clip(CircleShape).background(C.brand), contentAlignment = Alignment.Center) {
    Icon(Icons.Default.AutoAwesome, null, tint = C.onBrand, modifier = Modifier.size((size * .55f).dp))
}

@Composable
fun CoachScreen(mode: String = "avant", onClose: () -> Unit, onStart: () -> Unit) {
    val Q = remember(mode) { scripted(mode) }
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("force", android.content.Context.MODE_PRIVATE) }
    var consent by remember { mutableStateOf(prefs.getBoolean("coach_ok", false)) }
    val msgs = remember { mutableStateListOf(if (Q.isNotEmpty()) Msg(true, Q[0].first, Q[0].second) else Msg(true,
        t("Je t'écoute : une gêne qui dure, des vacances sans matériel, peu de temps, une rando à préparer ?", "I'm listening: lasting discomfort, holidays without equipment, little time, a hike to prepare?"),
        listOf(t("Une gêne qui dure", "Lasting discomfort"), t("Je pars sans matériel", "I'm away without equipment"), t("Séance express", "Quick workout"), t("Préparer une rando", "Prepare a hike")))) }
    // historique envoyé à l'API : commence par l'utilisateur, puis alterne
    val hist = remember { mutableStateListOf<Pair<String, String>>().also { h -> opener(mode)?.let { h += "user" to it; h += "assistant" to Q[0].first } } }
    var step by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size, busy) { list.animateScrollToItem(max0(msgs.size - 1 + if (busy) 1 else 0)) }

    fun ask() {
        busy = true
        scope.launch {
            val r = CoachApi.ask(hist.toList(), mode)
            busy = false
            if (r.erreur != null) { msgs += Msg(true, CoachApi.errText(r.erreur)); hist.removeAt(hist.size - 1); return@launch }
            hist += "assistant" to (r.texte + (r.plan?.explications?.takeIf { it.isNotEmpty() }?.let { "\n" + t("Plan proposé : ", "Proposed plan: ") + it.joinToString(" ; ") } ?: ""))
            msgs += Msg(true, r.texte, r.choix, r.plan)
        }
    }
    fun send(txt: String) {
        val s = txt.trim(); if (s.isEmpty() || busy) return
        msgs += Msg(false, s); hist += "user" to s; input = ""
        if (step < Q.size - 1) {
            // questions posées par l'appli, sans appel à l'API
            step++; val (q, c) = Q[step]; msgs += Msg(true, q, c); hist += "assistant" to q
        } else { step = Q.size; ask() }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(C.bg).systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Retour", "Back"), tint = C.ink) }
                CoachAvatar(); Spacer(Modifier.width(10.dp))
                Text(t("Coach", "Coach"), color = C.ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                if (Adapt.on) TextButton(onClick = { Adapt.clear(ctx); msgs += Msg(true, t("J'ai remis la séance habituelle.", "I've restored the usual workout.")) }) {
                    Text(t("Séance habituelle", "Usual workout"), color = C.brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
            // adaptations qui durent plusieurs jours, avec ✕ pour les arrêter
            val cs = CoachState.v
            val running = remember(cs) { CoachState.genes.map { (z, d) -> z to t("${Gear.LIMITS.firstOrNull { it.first == z }?.second ?: z} ménagé jusqu'au ${CoachState.fmt(d)}", "${z} protected until ${CoachState.fmt(d)}") } +
                listOfNotNull(CoachState.noGear?.let { "" to t("Sans matériel jusqu'au ${CoachState.fmt(it)}", "No equipment until ${CoachState.fmt(it)}") }) }
            if (running.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                running.forEach { (z, l) -> Row(Modifier.clip(RoundedCornerShape(16.dp)).background(C.chip).clickable { if (z.isEmpty()) CoachState.endNoGear(ctx) else CoachState.endGene(ctx, z) }.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) { Text(l, color = C.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium); Icon(Icons.Default.Close, t("Arrêter", "Stop"), tint = C.muted, modifier = Modifier.padding(start = 4.dp).size(14.dp)) } }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(msgs) { m ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.coach) Alignment.Start else Alignment.End) {
                        if (m.text.isNotBlank()) Text(m.text, color = if (m.coach) C.ink else C.onBrand, fontSize = 15.sp, lineHeight = 21.sp,
                            modifier = Modifier.fillMaxWidth(.86f).wrapContentWidth(if (m.coach) Alignment.Start else Alignment.End)
                                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (m.coach) 6.dp else 20.dp, bottomEnd = if (m.coach) 20.dp else 6.dp))
                                .background(if (m.coach) C.card1 else C.brand).padding(horizontal = 14.dp, vertical = 10.dp))
                        m.plan?.let { p -> PlanCard(p, onApply = { start ->
                            Adapt.apply(ctx, p)
                            if (mode == "apres") Store.log.value.maxByOrNull { it.d }?.let { prefs.edit().putLong("coachdebrief", it.d).apply() }
                            if (start && p.repos != true && p.today) { onClose(); onStart() }
                            else msgs += Msg(true, if (p.today) t("C'est noté, ta séance du jour est prête.", "Done, today's workout is ready.") else t("C'est noté pour tes prochaines séances.", "Noted for your next workouts."))
                        }, onAdjust = { input = t("Je préfère ", "I'd rather ") }) }
                        if (m.chips.isNotEmpty() && m === msgs.last()) Row(Modifier.padding(top = 6.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            m.chips.forEach { c -> Text(c, color = C.brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(C.brand.copy(alpha = .1f)).clickable(enabled = !busy) { send(c) }.padding(horizontal = 12.dp, vertical = 8.dp)) }
                        }
                    }
                }
                if (busy) item { Text(t("Le coach réfléchit…", "The coach is thinking…"), color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp)) }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(26.dp)).background(C.card1).padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicTextField(input, { input = it.take(500) }, Modifier.weight(1f).padding(vertical = 14.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = C.ink, fontSize = 15.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(C.brand),
                    decorationBox = { inner -> if (input.isEmpty()) Text(t("Écris au coach…", "Message the coach…"), color = C.muted, fontSize = 15.sp); inner() })
                Box(Modifier.size(40.dp).clip(CircleShape).background(if (input.isBlank() || busy) C.chip else C.brand).clickable(enabled = input.isNotBlank() && !busy) { send(input) },
                    contentAlignment = Alignment.Center) { Icon(Icons.Default.ArrowUpward, t("Envoyer", "Send"), tint = if (input.isBlank() || busy) C.muted else C.onBrand) }
            }
        }
        // accord avant le premier échange : données envoyées à Anthropic
        if (!consent) AlertDialog(onDismissRequest = onClose, containerColor = C.card1,
            title = { Text(t("Le coach UpRep", "The UpRep coach"), color = C.ink, fontWeight = FontWeight.Bold) },
            text = { Text(t("Le coach utilise Claude, l'IA d'Anthropic. Pour adapter tes séances, l'appli lui envoie tes réponses, ton profil, ton matériel, tes restrictions de santé, tes dernières séances, la récupération de tes muscles et tes pas. Le coach ne remplace pas un avis médical.",
                "The coach uses Claude, Anthropic's AI. To adapt your workouts, the app sends it your answers, profile, equipment, health restrictions, recent workouts, muscle recovery and steps. The coach does not replace medical advice."), color = C.muted) },
            confirmButton = { TextButton(onClick = { prefs.edit().putBoolean("coach_ok", true).apply(); consent = true }) { Text(t("J'accepte", "I agree"), color = C.brand, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = onClose) { Text(t("Plus tard", "Later"), color = C.muted) } })
    }
}

private fun max0(i: Int) = if (i < 0) 0 else i

@Composable
private fun PlanCard(p: CoachPlan, onApply: (Boolean) -> Unit, onAdjust: () -> Unit) {
    var done by remember { mutableStateOf(false) }
    val st = Store.st.value
    Column(Modifier.padding(top = 6.dp).fillMaxWidth().glam().padding(16.dp)) {
        Cap(if (p.repos == true || !p.today) t("Proposition du coach", "Coach's suggestion") else t("Séance adaptée", "Adapted workout"))
        val s = p.seance ?: st.s
        if (p.repos != true && p.today) Text(t("Séance $s · ", "Workout $s · ") + tr(Prog.sess(st.c)[s]?.t ?: ""), color = C.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
        p.explications.forEach { e ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(C.brand.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, tint = C.brand, modifier = Modifier.size(16.dp)) }
                Text(e, color = C.ink, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(start = 10.dp, top = 3.dp))
            }
        }
        if (!done && !p.today && p.repos != true) Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).heightIn(min = 46.dp).clip(RoundedCornerShape(23.dp)).background(C.brand).clickable { done = true; onApply(false) }, contentAlignment = Alignment.Center) {
                Text(t("Appliquer", "Apply"), color = C.onBrand, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            Box(Modifier.weight(1f).heightIn(min = 46.dp).clip(RoundedCornerShape(23.dp)).background(C.chip).clickable { onAdjust() }, contentAlignment = Alignment.Center) {
                Text(t("Ajuster", "Adjust"), color = C.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
        } else if (!done) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(C.brand).clickable { done = true; onApply(true) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (p.repos != true) { Icon(Icons.Default.PlayArrow, null, tint = C.onBrand); Spacer(Modifier.width(6.dp)) }
                Text(if (p.repos == true) t("D'accord", "OK") else t("C'est parti", "Let's go"), color = C.onBrand, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (p.repos != true) Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(C.chip).clickable { done = true; onApply(false) }, contentAlignment = Alignment.Center) {
                    Text(t("Appliquer", "Apply"), color = C.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(C.chip).clickable { onAdjust() }, contentAlignment = Alignment.Center) {
                    Text(t("Ajuster", "Adjust"), color = C.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            }
        }
    }
}
