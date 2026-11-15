package fr.pgragnic.force.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pgragnic.force.*
import kotlinx.coroutines.launch

/** Équilibre des muscles sur 4 semaines + rattrapage prévu cette semaine. */
@Composable
fun BalancePanel() {
    val log by Store.log.collectAsState()
    val R = remember(log) { Balance.compute(log) }
    val W = remember(log) { Balance.weekRes() }
    val lagPairs = W.pairs.filter { it.state != 0 }
    val fold = rememberFold("equilibre")
    Panel {
        FoldHead(t("Équilibre des muscles", "Muscle balance"), if (fold.value) t("Séries des 4 dernières semaines, par paires opposées", "Sets over the last 4 weeks, by opposing pairs")
            else if (R.ok) t("Équilibré", "Balanced") else t("À surveiller", "Worth a look"), fold)
        if (!fold.value) return@Panel
        if (R.sets.values.sum() < 2) { Text(t("Fais quelques séances pour voir l'équilibre.", "Do a few workouts to see the balance."), color = C.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)); return@Panel }
        R.pairs.forEach { p ->
            val col = if (p.state == 0) C.brand else C.warm
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(p.a, color = if (p.state == -1) C.warm else C.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(p.b, color = if (p.state == 1) C.warm else C.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(RoundedCornerShape(4.dp))) {
                val s = p.share.toFloat().coerceIn(.05f, .95f)
                Box(Modifier.weight(s).fillMaxHeight().background(col))
                Spacer(Modifier.width(2.dp))
                Box(Modifier.weight(1f - s).fillMaxHeight().background(col.copy(alpha = .35f)))
            }
            p.advice?.let { Text(it, color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
        }
        if (R.low.isNotEmpty()) Text(t("Peu travaillés : ", "Little work: ") + R.low.joinToString(", ") { mus(it) } + ".", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        HorizontalDivider(color = C.line, modifier = Modifier.padding(vertical = 12.dp))
        Text(t("Cette semaine", "This week"), color = C.muted, fontSize = 12.sp)
        if (!(lagPairs.isEmpty() && W.low.isNotEmpty())) Text(if (lagPairs.isEmpty()) t("Rien à rattraper : tes deux dernières semaines sont équilibrées.", "Nothing to catch up: your last two weeks are balanced.")
            else t("Le programme rattrape : +1 série sur les exercices de ", "The program catches up: +1 set on exercises for ") + lagPairs.joinToString(", ") { (if (it.state == -1) it.a else it.b).lowercase() } + ".",
            color = C.ink, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        if (W.low.isNotEmpty()) Text((if (lagPairs.isEmpty()) t("Le programme rattrape pour ", "The program catches up for ") else t("Et pour ", "And for ")) + W.low.joinToString(", ") { mus(it).lowercase() } + t(" : 1 série de plus, ou un petit exercice de 2 séries en fin de séance s'il n'y en a pas, jusqu'à 2 séries faites dans la semaine.", ": 1 more set, or a short 2-set exercise at the end of a workout if there is none, until 2 sets are done this week."),
            color = C.ink, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** « Le mot du coach » sur la semaine (bilan commenté par Claude, à la demande). */
@Composable
fun CoachWeekPanel() {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val key = remember { java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY).toString() }
    var txt by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { txt = CoachApi.bilan(ctx, key, false) }
    val fold = rememberFold("coach")
    Panel {
        FoldHead(t("Le mot du coach", "Coach's word"), null, fold) { CoachAvatar(30) }
        if (!fold.value) return@Panel
        txt?.let { Text(it, color = C.ink, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp)) }
        if (busy) Text(t("Le coach regarde ta semaine…", "The coach is looking at your week…"), color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        else Box(Modifier.padding(top = 12.dp).clip(RoundedCornerShape(20.dp)).background(if (txt == null) C.brand else C.chip).clickable {
            if (!ctx.getSharedPreferences("force", android.content.Context.MODE_PRIVATE).getBoolean("coach_ok", false)) { txt = t("Ouvre d'abord le coach depuis l'écran Séance pour l'activer.", "First open the coach from the Workout screen to turn it on."); return@clickable }
            busy = true; scope.launch { txt = CoachApi.bilan(ctx, key, true); busy = false }
        }.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(if (txt == null) t("Demander au coach", "Ask the coach") else t("Actualiser", "Refresh"), color = if (txt == null) C.onBrand else C.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

/** Section repliable (Philippe, 14 novembre 19 h 10 : « Dans l'onglet semaine peux-tu faire des sections qui se replient ? »). État gardé sur le téléphone. */
@Composable
fun rememberFold(id: String): MutableState<Boolean> {
    val p = LocalContext.current.getSharedPreferences("force", android.content.Context.MODE_PRIVATE)
    val s = remember(id) { mutableStateOf(p.getBoolean("fold_$id", true)) }
    LaunchedEffect(s.value) { p.edit().putBoolean("fold_$id", s.value).apply() }
    return s
}
@Composable
fun FoldHead(title: String, sub: String?, open: MutableState<Boolean>, lead: (@Composable () -> Unit)? = null) {
    val rot by androidx.compose.animation.core.animateFloatAsState(if (open.value) 180f else 0f, label = "fold")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open.value = !open.value }, verticalAlignment = Alignment.CenterVertically) {
        lead?.let { it(); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = C.ink, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            sub?.let { Text(it, color = C.muted, fontSize = 12.sp) }
        }
        Icon(androidx.compose.material.icons.Icons.Default.ExpandMore, if (open.value) t("Replier", "Collapse") else t("Déplier", "Expand"), tint = C.muted,
            modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rot })
    }
}
