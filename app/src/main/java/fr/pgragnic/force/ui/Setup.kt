package fr.pgragnic.force.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pgragnic.force.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

/** Ouverture de l'écran de réglage du matériel et du profil depuis Infos. */
val setupOpen = MutableStateFlow(false)

private fun nums(t: String) = t.split(Regex("[^0-9,.]+")).mapNotNull { it.replace(',', '.').toDoubleOrNull() }.filter { it > 0 }.distinct().sorted()
private fun numTxt(l: List<Double>) = l.joinToString(" ; ") { Prog.fk(it) }

@Composable
private fun Check(t: String, on: Boolean, sub: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t, color = if (enabled) C.ink else C.muted, fontSize = 16.sp)
            if (sub != null) Text(sub, color = C.muted, fontSize = 12.sp)
        }
        Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(if (on) C.effort else C.bg).border(1.5.dp, if (on) C.effort else C.muted, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
            if (on) Icon(Icons.Default.Check, null, tint = C.onColor, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Seg(opts: List<String>, sel: Int, onSel: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opts.forEachIndexed { i, t ->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (i == sel) C.ink else C.bg).clickable { onSel(i) }.padding(vertical = 10.dp, horizontal = 4.dp), contentAlignment = Alignment.Center) {
                Text(t, color = if (i == sel) C.bg else C.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

/** Premier lancement guidé : matériel, charges disponibles, santé, profil. */
@Composable
fun SetupScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val cur = GearStore.g.collectAsState().value
    var g by remember { mutableStateOf(cur) }
    var step by remember { mutableIntStateOf(Demo.setupStep) }
    var consent by remember { mutableStateOf(cur.limits.isNotEmpty()) }
    val eqList = remember { Prog.raw["EQ"]?.jsonArray?.map { s -> s.jsonArray[0].jsonPrimitive.content to s.jsonArray[1].jsonArray.map { it.jsonArray } } ?: emptyList() }
    val needDetail = "db" in g.items || "kb" in g.items || "band" in g.items || "barbell" in g.items
    val steps = listOf("Ton matériel", "Tes charges", "Ta santé", "Ton profil")
    fun go(d: Int) {
        var n = step + d; if (n == 1 && !needDetail) n += d; if (n < 0) return
        if (n > 3) {
            if (!cur.done || cur.obj != g.obj) Store.setSettings { it.rest = Gear.restFor(g.obj) }
            GearStore.save(ctx, g.copy(done = true, level = g.level ?: 0, limits = if (consent) g.limits else emptySet())); onDone(); return
        }
        step = n
    }

    Column(Modifier.fillMaxSize().background(C.bg)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (step == 0) { if (cur.done) onDone() } else go(-1) }, enabled = step > 0 || cur.done) { Icon(Icons.Default.ArrowBack, "Retour", tint = if (step > 0 || cur.done) C.ink else C.bg) }
            Text(tr(steps[step]).uppercase(), color = C.ink, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Text("${step + 1}/4", color = C.muted)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                0 -> {
                    Text("Coche ce que tu as sous la main. Les séances sont composées avec ce matériel, et le poids du corps est toujours disponible.", color = C.muted)
                    eqList.forEach { (cat, items) ->
                        Eyebrow(cat)
                        Panel {
                            items.forEach { it ->
                                val id = it[0].jsonPrimitive.content; val name = it[1].jsonPrimitive.content; val live = it[2].jsonPrimitive.int == 1
                                Check(name, id in g.items, if (live) null else "Exercices bientôt disponibles") {
                                    g = g.copy(items = if (id in g.items) g.items - id else g.items + id)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if ("db" in g.items) Panel {
                        Text("Haltères", color = C.ink, fontWeight = FontWeight.Bold)
                        Seg(listOf("Réglables", "Poids fixes"), if (g.dbMode == "fixed") 1 else 0) { g = g.copy(dbMode = if (it == 1) "fixed" else "adj") }
                        if (g.dbMode == "fixed") {
                            var t by remember { mutableStateOf(numTxt(g.fixed)) }
                            OutlinedTextField(t, { t = it; g = g.copy(fixed = nums(it)) }, label = { Text("Poids disponibles (kg)") }, placeholder = { Text("ex. 2 ; 4 ; 6 ; 8 ; 10") },
                                supportingText = { Text("Sépare les poids par des points-virgules.") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        } else {
                            var bar by remember { mutableStateOf(Prog.fk(g.bar)) }
                            OutlinedTextField(bar, { bar = it; it.replace(',', '.').toDoubleOrNull()?.let { v -> g = g.copy(bar = v) } }, label = { Text("Poids d'une barre d'haltère (kg)") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            Text("Disques disponibles pour UN haltère", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                            listOf(5.0, 2.5, 2.0, 1.25, 1.0, 0.5).forEach { kg ->
                                val n = g.plates.firstOrNull { it.kg == kg }?.n ?: 0
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${Prog.fk(kg)} kg", color = C.ink, modifier = Modifier.weight(1f))
                                    OutlinedButton(onClick = { g = g.copy(plates = (g.plates.filter { it.kg != kg } + Plate(kg, maxOf(0, n - 2))).filter { it.n > 0 }) }) { Text("−") }
                                    Text("$n", color = C.ink, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    OutlinedButton(onClick = { g = g.copy(plates = g.plates.filter { it.kg != kg } + Plate(kg, n + 2)) }) { Text("+") }
                                }
                            }
                            val w = g.dbWeights()
                            Text(t("Charges possibles par haltère : de ${Prog.fk(w.first())} à ${Prog.fk(w.last())} kg (${w.size} réglages).", "Possible loads per dumbbell: ${Prog.fk(w.first())} to ${Prog.fk(w.last())} kg (${w.size} settings)."), color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    if ("kb" in g.items) Panel {
                        Text("Kettlebells", color = C.ink, fontWeight = FontWeight.Bold)
                        var t by remember { mutableStateOf(numTxt(g.kbs)) }
                        OutlinedTextField(t, { t = it; g = g.copy(kbs = nums(it)) }, label = { Text("Poids de tes kettlebells (kg)") }, placeholder = { Text("ex. 8 ; 12 ; 16") },
                            supportingText = { Text("Sépare les poids par des points-virgules.") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    if ("barbell" in g.items) Panel {
                        Text("Barre et disques", color = C.ink, fontWeight = FontWeight.Bold)
                        var bar by remember { mutableStateOf(Prog.fk(g.bbBar)) }
                        OutlinedTextField(bar, { bar = it; it.replace(',', '.').toDoubleOrNull()?.let { v -> g = g.copy(bbBar = v) } }, label = { Text("Poids de la barre (kg)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        Text("Nombre total de disques de chaque poids", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        listOf(20.0, 15.0, 10.0, 5.0, 2.5, 1.25).forEach { kg ->
                            val n = g.bbPlates.firstOrNull { it.kg == kg }?.n ?: 0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${Prog.fk(kg)} kg", color = C.ink, modifier = Modifier.weight(1f))
                                OutlinedButton(onClick = { g = g.copy(bbPlates = (g.bbPlates.filter { it.kg != kg } + Plate(kg, maxOf(0, n - 2))).filter { it.n > 0 }) }) { Text("−") }
                                Text("$n", color = C.ink, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                OutlinedButton(onClick = { g = g.copy(bbPlates = g.bbPlates.filter { it.kg != kg } + Plate(kg, n + 2)) }) { Text("+") }
                            }
                        }
                        Text(t("Charge maximale : ${Prog.fk(g.bbWeights().last())} kg.", "Maximum load: ${Prog.fk(g.bbWeights().last())} kg."), color = C.muted, fontSize = 12.sp)
                    }
                    if ("band" in g.items) Panel {
                        Text("Bandes à poignées", color = C.ink, fontWeight = FontWeight.Bold)
                        Text("Combien de niveaux de résistance as-tu (de la plus souple à la plus dure) ?", color = C.muted, fontSize = 13.sp)
                        Seg(listOf("1", "2", "3", "4", "5"), g.bands - 1) { g = g.copy(bands = it + 1) }
                    }
                }
                2 -> {
                    Text("Coche jusqu'à trois zones à ménager. Les exercices qui les sollicitent seront remplacés par des variantes plus douces.", color = C.muted)
                    Panel {
                        Gear.LIMITS.forEach { (id, t) ->
                            val on = id in g.limits
                            Check(t, on, enabled = on || g.limits.size < 3) { g = g.copy(limits = if (on) g.limits - id else g.limits + id) }
                        }
                    }
                    if (g.limits.isNotEmpty()) {
                        Check("J'accepte que ces informations de santé servent à adapter mes séances. Elles restent sur ce téléphone.", consent) { consent = !consent }
                        Check("Sauvegarder aussi ces informations avec mon compte en ligne (facultatif).", g.limitsOnline, enabled = consent) { g = g.copy(limitsOnline = !g.limitsOnline) }
                        if (!consent) Text("Sans ton accord, les restrictions ne seront pas prises en compte.", color = C.warm, fontSize = 13.sp)
                    }
                    Panel {
                        Text("Ceci ne remplace pas un avis médical. Si tu as une maladie cardiovasculaire, une blessure ou des douleurs, demande l'avis d'un médecin avant de t'entraîner, et arrête en cas de douleur ou de malaise.", color = C.ink, fontSize = 14.sp)
                    }
                }
                else -> {
                    Eyebrow("Objectif")
                    Panel {
                        Gear.OBJS.forEach { (id, t) -> Check(t, g.obj == id) { g = g.copy(obj = id) } }
                    }
                    Eyebrow("Niveau")
                    Panel {
                        Gear.LEVELS.forEachIndexed { i, t -> Check(t, (g.level ?: -1) == i, Gear.LEVEL_TXT[i]) { g = g.copy(level = i) } }
                    }
                    Eyebrow("Zones à travailler")
                    Panel {
                        Gear.ZONES.forEach { (id, t) -> Check(t, id in g.zones) { g = g.copy(zones = if (id in g.zones) g.zones - id else g.zones + id) } }
                        Text("Les exercices de ces zones ont une série de plus et passent en premier.", color = C.muted, fontSize = 12.sp)
                    }
                    Eyebrow("Poids")
                    Panel {
                        var bw by remember { mutableStateOf(g.bw?.let { Prog.fk(it) } ?: "") }
                        var goal by remember { mutableStateOf(g.goal?.let { Prog.fk(it) } ?: "") }
                        OutlinedTextField(bw, { bw = it; g = g.copy(bw = it.replace(',', '.').toDoubleOrNull()) }, label = { Text("Ton poids (kg)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(goal, { goal = it; g = g.copy(goal = it.replace(',', '.').toDoubleOrNull()) }, label = { Text("Poids visé (facultatif, kg)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        Text("Ton poids sert à estimer tes charges de départ et ta dépense. Elles s'ajustent ensuite à chaque séance selon tes répétitions et ton ressenti.", color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Eyebrow(t("Taille, sexe et âge (facultatif)", "Height, sex and age (optional)"))
                    Panel {
                        // roulettes (demande de Philippe : « Peux tu prévoir des roulettes pour l'année se naissance et la taille ? ») ; « — » = non renseigné
                        val hv = remember { listOf<Int?>(null) + (140..210).toList() }
                        val yv = remember { val y = java.time.LocalDate.now().year; listOf<Int?>(null) + (y - 12 downTo 1930).toList() }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(t("Ta taille", "Your height"), color = C.muted, fontSize = 13.sp)
                                Wheel(hv.map { it?.let { v -> "$v cm" } ?: "—" }, hv.indexOf(g.height).coerceAtLeast(0)) { g = g.copy(height = hv[it]) }
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(t("Ton année de naissance", "Your year of birth"), color = C.muted, fontSize = 13.sp)
                                Wheel(yv.map { it?.toString() ?: "—" }, yv.indexOf(g.born).coerceAtLeast(0)) { g = g.copy(born = yv[it]) }
                                Text(g.age()?.let { a -> t("$a ans", "$a years old") } ?: " ", color = C.muted, fontSize = 12.sp)
                            }
                        }
                        Text(t("Sexe", "Sex"), color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                        Seg(listOf(t("Homme", "Male"), t("Femme", "Female"), t("Non précisé", "Not specified")), when (g.sex) { "m" -> 0; "f" -> 1; else -> 2 }) { g = g.copy(sex = when (it) { 0 -> "m"; 1 -> "f"; else -> null }) }
                        Text(t("Ta taille règle la longueur de tes pas pour la dépense de l'onglet Activité. Avec ton âge et ton sexe, l'appli estime aussi ta dépense au repos et ajuste tes charges de départ.",
                            "Your height sets your stride length for the burn in the Activity tab. With your age and sex, the app also estimates your resting burn and adjusts your starting loads."), color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        val okNext = when (step) { 3 -> g.obj != null && g.level != null; 1 -> !("db" in g.items && g.dbMode == "fixed" && g.fixed.isEmpty()) && !("kb" in g.items && g.kbs.isEmpty()); else -> true }
        Button(onClick = { go(1) }, enabled = okNext, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) {
            Text(if (step == 3) "Enregistrer" else "Continuer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/** Résumé du matériel dans l'onglet Infos. */
@Composable
fun GearPanel() {
    val g by GearStore.g.collectAsState()
    val names = remember { Prog.raw["EQ"]?.jsonArray?.flatMap { s -> s.jsonArray[1].jsonArray.map { it.jsonArray[0].jsonPrimitive.content to it.jsonArray[1].jsonPrimitive.content } }?.toMap() ?: emptyMap() }
    Panel {
        Text("Matériel et profil", color = C.ink, fontWeight = FontWeight.Bold)
        Text(g.items.mapNotNull { names[it] }.ifEmpty { listOf("Poids du corps seulement") }.joinToString(" · "), color = C.muted, fontSize = 13.sp)
        if ("db" in g.items) Text(if (g.dbMode == "fixed") "Haltères fixes : " + g.fixed.joinToString(", ") { Prog.fk(it) } + " kg"
            else t("Haltères réglables : barre ${Prog.fk(g.bar)} kg, charge max ${Prog.fk(g.dbWeights().last())} kg", "Adjustable dumbbells: handle ${Prog.fk(g.bar)} kg, max load ${Prog.fk(g.dbWeights().last())} kg"), color = C.muted, fontSize = 13.sp)
        if ("barbell" in g.items) Text(t("Barre : ${Prog.fk(g.bbBar)} kg, charge max ${Prog.fk(g.bbWeights().last())} kg", "Barbell: ${Prog.fk(g.bbBar)} kg, max load ${Prog.fk(g.bbWeights().last())} kg"), color = C.muted, fontSize = 13.sp)
        if ("kb" in g.items) Text("Kettlebells : " + g.kbs.joinToString(", ") { Prog.fk(it) } + " kg", color = C.muted, fontSize = 13.sp)
        listOfNotNull(g.height?.let { "$it cm" }, g.age()?.let { t("$it ans", "$it years old") }, when (g.sex) { "m" -> t("homme", "male"); "f" -> t("femme", "female"); else -> null }, g.bw?.let { "${Prog.fk(it)} kg" })
            .takeIf { it.isNotEmpty() }?.let { Text(t("Profil : ", "Profile: ") + it.joinToString(" · "), color = C.muted, fontSize = 13.sp) }
        if (g.limits.isNotEmpty()) Text("À ménager : " + Gear.LIMITS.filter { it.first in g.limits }.joinToString(", ") { it.second }, color = C.muted, fontSize = 13.sp)
        OutlinedButton(onClick = { setupOpen.value = true }, modifier = Modifier.padding(top = 6.dp)) { Text("Modifier") }
    }
}

/** Roulette verticale : 5 lignes visibles, la ligne du milieu est la valeur choisie. */
@Composable
fun Wheel(items: List<String>, index: Int, onChange: (Int) -> Unit) {
    val rowH = 40.dp; val st = rememberLazyListState(initialFirstVisibleItemIndex = index)
    val half = with(LocalDensity.current) { rowH.toPx() / 2 }; val scope = rememberCoroutineScope()
    val center by remember { derivedStateOf { (st.firstVisibleItemIndex + if (st.firstVisibleItemScrollOffset > half) 1 else 0).coerceIn(0, items.size - 1) } }
    LaunchedEffect(st.isScrollInProgress) { if (!st.isScrollInProgress && center != index) onChange(center) }
    Box(Modifier.fillMaxWidth().height(rowH * 5).padding(top = 4.dp)) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(rowH).clip(RoundedCornerShape(10.dp)).background(C.bg))
        LazyColumn(state = st, flingBehavior = rememberSnapFlingBehavior(st), contentPadding = PaddingValues(vertical = rowH * 2), modifier = Modifier.fillMaxSize()) {
            itemsIndexed(items) { i, s ->
                val d = kotlin.math.abs(i - center)
                Text(s, color = if (d == 0) C.ink else C.muted, fontSize = if (d == 0) 20.sp else 16.sp, fontWeight = if (d == 0) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().height(rowH).wrapContentHeight().clickable { scope.launch { st.animateScrollToItem(i) } }.alpha(if (d > 1) .45f else 1f))
            }
        }
    }
}
