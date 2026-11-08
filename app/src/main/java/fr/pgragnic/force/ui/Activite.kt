package fr.pgragnic.force.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import fr.pgragnic.force.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Dépenses estimées (mêmes formules que la page web). */
object Kcal {
    fun bwNow(log: List<Entry>, t: Long? = null): Double { var b = 74.0; log.sortedBy { it.d }.forEach { e -> if (e.bw != null && (t == null || e.d <= t)) b = e.bw }; return b }
    fun day(d: DayRec, log: List<Entry>): Int {
        val bw = bwNow(log, Recovery.dayTs(d)); val load = bw + (d.pack ?: 0.0); val steps = (d.steps ?: 0).toDouble()
        var k = steps * GearStore.g.value.stride() / 1000 * 0.5 * load
        if (steps == 0.0 && d.act != null && d.dur != null) k = (mapOf("Trail" to 9.0, "Randonnée" to 6.0, "Marche lestée" to 5.0)[d.act] ?: 5.0) * load * d.dur / 60
        k += (d.dplus ?: 0) * load * 9.81 / 0.25 / 4184
        return k.roundToInt()
    }
    fun entry(e: Entry, log: List<Entry>): Double {
        e.kcal?.let { return it }
        return runCatching { val st = Settings(); Prog.sessKcal(Prog.phases(e.c, e.w, e.s, e.rest ?: 30, st, emptyList()), e.bw ?: bwNow(log, e.d)) }.getOrDefault(0.0)
    }
    fun kr(k: Double) = "≈ " + String.format(fr.pgragnic.force.L.loc, "%,d", k.roundToInt()) + " kcal"
    fun range(k: Double) = "${(k * .75 / 10).roundToInt() * 10}–${(k * 1.25 / 10).roundToInt() * 10} kcal"
}

private val ACTS = listOf("Aucune", "Marche lestée", "Randonnée", "Trail")

@Composable
fun ActiviteScreen() {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val days by Store.days.collectAsState(); val log by Store.log.collectAsState()
    val hcStatus by Health.status.collectAsState(); val granted by Health.granted.collectAsState()
    val avail = remember { Health.available(ctx) }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { g ->
        if (g.containsAll(Health.PERMS)) scope.launch { Health.importSteps(ctx) } else Health.status.value = "Accès aux pas refusé : tu peux saisir tes pas à la main."
    }
    LaunchedEffect(Unit) { if (avail) Health.importSteps(ctx) }

    val today = LocalDate.now()
    var sel by remember { mutableStateOf(today) }
    val byD = days.associateBy { it.date }
    val cur = byD[sel.toString()]
    var steps by remember(sel, cur) { mutableStateOf(cur?.steps?.takeIf { it > 0 }?.toString() ?: "") }
    var act by remember(sel, cur) { mutableStateOf(cur?.act ?: "Aucune") }
    var pack by remember(sel, cur) { mutableStateOf(cur?.pack?.let { Prog.fk(it) } ?: "") }
    var dur by remember(sel, cur) { mutableStateOf(cur?.dur?.toString() ?: "") }
    var dplus by remember(sel, cur) { mutableStateOf(cur?.dplus?.toString() ?: "") }
    var msg by remember { mutableStateOf("Au-delà de 8 000 pas, la marche compte dans la récupération des jambes. Le sac et le dénivelé augmentent la fatigue.") }

    val last14 = (13 downTo 0).map { today.minusDays(it.toLong()) }
    val last7 = last14.drop(7).mapNotNull { byD[it.toString()]?.steps?.takeIf { s -> s > 0 } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text("Pas & activités", fontSize = 28.sp, fontWeight = FontWeight.Black, color = C.ink)
            Text(if (last7.isNotEmpty()) t("Moyenne sur 7 jours : ", "7-day average: ") + String.format(fr.pgragnic.force.L.loc, "%,d", last7.average().roundToInt()) + t(" pas par jour", " steps per day") else "Aucune journée saisie", color = C.muted, fontSize = 13.sp)
        }

        // Health Connect
        Panel {
            Text("Pas automatiques", color = C.ink, fontWeight = FontWeight.Bold)
            when {
                !avail -> {
                    Text("Installe ou mets à jour l'appli Health Connect pour importer tes pas (téléphone, montre, Google Fit, Samsung Santé…).", color = C.muted, fontSize = 13.sp)
                    TextButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))) } }) { Text("Ouvrir le Play Store") }
                }
                !granted -> {
                    Text("Autorise la lecture de tes pas dans Health Connect : ils arriveront tout seuls ici chaque jour.", color = C.muted, fontSize = 13.sp)
                    Button(onClick = { launcher.launch(Health.PERMS) }, modifier = Modifier.padding(top = 6.dp)) { Text("Autoriser l'accès aux pas") }
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(hcStatus.ifBlank { "Connecté à Health Connect" }, color = C.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { Health.importSteps(ctx) } }) { Text("Actualiser") }
                }
            }
            if (!granted && hcStatus.isNotBlank()) Text(hcStatus, color = C.warm, fontSize = 12.sp)
        }

        // 14 jours
        val mx = max(10000, last14.maxOf { byD[it.toString()]?.steps ?: 0 })
        Panel {
            Text("14 derniers jours · touche un jour pour le modifier", color = C.muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                last14.forEach { d ->
                    val r = byD[d.toString()]; val v = r?.steps ?: 0; val on = d == sel
                    Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(if (on) C.line else C.surface).clickable { sel = d },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text(if (v >= 1000) "${(v / 1000.0).roundToInt()}k" else if (v > 0) "<1k" else "", color = if (on) C.ink else C.muted, fontSize = 9.sp, maxLines = 1, softWrap = false)
                        val col = if (r?.act != null) C.warm else if (d == today) C.effort else C.rest
                        Canvas(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 3.dp)) {
                            val h = if (v > 0) max(3f, size.height * v / mx) else 0f
                            drawRoundRect(col, Offset(0f, size.height - h), Size(size.width, h), CornerRadius(4f, 4f))
                        }
                        Text(t("lmmjvsd", "mtwtfss")[d.dayOfWeek.value - 1].toString(), color = if (on) C.ink else C.muted, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Pas" to C.rest, "Aujourd'hui" to C.effort, "Avec activité" to C.warm).forEach { (t, c) ->
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(c)); Text(" $t", color = C.muted, fontSize = 11.sp) }
                }
            }
        }

        // Saisie
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { sel = sel.minusDays(1) }) { Icon(Icons.Default.ChevronLeft, "Jour précédent", tint = C.ink) }
                Text(sel.format(DateTimeFormatter.ofPattern("EEEE d MMMM", fr.pgragnic.force.L.loc)).replaceFirstChar { it.uppercase() } + if (sel == today) t(" (aujourd'hui)", " (today)") else "",
                    color = C.ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (sel < today) sel = sel.plusDays(1) }, enabled = sel < today) { Icon(Icons.Default.ChevronRight, "Jour suivant", tint = if (sel < today) C.ink else C.line) }
            }
            if (cur?.src == "hc") Text(t("${String.format(fr.pgragnic.force.L.loc, "%,d", cur.steps ?: 0)} pas importés depuis Health Connect", "${String.format(fr.pgragnic.force.L.loc, "%,d", cur.steps ?: 0)} steps imported from Health Connect"), color = C.muted, fontSize = 12.sp)
            val num = KeyboardOptions(keyboardType = KeyboardType.Number); val dec = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            OutlinedTextField(steps, { steps = it.filter { c -> c.isDigit() } }, label = { Text("Pas") }, placeholder = { Text("ex. 7000") }, singleLine = true, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
            Text("Activité", color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ACTS.forEach { a -> Box(Modifier.weight(1f)) { ActChip(a, act == a) { act = a } } } }
            if (act != "Aucune") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedTextField(pack, { pack = it }, label = { Text("Sac (kg)") }, singleLine = true, keyboardOptions = dec, modifier = Modifier.weight(1f))
                    OutlinedTextField(dur, { dur = it.filter { c -> c.isDigit() } }, label = { Text("Durée (min)") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                    OutlinedTextField(dplus, { dplus = it.filter { c -> c.isDigit() } }, label = { Text("D+ (m)") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                }
            }
            Button(onClick = {
                val a = act.takeIf { it != "Aucune" }
                val d = DayRec(sel.toString(), steps.toIntOrNull() ?: 0, a, if (a != null) pack.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } else null,
                    if (a != null) dplus.toIntOrNull()?.takeIf { it > 0 } else null, if (a != null) dur.toIntOrNull()?.takeIf { it > 0 } else null,
                    src = if (cur?.src == "hc" && (steps.toIntOrNull() ?: 0) == cur.steps) "hc" else "main")
                Store.saveDay(d)
                val L = Recovery.dayLoad(d)
                msg = "Journée du " + sel.format(DateTimeFormatter.ofPattern("EEEE d MMMM", fr.pgragnic.force.L.loc)) + " enregistrée." +
                    (if (L > 0) t(" Elle compte pour environ ${Prog.fk(Prog.r1(L))} séries dans la fatigue des quadriceps.", " It counts for about ${Prog.fk(Prog.r1(L))} sets of quad fatigue.") else " Pas assez de pas pour fatiguer les jambes.") +
                    t(" Dépense estimée : ${Kcal.kr(Kcal.day(d, log).toDouble())}.", " Estimated burn: ${Kcal.kr(Kcal.day(d, log).toDouble())}.")
            }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Enregistrer la journée") }
            Text(msg, color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }

        // Dépense
        val t0 = System.currentTimeMillis() - 7 * 864e5.toLong()
        val ses7 = log.filter { it.ex.isNotEmpty() && it.d >= t0 }
        val ks = ses7.sumOf { Kcal.entry(it, log) }
        val kd = days.filter { Recovery.dayTs(it) >= t0 }.sumOf { Kcal.day(it, log) }.toDouble()
        val tot = ks + kd
        Panel {
            Text("Dépense estimée · 7 jours", color = C.muted, fontSize = 12.sp)
            Text(Kcal.kr(tot), color = C.ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(Kcal.range(tot), color = C.muted, fontSize = 12.sp)
            Row(Modifier.padding(top = 10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(t("Séances (${ses7.size})", "Sessions (${ses7.size})"), color = C.muted, fontSize = 12.sp); Text(Kcal.kr(ks), color = C.effort, fontWeight = FontWeight.Bold)
                    Text(t("${if (tot > 0) (100 * ks / tot).roundToInt() else 0} % du total", "${if (tot > 0) (100 * ks / tot).roundToInt() else 0}% of total"), color = C.muted, fontSize = 12.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("Pas & activités", color = C.muted, fontSize = 12.sp); Text(Kcal.kr(kd), color = C.rest, fontWeight = FontWeight.Bold)
                    Text(t("${if (tot > 0) (100 * kd / tot).roundToInt() else 0} % du total", "${if (tot > 0) (100 * kd / tot).roundToInt() else 0}% of total"), color = C.muted, fontSize = 12.sp)
                }
            }
            if (tot > 0) Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp))) {
                if (ks > 0) Box(Modifier.weight(ks.toFloat()).fillMaxHeight().background(C.effort))
                if (kd > 0) Box(Modifier.weight(kd.toFloat()).fillMaxHeight().background(C.rest))
            }
            Text("Estimation à ± 25 % de la dépense pendant l'activité, d'après ton poids du jour, la durée des séances, les pas, le sac et le dénivelé.", color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            val gr by GearStore.g.collectAsState()
            val rest = gr.bmr(if (log.any { it.bw != null }) Kcal.bwNow(log) else gr.bw)
            HorizontalDivider(color = C.line, modifier = Modifier.padding(vertical = 10.dp))
            if (rest != null) {
                Text(t("Au repos : ${Kcal.kr(rest)} par jour", "At rest: ${Kcal.kr(rest)} per day"), color = C.ink, fontWeight = FontWeight.Bold)
                Text(t("Soit ${Kcal.kr(rest * 7)} sur 7 jours, ${Kcal.kr(rest * 7 + tot)} en tout avec l'activité. Métabolisme de base estimé d'après ta taille, ton âge, ton sexe et ton poids (formule de Mifflin-St Jeor).",
                    "That is ${Kcal.kr(rest * 7)} over 7 days, ${Kcal.kr(rest * 7 + tot)} in total with activity. Basal metabolic rate estimated from your height, age, sex and weight (Mifflin-St Jeor equation)."), color = C.muted, fontSize = 12.sp)
            } else Text(t("Renseigne ta taille, ton âge et ton sexe dans Infos → Matériel et profil pour voir aussi ta dépense au repos.", "Add your height, age and sex in Info → Equipment and profile to also see your resting burn."), color = C.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ActChip(t: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (on) C.ink else C.bg).clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 2.dp), contentAlignment = Alignment.Center) {
        Text(t, color = if (on) C.bg else C.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
    }
}
