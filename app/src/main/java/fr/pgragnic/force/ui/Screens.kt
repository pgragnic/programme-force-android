package fr.pgragnic.force.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pgragnic.force.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.surface).border(1.dp, C.line, RoundedCornerShape(16.dp)).padding(14.dp), content = content)
}

@Composable
fun Eyebrow(t: String) = Text(fr.pgragnic.force.tr(t).uppercase(), color = C.muted, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)

@Composable
fun PChip(label: String, on: Boolean, sub: String? = null, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) C.ink else C.surface).border(1.dp, C.line, RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = if (on) C.bg else C.ink, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, color = if (on) C.bg else C.effort, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

/** Pastille de semaine compacte : « S1 » sur une ligne, puis 4 points (séances faites) sur une ligne. */
@Composable
fun WeekChip(w: Int, n: Int, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(if (on) C.ink else C.surface).border(1.dp, C.line, RoundedCornerShape(10.dp))
        .clickable(onClick = onClick).padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(t("S${w + 1}", "W${w + 1}"), color = if (on) C.bg else C.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val c = if (on) C.bg else C.effort
            repeat(4) { i -> Box(Modifier.size(5.dp).clip(CircleShape).then(if (i < n) Modifier.background(c) else Modifier.border(1.dp, c, CircleShape))) }
        }
    }
}

// ---------------- Séance ----------------
@Composable
fun SeanceScreen(onStart: () -> Unit) {
    val st by Store.st.collectAsState(); val log by Store.log.collectAsState()
    Store.ver.collectAsState().value
    val gear by GearStore.g.collectAsState()
    remember(log, gear) { Prog.computeLoads(log, st); 0 }
    val done = remember(log) { log.map { "${it.c}-${it.w}-${it.s}" }.toSet() }
    val S = Prog.sess(st.c); val W = Prog.WEEKS[st.w]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow("Haltères réglables · 4 séances par semaine")
        Text("Programme Force", fontSize = 30.sp, fontWeight = FontWeight.Black, color = C.ink)
        Store.progNote.collectAsState().value.takeIf { it.isNotBlank() }?.let { Text(it, color = C.effort, fontSize = 13.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            (1..2).forEach { c -> PChip("Cycle $c", st.c == c) { Store.setSettings { it.c = c } } }
            Text(if (st.c % 2 == 0) "variantes" else "exercices de base", color = C.muted, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (0 until 8).forEach { w ->
                val n = listOf("A", "B", "C", "D").count { "${st.c}-$w-$it" in done }
                WeekChip(w, n, st.w == w, Modifier.weight(1f)) { Store.setSettings { it.w = w } }
            }
        }
        Eyebrow(t("Cycle ${st.c} · semaine ${st.w + 1} · ${W.t}", "Cycle ${st.c} · week ${st.w + 1} · ${tr(W.t)}"))
        Text(t("${W.s} séries × ${W.r} reps · tempo ${W.tp.take(3).joinToString("-") { Prog.fk(it) }}", "${W.s} sets × ${W.r} reps · tempo ${W.tp.take(3).joinToString("-") { Prog.fk(it) }}"), color = C.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(W.note, color = C.muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            S.keys.forEach { s ->
                val on = st.s == s
                Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { Store.setSettings { it.s = s } }
                    .background(if (on) C.surface else Color.Transparent).padding(8.dp)) {
                    Text(s + if ("${st.c}-${st.w}-$s" in done) " ✓" else "", color = C.ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(S[s]!!.t, color = C.muted, fontSize = 12.sp, lineHeight = 15.sp)
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp).background(if (on) C.effort else C.line))
                }
            }
        }
        val order = remember(st.c, st.w, st.s, log, gear) { Prog.orderEx(st.c, st.s, st.w, st) }
        Panel {
            order.forEachIndexed { i, id ->
                val e = Prog.EX[id]!!; val wk = Prog.work(id, st.w)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = C.muted, modifier = Modifier.width(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.n, color = C.ink, fontWeight = FontWeight.Bold)
                        Text(Prog.load(id, st.w, st) + " · " + e.tip, color = C.muted, fontSize = 12.sp, lineHeight = 15.sp)
                    }
                    Text("${W.s} × ${wk.label}", color = C.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                if (i < order.size - 1) HorizontalDivider(color = C.line)
            }
        }
        val P = remember(st.c, st.w, st.s, st.rest, log) { Prog.phases(st.c, st.w, st.s, st.rest, st, log) }
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp))
            Text(t("Démarrer la séance ${st.s} · ${Prog.fmt(P.sumOf { it.dur })}", "Start workout ${st.s} · ${Prog.fmt(P.sumOf { it.dur })}"), fontWeight = FontWeight.Black, fontSize = 17.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pause entre séries", color = C.muted, modifier = Modifier.weight(1f))
            listOf(30, 45, 60, 90).forEach { r -> Box(Modifier.padding(start = 4.dp)) { PChip("${r}s", st.rest == r) { Store.setSettings { it.rest = r } } } }
        }
        Text(Store.sync.collectAsState().value, color = C.muted, fontSize = 12.sp)
    }
}

// ---------------- Fin de séance ----------------
class EdRow(val id: String, val n: String, val target: Int, val unit: String, val kg: Double, val sets: SnapshotStateList<SetRec>, var f: MutableState<String>)

fun rowsFor(ses: Session, st: Settings): List<EdRow> {
    val ids = ses.P.filter { it.k == "work" && it.set == 1 }.mapNotNull { it.ex }
    return ids.map { id ->
        val wk = Prog.work(id, ses.w); val time = Prog.EX[id]!!.m == "time"; val tgt = if (time) wk.dur.toInt() else wk.reps
        val got = ses.sets[id] ?: mutableListOf()
        val sets = mutableStateListOf<SetRec>().apply { (0 until ses.nsetsOf(id)).forEach { add(got.getOrNull(it) ?: SetRec(tgt, null)) } }
        EdRow(id, Prog.EX[id]!!.n, tgt, if (time) "s" else "reps", if (Prog.hasDb(id)) Prog.kgOf(id, ses.w, st) else 0.0, sets, mutableStateOf(Prog.feelOf(sets, tgt)))
    }
}

@Composable
fun SetsEditor(rows: List<EdRow>) {
    rows.forEach { r ->
        Panel(Modifier.padding(vertical = 4.dp)) {
            Text(r.n, color = C.ink, fontWeight = FontWeight.Bold)
            Text((if (r.kg > 0) Prog.loadTxt(r.id, r.kg) + " · " else "") + t("objectif ${r.target} ${if (r.unit == "s") "s" else "reps"}", "goal ${r.target} ${if (r.unit == "s") "s" else "reps"}"), color = C.muted, fontSize = 12.sp)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.sets.forEachIndexed { i, x ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).border(1.dp, C.line, RoundedCornerShape(10.dp)).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("S${i + 1}", color = C.muted, fontSize = 11.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("−", color = C.ink, fontSize = 20.sp, modifier = Modifier.clickable { r.sets[i] = x.copy(r = maxOf(0, x.r - 1)); r.f.value = Prog.feelOf(r.sets, r.target) }.padding(horizontal = 6.dp))
                            Text("${x.r}", color = C.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("+", color = C.ink, fontSize = 20.sp, modifier = Modifier.clickable { r.sets[i] = x.copy(r = x.r + 1); r.f.value = Prog.feelOf(r.sets, r.target) }.padding(horizontal = 6.dp))
                        }
                        Text("R " + (x.res?.let { if (it >= 3) "3+" else "$it" } ?: "–"), color = C.muted, fontSize = 12.sp,
                            modifier = Modifier.clickable { val nv = when (x.res) { null -> 0; 0 -> 1; 1 -> 2; 2 -> 3; else -> null }; r.sets[i] = x.copy(res = nv); r.f.value = Prog.feelOf(r.sets, r.target) }.padding(4.dp))
                    }
                }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Facile", "Juste bien", "Difficile").forEach { v -> Box(Modifier.weight(1f)) { PChip(v, r.f.value == v) { r.f.value = v } } }
            }
        }
    }
}

fun recsOf(rows: List<EdRow>) = rows.map { r -> ExRec(r.id, r.n, r.kg, r.target, r.unit, r.sets.toList(), r.sets.minOf { it.r }, r.f.value) }
fun overall(ex: List<ExRec>): String { val nF = ex.count { it.f == "Facile" }; val nD = ex.count { it.f == "Difficile" }; return if (nD > ex.size / 2.0) "Difficile" else if (nF > ex.size / 2.0) "Facile" else "Juste bien" }

@Composable
fun EndScreen(ses: Session, onDone: () -> Unit) {
    val st = Store.st.value
    val rows = remember { rowsFor(ses, st) }
    var bw by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(C.bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Eyebrow(t("Séance ${ses.s} terminée", "Workout ${ses.s} complete"))
        Text("Bravo !", color = C.ink, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("Chaque série telle que notée pendant les pauses : répétitions et réserve (R, touche pour changer). Corrige si besoin.", color = C.muted, modifier = Modifier.padding(vertical = 8.dp))
        SetsEditor(rows)
        OutlinedTextField(bw, { bw = it }, label = { Text("Poids du jour (facultatif, kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Button(onClick = {
            val ex = recsOf(rows); val b = bw.replace(',', '.').toDoubleOrNull()
            val e = Entry(d = System.currentTimeMillis(), c = ses.c, w = ses.w, s = ses.s, f = overall(ex), rest = st.rest, ex = ex, bw = b, kcal = Prog.sessKcal(ses.P, b ?: 80.0))
            Store.add(e)
            val order = listOf("A", "B", "C", "D"); val ni = order.indexOf(ses.s)
            Store.setSettings { if (ni < 3) it.s = order[ni + 1] else if (it.w < 7) { it.w++; it.s = "A" } else { it.c++; it.w = 0; it.s = "A" } }
            onDone()
        }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Text("Enregistrer la séance", fontWeight = FontWeight.Black) }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Quitter sans enregistrer", color = C.muted) }
    }
}

// ---------------- Suivi ----------------
@Composable
fun SuiviScreen() {
    val log by Store.log.collectAsState()
    var open by remember { mutableStateOf<Long?>(null) }
    var edit by remember { mutableStateOf<Long?>(null) }
    var arm by remember { mutableStateOf<Long?>(null) }
    val df = remember { SimpleDateFormat("EEE d MMM", fr.pgragnic.force.L.loc) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Suivi", fontSize = 28.sp, fontWeight = FontWeight.Black, color = C.ink)
            val wk = log.count { it.d > System.currentTimeMillis() - 7 * 86400000L }
            Text(t("${log.size} séances au total · $wk ces 7 derniers jours", "${log.size} sessions in total · $wk in the last 7 days"), color = C.muted, modifier = Modifier.padding(bottom = 6.dp))
        }
        item { BilanPanel() }
        item { ProgressPanel() }
        item { Box(Modifier.padding(top = 8.dp)) { Eyebrow("Historique") } }
        items(log.sortedByDescending { it.d }, key = { it.d }) { e ->
            Panel {
                Row(Modifier.fillMaxWidth().clickable { open = if (open == e.d) null else e.d; edit = null }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("C${e.c} · S${e.w + 1} · Séance ${e.s} · ${e.f}", "C${e.c} · W${e.w + 1} · Workout ${e.s} · ${tr(e.f ?: "")}"), color = C.ink, fontWeight = FontWeight.Bold)
                        val ups = Prog.ups[e.d]; if (!ups.isNullOrEmpty()) Text(ups.joinToString(" · "), color = C.effort, fontSize = 12.sp)
                    }
                    Text(df.format(Date(e.d)), color = C.muted, fontSize = 13.sp)
                }
                if (open == e.d) {
                    if (edit == e.d) {
                        val rows = remember(e.d) { e.ex.map { x -> val ss = mutableStateListOf<SetRec>().apply { addAll(if (x.sets.isNotEmpty()) x.sets else List(Prog.WEEKS[e.w].s) { SetRec(x.done) }) }; EdRow(x.id, x.n, x.target, x.unit, x.kg, ss, mutableStateOf(x.f)) } }
                        SetsEditor(rows)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { val ex = recsOf(rows); Store.update(e.copy(ex = ex, f = overall(ex))); edit = null }) { Text("Enregistrer") }
                            OutlinedButton(onClick = { edit = null }) { Text("Annuler") }
                        }
                    } else {
                        e.ex.forEach { x ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(x.n + if (x.kg > 0) " · " + Prog.loadTxt(x.id, x.kg) else "", color = C.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text((if (x.sets.isNotEmpty()) x.sets.joinToString(" · ") { s -> "${s.r}" + (s.res?.let { " R" + if (it >= 3) "3+" else "$it" } ?: "") } else t("pire série ${x.done}", "worst set ${x.done}")) + " / ${x.target}", color = C.muted, fontSize = 13.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            if (e.ex.isNotEmpty()) OutlinedButton(onClick = { edit = e.d }) { Text("Modifier") }
                            val armed = arm == e.d
                            Button(onClick = { if (armed) { Store.delete(e); arm = null } else arm = e.d },
                                colors = ButtonDefaults.buttonColors(containerColor = if (armed) C.danger else C.surface, contentColor = if (armed) Color.White else C.danger)) {
                                Text(if (armed) "Confirmer la suppression" else "Supprimer")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Infos ----------------
@Composable
fun InfosScreen() {
    val user by Store.user.collectAsState(); val st by Store.st.collectAsState()
    var email by remember { mutableStateOf("") }; var pw by remember { mutableStateOf("") }; var pw2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }; var create by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Infos", fontSize = 28.sp, fontWeight = FontWeight.Black, color = C.ink)
        Panel {
            Text("Compte et sauvegarde en ligne", color = C.ink, fontWeight = FontWeight.Bold)
            if (user != null) {
                Text(t("Connecté : $user", "Signed in: $user"), color = C.muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Store.sync.collectAsState().value, color = C.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { Store.pull() }) { Text("Actualiser", fontSize = 12.sp) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { scope.launch { Store.signOut() } }, contentPadding = PaddingValues(0.dp)) { Text("Se déconnecter", color = C.muted) }
                    Spacer(Modifier.weight(1f))
                    var armDel by remember { mutableStateOf(false) }
                    TextButton(onClick = { if (!armDel) armDel = true else scope.launch { msg = Store.deleteAccount(); armDel = false } }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (armDel) "Confirmer : tout effacer" else "Supprimer mon compte", color = C.danger)
                    }
                }
                Text("La suppression efface définitivement ton compte et toutes tes données, en ligne et sur ce téléphone.", color = C.muted, fontSize = 11.sp)
                msg?.let { Text(it, color = C.danger, fontSize = 13.sp) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    PChip("J'ai un compte", !create) { create = false; msg = null }
                    PChip("Créer un compte", create) { create = true; msg = null }
                }
                OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                val eye = @Composable { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (show) "Masquer le mot de passe" else "Afficher le mot de passe") } }
                OutlinedTextField(pw, { pw = it }, label = { Text("Mot de passe") }, singleLine = true, trailingIcon = eye,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                if (create) {
                    OutlinedTextField(pw2, { pw2 = it }, label = { Text("Confirmer le mot de passe") }, singleLine = true, trailingIcon = eye,
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = pw2.isNotEmpty() && pw2 != pw, supportingText = { if (pw2.isNotEmpty() && pw2 != pw) Text("Les deux mots de passe sont différents") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    Text("6 caractères minimum.", color = C.muted, fontSize = 12.sp)
                }
                Button(onClick = {
                    msg = when {
                        email.isBlank() || pw.isEmpty() -> "Renseigne l'e-mail et le mot de passe."
                        create && pw.length < 6 -> "Le mot de passe doit faire au moins 6 caractères."
                        create && pw != pw2 -> "Les deux mots de passe sont différents."
                        else -> { scope.launch { msg = Store.signIn(email.trim(), pw, create) ?: "Connecté" }; "…" }
                    }
                }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(if (create) "Créer le compte" else "Se connecter") }
                msg?.let { Text(it, color = C.muted, fontSize = 13.sp) }
            }
        }
        FeedbackPanel()
        RappelPanel()
        ThemePanel()
        LegalPanel()
        GearPanel()
        Panel {
            Text("Comment ça marche", color = C.ink, fontWeight = FontWeight.Bold)
            Text("8 semaines, 4 séances (A, B, C, D), adaptées à ton matériel. Pendant chaque pause, note les répétitions faites et combien tu aurais encore pu en faire : la charge monte quand c'est réussi plusieurs fois, et baisse après deux échecs.", color = C.muted)
            Text(t("Version ${BuildConfig.VERSION_NAME}. Personnage : MakeHuman (domaine public, CC0).", "Version ${BuildConfig.VERSION_NAME}. Character: MakeHuman (public domain, CC0)."), color = C.muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun Soon(title: String, txt: String) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black, color = C.ink)
        Panel(Modifier.padding(top = 12.dp)) { Text(txt, color = C.muted) }
    }
}

/** Rappel de séance : jours et heure. */
@Composable
fun RappelPanel() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var c by remember { mutableStateOf(Reminder.get(ctx)) }
    val perm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    fun upd(n: Reminder.Cfg) { c = n; Reminder.set(ctx, n) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Rappel de séance", color = C.ink, fontWeight = FontWeight.Bold)
                Text(if (c.on) t("Notification à ${c.h} h ${c.m.toString().padStart(2, '0')} les jours cochés, sauf si tu t'es déjà entraîné ce jour-là", "Notification at ${c.h}:${c.m.toString().padStart(2, '0')} on the selected days, unless you already trained that day") else "Désactivé", color = C.muted, fontSize = 12.sp)
            }
            Switch(c.on, { on ->
                if (on && android.os.Build.VERSION.SDK_INT >= 33) runCatching { perm.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                upd(c.copy(on = on))
            })
        }
        if (c.on) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                t("LMMJVSD", "MTWTFSS").forEachIndexed { i, l ->
                    val on = (c.days shr i) and 1 == 1
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) C.ink else C.bg).clickable { upd(c.copy(days = c.days xor (1 shl i))) }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        Text(l.toString(), color = if (on) C.bg else C.muted, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Heure", color = C.muted, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { upd(c.copy(h = (c.h + 23) % 24)) }) { Text("−") }
                Text("${c.h} h ${c.m.toString().padStart(2, '0')}", color = C.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp).clickable { upd(c.copy(m = (c.m + 15) % 60)) })
                OutlinedButton(onClick = { upd(c.copy(h = (c.h + 1) % 24)) }) { Text("+") }
            }
            Text("Touche les minutes pour avancer de 15 min.", color = C.muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun ThemePanel() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Panel {
        Text("Langue", color = C.ink, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("auto" to "Comme le téléphone", "fr" to "Français", "en" to "English").forEach { (m, tx) ->
                val on = fr.pgragnic.force.L.mode == m
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) C.ink else C.bg).clickable { fr.pgragnic.force.L.save(ctx, m) }.padding(vertical = 10.dp, horizontal = 4.dp), contentAlignment = Alignment.Center) {
                    Text(tx, color = if (on) C.bg else C.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
        Text("Apparence", color = C.ink, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("auto" to "Comme le téléphone", "light" to "Clair", "dark" to "Sombre").forEach { (m, t) ->
                val on = C.mode == m
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) C.ink else C.bg).clickable { C.save(ctx, m) }.padding(vertical = 10.dp, horizontal = 4.dp), contentAlignment = Alignment.Center) {
                    Text(t, color = if (on) C.bg else C.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Nouveautés de l'appli, la plus récente en premier. */
val NEWS = listOf(
    "8 novembre 2026" to listOf(
        "L'appli parle anglais : elle suit la langue du téléphone, ou ton choix dans Infos → Langue.",
        "Voix de séance, notification et rappel en anglais quand l'appli est en anglais."),
    "7 novembre 2026, 23 h 45" to listOf(
        "Premier lancement guidé : ton matériel, tes charges disponibles, les zones à ménager, ton objectif, ton niveau et les zones à travailler (Infos → Matériel et profil).",
        "77 exercices : haltères, kettlebell, barre, élastiques, traction, dips, banc, roue, chaise, mur, serviette, medicine ball, ballon, Bosu, poids du corps ; les séances se composent avec ton matériel.",
        "Échauffement à la corde ou au bâton, retour au calme au rouleau ou à la balle selon ton matériel.",
        "Ton avis compte : envoie une idée ou signale un problème depuis l'onglet Infos.",
        "Personnage 3D : les exercices penchés en avant (rowing, soulevé de terre, pompes…) ne sont plus dessinés cambrés en arrière."),
    "7 novembre 2026, 21 h 40" to listOf(
        "Pendant la séance, les exercices sont regroupés par poids d'haltères pour changer les disques le moins possible.",
        "Le programme (exercices, séries, consignes) se met à jour tout seul depuis ta base en ligne, sans réinstaller l'appli.",
        "Avertissement santé au premier lancement, politique de confidentialité et suppression du compte depuis l'appli."),
    "7 novembre 2026, soir" to listOf(
        "Personnage 3D réaliste pendant la séance, avec les muscles travaillés en rouge ; fais-le tourner du doigt.",
        "Onglet Corps : récupération de chaque muscle, de face et de dos, et le détail par muscle.",
        "Onglet Activité : pas importés automatiquement via Health Connect, saisie des marches lestées, randos et trails, graphique sur 14 jours et dépense estimée.",
        "Notification de séance avec le minuteur et les boutons Précédent, Pause, Suivant, aussi sur l'écran verrouillé ; la séance continue écran éteint.",
        "Rappel de séance les jours et à l'heure de ton choix.",
        "Suivi : bilan de la semaine, courbes de poids et de charges, volume par semaine.",
        "Thème clair, sombre ou comme le téléphone.",
        "Semaines S1 à S8 sur une seule ligne, avec les points de séances."),
    "7 novembre 2026, 18 h 55" to listOf(
        "Création de compte : mot de passe visible et à confirmer.",
        "Le lien de confirmation par e-mail ouvre l'appli.",
        "Synchronisation automatique."),
    "7 novembre 2026" to listOf("Première version de l'appli : séance guidée avec voix, bips et vibrations, historique synchronisé en ligne."),
)

@Composable
fun NewsPanel() {
    var all by remember { mutableStateOf(false) }
    Panel {
        Text("Nouveautés", color = C.ink, fontWeight = FontWeight.Bold)
        (if (all) NEWS else NEWS.take(1)).forEach { (d, items) ->
            Text(d, color = C.effort, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            items.forEach { Text("• $it", color = C.ink, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp, start = 4.dp)) }
        }
        if (NEWS.size > 1) TextButton(onClick = { all = !all }, contentPadding = PaddingValues(0.dp)) { Text(if (all) "Moins" else "Versions précédentes") }
        Text(t("Version ${fr.pgragnic.force.BuildConfig.VERSION_NAME} · programme ", "Version ${fr.pgragnic.force.BuildConfig.VERSION_NAME} · programme ") + if (Prog.version > 0) t("n° ${Prog.version} (en ligne)", "no. ${Prog.version} (online)") else t("d'origine", "original"), color = C.muted, fontSize = 12.sp)
    }
}

const val SITE = "https://pgragnic.github.io/programme-force-android/"

/** Avertissement santé, à accepter au premier lancement. */
@Composable
fun HealthNotice() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val p = remember { ctx.getSharedPreferences("force", android.content.Context.MODE_PRIVATE) }
    var ok by remember { mutableStateOf(p.getBoolean("avert", false)) }
    if (ok) return
    AlertDialog(onDismissRequest = {}, containerColor = C.surface,
        title = { Text("Avant de commencer", color = C.ink, fontWeight = FontWeight.Bold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Programme Force propose des exercices de renforcement musculaire. Ce n'est pas un dispositif médical et elle ne remplace pas l'avis d'un médecin.", color = C.ink)
            Text("Demande l'avis d'un médecin avant de commencer si tu as un problème cardiaque, une tension élevée, une blessure, des douleurs articulaires ou dorsales, si tu es enceinte, ou si tu reprends le sport après une longue pause.", color = C.ink, modifier = Modifier.padding(top = 8.dp))
            Text("Arrête immédiatement en cas de douleur, de vertige, d'essoufflement inhabituel ou de malaise. Choisis des charges que tu maîtrises et garde une bonne technique.", color = C.ink, modifier = Modifier.padding(top = 8.dp))
            Text("Appli réservée aux personnes de 16 ans et plus.", color = C.muted, modifier = Modifier.padding(top = 8.dp))
        } },
        confirmButton = { Button(onClick = { p.edit().putBoolean("avert", true).apply(); ok = true }) { Text("J'ai compris") } })
}

@Composable
fun LegalPanel() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun open(u: String) = runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u))) }
    Panel {
        Text("Santé et confidentialité", color = C.ink, fontWeight = FontWeight.Bold)
        Text("Programme Force n'est pas un dispositif médical. En cas de doute sur ta santé, demande l'avis d'un médecin avant de t'entraîner, et arrête en cas de douleur ou de malaise.", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Row {
            TextButton(onClick = { open(SITE + "confidentialite.html") }, contentPadding = PaddingValues(0.dp)) { Text("Politique de confidentialité") }
            Spacer(Modifier.width(16.dp))
            TextButton(onClick = { open(SITE + "suppression.html") }, contentPadding = PaddingValues(0.dp)) { Text("Supprimer mon compte") }
        }
    }
}

/** Avis et idées d'amélioration envoyés au développeur. */
@Composable
fun FeedbackPanel() {
    val user by Store.user.collectAsState(); val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("idee") }; var txt by remember { mutableStateOf("") }
    var contact by remember(user) { mutableStateOf(user ?: "") }; var msg by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    Panel {
        Text("Ton avis compte", color = C.ink, fontWeight = FontWeight.Bold)
        Text("Une idée d'amélioration, un problème, un exercice qui manque ? Écris-le ici : chaque message est lu.", color = C.muted, fontSize = 13.sp)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("idee" to "Idée", "probleme" to "Problème", "avis" to "Commentaire").forEach { (k, t) ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (kind == k) C.ink else C.bg).clickable { kind = k }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                    Text(t, color = if (kind == k) C.bg else C.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        OutlinedTextField(txt, { if (it.length <= 2000) txt = it }, label = { Text("Ton message") }, minLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        OutlinedTextField(contact, { contact = it }, label = { Text("E-mail pour te répondre (facultatif)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        Button(onClick = { busy = true; scope.launch { msg = Store.sendFeedback(kind, txt, contact) ?: "Merci ! Ton message est bien arrivé.".also { txt = "" }; busy = false } },
            enabled = !busy && txt.trim().length >= 3, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(if (busy) "Envoi…" else "Envoyer") }
        msg?.let { Text(it, color = C.muted, fontSize = 13.sp) }
    }
}
