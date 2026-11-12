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
        val MET = mapOf("Trail" to 9.0, "Randonnée" to 6.0, "Marche lestée" to 5.0)
        if (d.outs.isNotEmpty()) {
            // sorties : les pas de la journée au poids du corps, puis le sac seulement pendant chaque sortie
            val stride = GearStore.g.value.stride(); var k = steps * stride / 1000 * 0.5 * bw
            d.outs.forEach { o -> val p = o.pack ?: 0.0
                k += if (steps > 0) o.dur * (if (o.act == "Trail") 150 else 100) * stride / 1000 * 0.5 * p else (MET[o.act] ?: 5.0) * (bw + p) * o.dur / 60
                k += (o.dplus ?: 0) * (bw + p) * 9.81 / 0.25 / 4184 }
            return k.roundToInt()
        }
        var k = steps * GearStore.g.value.stride() / 1000 * 0.5 * load
        if (steps == 0.0 && d.act != null && d.dur != null) k = (MET[d.act] ?: 5.0) * load * d.dur / 60
        k += (d.dplus ?: 0) * load * 9.81 / 0.25 / 4184
        return k.roundToInt()
    }
    fun entry(e: Entry, log: List<Entry>): Double {
        e.kcal?.let { return it }
        return runCatching { val st = Settings(); Prog.sessKcal(Prog.phases(e.c, e.w, e.s, e.rest ?: 30, st, emptyList()), e.bw ?: bwNow(log, e.d)) }.getOrDefault(0.0)
    }
    /** Dépense affichée en fourchette (estimation à ± 25 %) plutôt qu'en chiffre précis, qui ferait croire à une mesure. */
    fun kr(k: Double): String {
        fun f(x: Double) = String.format(fr.pgragnic.force.L.loc, "%,d", ((x / 10).roundToInt() * 10))
        return if (k < 40) "≈ ${f(k)} kcal" else "${f(k * .75)}–${f(k * 1.25)} kcal"
    }
    fun range(k: Double) = "${(k * .75 / 10).roundToInt() * 10}–${(k * 1.25 / 10).roundToInt() * 10} kcal"
}

private val ACTS = listOf("Marche lestée", "Randonnée", "Trail")
private fun hm(min: Int) = if (min >= 60) "${min / 60} h ${"%02d".format(min % 60)}" else "$min min"

@Composable
fun ActiviteScreen(inProgres: Boolean = false) {
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
    // nouvelle sortie (saisie à la main) ; le sac proposé est celui de la dernière sortie
    val lastPack = remember(days) { days.flatMap { it.sorties() }.lastOrNull { (it.pack ?: 0.0) > 0 }?.pack }
    var act by remember(sel) { mutableStateOf("Marche lestée") }
    var pack by remember(sel, lastPack) { mutableStateOf(lastPack?.let { Prog.fk(it) } ?: "") }
    var dur by remember(sel) { mutableStateOf("") }
    var dplus by remember(sel) { mutableStateOf("") }
    var hour by remember(sel) { mutableStateOf("") }
    val ruck by Ruck.run.collectAsState()
    var msg by remember { mutableStateOf("Au-delà de 8 000 pas, la marche compte dans la récupération des jambes. Le sac et le dénivelé augmentent la fatigue.") }

    val last14 = (13 downTo 0).map { today.minusDays(it.toLong()) }
    val last7 = last14.drop(7).mapNotNull { byD[it.toString()]?.steps?.takeIf { s -> s > 0 } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            if (!inProgres) Text("Pas & activités", fontSize = 28.sp, fontWeight = FontWeight.Black, color = C.ink)
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

        // chrono « sac sur le dos » (Philippe : « 1,2 et 3 », point 2)
        Panel {
            Text(t("Sac sur le dos", "Pack on"), color = C.ink, fontWeight = FontWeight.Bold)
            val r = ruck
            if (r == null) {
                Text(t("Démarre quand tu mets le sac, arrête quand tu l'enlèves : la sortie s'ajoute toute seule à la journée. Autant de fois que tu veux.",
                    "Start when you put the pack on, stop when you take it off: the outing is added to the day automatically. As many times as you like."), color = C.muted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) { ACTS.forEach { a -> Box(Modifier.weight(1f)) { ActChip(a, act == a) { act = a } } } }
                OutlinedTextField(pack, { pack = it }, label = { Text(t("Sac (kg)", "Pack (kg)")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                Button(onClick = { Ruck.start(ctx, act, pack.replace(',', '.').toDoubleOrNull() ?: 0.0) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(t("Démarrer", "Start")) }
            } else {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(r) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
                val sec = ((now - r.start) / 1000).coerceAtLeast(0)
                Text(String.format("%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60), color = C.effort, fontSize = 40.sp, fontWeight = FontWeight.Black)
                Text(fr.pgragnic.force.tr(r.act) + (if (r.pack > 0) " · ${Prog.fk(r.pack)} kg" else "") + t(" · depuis ", " · since ") +
                    java.time.Instant.ofEpochMilli(r.start).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")), color = C.muted, fontSize = 13.sp)
                Button(onClick = { val o = Ruck.stop(ctx); if (o != null) { sel = today; msg = t("Sortie enregistrée : ${hm(o.dur)}", "Outing saved: ${hm(o.dur)}") + (o.pack?.let { " · ${Prog.fk(it)} kg" } ?: "") + "." } },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(t("Arrêter et enregistrer", "Stop and save")) }
                var arm by remember { mutableStateOf(false) }
                TextButton(onClick = { if (!arm) arm = true else { Ruck.stop(ctx, save = false); arm = false } }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (arm) t("Confirmer : annuler sans enregistrer", "Confirm: cancel without saving") else t("Annuler", "Cancel"), color = C.danger) }
            }
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
                        val col = if (r?.act != null || r?.outs?.isNotEmpty() == true) C.warm else if (d == today) C.effort else C.rest
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
            // temps passé avec le sac sur 7 jours
            val o7 = last14.drop(7).flatMap { d -> byD[d.toString()]?.sorties().orEmpty() }.filter { (it.pack ?: 0.0) > 0 }
            if (o7.isNotEmpty()) Text(t("7 derniers jours : ${o7.size} sortie${if (o7.size > 1) "s" else ""} avec sac, ${hm(o7.sumOf { it.dur })} au total, ",
                "Last 7 days: ${o7.size} outing${if (o7.size > 1) "s" else ""} with a pack, ${hm(o7.sumOf { it.dur })} in total, ") +
                o7.mapNotNull { it.pack }.let { p -> if (p.min() == p.max()) "${Prog.fk(p.min())} kg" else "${Prog.fk(p.min())} à ${Prog.fk(p.max())} kg" } + ".", color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }

        // Saisie
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { sel = sel.minusDays(1) }) { Icon(Icons.Default.ChevronLeft, fr.pgragnic.force.tr("Jour précédent"), tint = C.ink) }
                Text(sel.format(DateTimeFormatter.ofPattern("EEEE d MMMM", fr.pgragnic.force.L.loc)).replaceFirstChar { it.uppercase() } + if (sel == today) t(" (aujourd'hui)", " (today)") else "",
                    color = C.ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (sel < today) sel = sel.plusDays(1) }, enabled = sel < today) { Icon(Icons.Default.ChevronRight, fr.pgragnic.force.tr("Jour suivant"), tint = if (sel < today) C.ink else C.line) }
            }
            if (cur?.src == "hc") Text(t("${String.format(fr.pgragnic.force.L.loc, "%,d", cur.steps ?: 0)} pas importés depuis Health Connect", "${String.format(fr.pgragnic.force.L.loc, "%,d", cur.steps ?: 0)} steps imported from Health Connect"), color = C.muted, fontSize = 12.sp)
            val num = KeyboardOptions(keyboardType = KeyboardType.Number); val dec = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            OutlinedTextField(steps, { steps = it.filter { c -> c.isDigit() } }, label = { Text("Pas") }, placeholder = { Text("ex. 7000") }, singleLine = true, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
            fun dayMsg(d: DayRec): String { val L = Recovery.dayLoad(d); val B = Recovery.backLoad(d)
                return (if (L > 0) t(" Elle compte pour environ ${Prog.fk(Prog.r1(L))} séries dans la fatigue des quadriceps", " It counts for about ${Prog.fk(Prog.r1(L))} sets of quad fatigue") else t(" Pas assez de pas pour fatiguer les jambes", " Not enough steps to tire the legs")) +
                    (if (B > 0) t(", ${Prog.fk(Prog.r1(B * .6))} pour les lombaires", ", ${Prog.fk(Prog.r1(B * .6))} for the lower back") else "") + "." +
                    t(" Dépense estimée : ${Kcal.kr(Kcal.day(d, log).toDouble())}.", " Estimated burn: ${Kcal.kr(Kcal.day(d, log).toDouble())}.") }
            Button(onClick = {
                val base = cur ?: DayRec(sel.toString())
                val d = base.copy(steps = steps.toIntOrNull() ?: 0, src = if (cur?.src == "hc" && (steps.toIntOrNull() ?: 0) == cur.steps) "hc" else "main")
                Store.saveDay(if (d.outs.isEmpty() && d.act != null) d.withOuts(d.sorties()) else d)
                msg = t("Pas du ", "Steps for ") + sel.format(DateTimeFormatter.ofPattern("EEEE d MMMM", fr.pgragnic.force.L.loc)) + t(" enregistrés.", " saved.") + dayMsg(d)
            }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(t("Enregistrer les pas", "Save steps")) }

            // sorties de la journée (Philippe : « 1,2 et 3 », point 1) : chacune avec son sac et sa durée
            val outs = cur?.sorties().orEmpty()
            Text(t("Sorties de la journée", "Outings of the day"), color = C.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            if (outs.isEmpty()) Text(t("Aucune sortie ce jour-là.", "No outing that day."), color = C.muted, fontSize = 13.sp)
            outs.forEachIndexed { i, o ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(C.bg).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text((o.h?.let { it.replace(":", " h ") + " · " } ?: "") + fr.pgragnic.force.tr(o.act), color = C.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(listOfNotNull(o.dur.takeIf { it > 0 }?.let { hm(it) }, o.pack?.let { t("sac ${Prog.fk(it)} kg", "pack ${Prog.fk(it)} kg") }, o.dplus?.let { "D+ $it m" }).joinToString(" · "), color = C.muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { val d = cur!!.withOuts(outs.filterIndexed { j, _ -> j != i }); Store.saveDay(d); msg = t("Sortie supprimée.", "Outing deleted.") + dayMsg(d) }) { Text(t("Retirer", "Remove"), color = C.danger, fontSize = 12.sp) }
                }
            }
            Text(t("Ajouter une sortie", "Add an outing"), color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ACTS.forEach { a -> Box(Modifier.weight(1f)) { ActChip(a, act == a) { act = a } } } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                OutlinedTextField(pack, { pack = it }, label = { Text(t("Sac (kg)", "Pack (kg)")) }, singleLine = true, keyboardOptions = dec, modifier = Modifier.weight(1f))
                OutlinedTextField(dur, { dur = it.filter { c -> c.isDigit() } }, label = { Text(t("Durée (min)", "Time (min)")) }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                OutlinedTextField(dplus, { dplus = it.filter { c -> c.isDigit() } }, label = { Text("D+ (m)") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                OutlinedTextField(hour, { hour = it.filter { c -> c.isDigit() || c == ':' || c == 'h' }.take(5) }, label = { Text(t("Heure", "Time of day")) }, placeholder = { Text("12:30") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
            }
            Button(onClick = {
                val m = dur.toIntOrNull()
                if (m == null || m <= 0) { msg = t("Indique la durée de la sortie.", "Enter the outing time."); return@Button }
                val hh = hour.replace('h', ':').split(':').let { p -> p.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..23 }?.let { h -> "%02d:%02d".format(h, p.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0) } }
                    ?: if (sel == today) java.time.LocalTime.now().minusMinutes(m.toLong()).format(DateTimeFormatter.ofPattern("HH:mm")) else null
                val o = Out(hh, act, pack.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }, m, dplus.toIntOrNull()?.takeIf { it > 0 })
                val base = (cur ?: DayRec(sel.toString(), steps.toIntOrNull() ?: 0, src = "main"))
                val d = base.withOuts(base.sorties() + o); Store.saveDay(d)
                dur = ""; dplus = ""; hour = ""
                msg = t("Sortie ajoutée.", "Outing added.") + dayMsg(d)
            }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(t("Ajouter la sortie", "Add the outing")) }
            // suppression d'une journée saisie à la main (les pas importés de Health Connect reviendraient à l'import suivant)
            if (cur != null && cur.src != "hc") {
                var arm by remember(sel) { mutableStateOf(false) }
                TextButton(onClick = { if (!arm) arm = true else { Store.deleteDay(sel.toString()); arm = false; msg = t("Journée supprimée.", "Day deleted.") } }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (arm) t("Confirmer la suppression", "Confirm deletion") else t("Supprimer cette journée", "Delete this day"), color = C.danger)
                }
            }
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
            Text(t("Estimation à ± 25 %", "Estimate, ± 25%"), color = C.muted, fontSize = 12.sp)
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
            } else Text(t("Renseigne ta taille, ton âge et ton sexe dans Réglages → Mon programme pour voir aussi ta dépense au repos.", "Add your height, age and sex in Settings → My program to also see your resting burn."), color = C.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ActChip(t: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (on) C.ink else C.bg).clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 2.dp), contentAlignment = Alignment.Center) {
        Text(t, color = if (on) C.bg else C.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
    }
}
