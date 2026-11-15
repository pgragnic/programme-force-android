package fr.pgragnic.force

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

// Coach conversationnel (Philippe, 14 novembre 2026 : « L'idée serait qu'il y ait une vraie discussion entre l'application et l'utilisateur »,
// « Et que de cette discussion découle les séances adaptées », « L'application proposerait des suggestions », « Et suivant les réponses
// adapterait les séances » ; maquette du parcours validée : « Ok pour maquette du parcours »).
// L'appli parle à la fonction Supabase « coach » (supabase/functions/coach), qui garde la clé de l'API Claude.

@Serializable data class CoachRep(val de: String, val vers: String)
@Serializable data class CoachLoad(val id: String, val sens: Int)
@Serializable data class CoachGene(val zone: String, val jours: Int)
@Serializable data class CoachPlan(val seance: String? = null, val repos: Boolean? = null, val duree_max: Int? = null, val series_moins: Int? = null,
                                   val remplacements: List<CoachRep> = emptyList(), val explications: List<String> = emptyList(),
                                   val charges: List<CoachLoad> = emptyList(), val gene: CoachGene? = null, val sans_materiel_jours: Int? = null) {
    /** le plan change-t-il la séance du jour (et pas seulement les jours suivants) ? */
    val today get() = seance != null || duree_max != null || (series_moins ?: 0) > 0 || remplacements.isNotEmpty() || gene != null || sans_materiel_jours != null
}

/**
 * Ce que le coach a décidé pour plusieurs jours (Philippe, 14 novembre 17 h 22 : « Tous les ajouts ») :
 * cran de charge pour la prochaine fois, zone à ménager quelques jours, séances au poids du corps (vacances).
 */
object CoachState {
    @Serializable data class Bias(val dir: Int, val t: Long)
    @Serializable private data class Saved(val bias: Map<String, Bias> = emptyMap(), val genes: Map<String, String> = emptyMap(), val noGear: String? = null)
    private var s = Saved()
    var v by mutableIntStateOf(0)
    private fun today() = java.time.LocalDate.now()
    private fun live(until: String) = runCatching { !java.time.LocalDate.parse(until).isBefore(today()) }.getOrDefault(false)
    val bias get() = s.bias
    /** zones ménagées et date de fin (incluse) */
    val genes: Map<String, String> get() = s.genes.filterValues { live(it) }
    val noGear: String? get() = s.noGear?.takeIf { live(it) }
    private val BW = setOf("chair", "wall", "mat")
    /** matériel et restrictions effectivement utilisés par le programme */
    private var effIn: Gear? = null; private var effKey = ""; private var effOut: Gear? = null
    fun eff(g: Gear): Gear {
        val key = "$v|${today()}"
        if (g === effIn && key == effKey) return effOut!!
        effIn = g; effKey = key; effOut = effRaw(g); return effOut!!
    }
    private fun effRaw(g: Gear): Gear {
        val gz = genes.keys; val ng = noGear != null
        if (gz.isEmpty() && !ng) return g
        return g.copy(limits = g.limits + gz, items = if (ng) g.items.filter { it in BW }.toSet() + "mat" else g.items)
    }
    fun load(ctx: Context) { s = runCatching { Json.decodeFromString<Saved>(ctx.getSharedPreferences("force", Context.MODE_PRIVATE).getString("coachst", null) ?: return) }.getOrDefault(Saved()) }
    private fun save(ctx: Context) { ctx.getSharedPreferences("force", Context.MODE_PRIVATE).edit().putString("coachst", Json.encodeToString(Saved.serializer(), s)).apply(); v++; Adapt.v++ }
    fun apply(ctx: Context, p: CoachPlan) {
        val now = System.currentTimeMillis()
        var b = s.bias; p.charges.filter { it.id in Prog.EX && (it.sens == 1 || it.sens == -1) }.forEach { b = b + (it.id to Bias(it.sens, now)) }
        var gz = s.genes; p.gene?.takeIf { it.zone in Gear.LIMITS.map { l -> l.first } }?.let { gz = gz + (it.zone to today().plusDays(it.jours.coerceIn(1, 14).toLong() - 1).toString()) }
        val ng = p.sans_materiel_jours?.let { today().plusDays(it.coerceIn(1, 21).toLong() - 1).toString() } ?: s.noGear
        s = Saved(b, gz, ng); save(ctx)
    }
    fun endGene(ctx: Context, zone: String) { s = s.copy(genes = s.genes - zone); save(ctx) }
    fun endNoGear(ctx: Context) { s = s.copy(noGear = null); save(ctx) }
    fun fmt(d: String) = runCatching { java.time.LocalDate.parse(d).format(java.time.format.DateTimeFormatter.ofPattern("d MMM", L.loc)) }.getOrDefault(d)
}
@Serializable data class CoachReply(val texte: String = "", val choix: List<String> = emptyList(), val plan: CoachPlan? = null, val erreur: String? = null)

/** Adaptation de la séance du jour décidée avec le coach ; ne vaut que pour la date du jour. */
object Adapt {
    @Serializable private data class Saved(val day: String, val maxMin: Int? = null, val less: Int = 0, val swaps: Map<String, String> = emptyMap(), val notes: List<String> = emptyList())
    private var cur: Saved? = null
    /** change à chaque modification, pour recalculer l'écran Séance */
    var v by mutableIntStateOf(0)
    private fun today() = java.time.LocalDate.now().toString()
    val on get() = cur?.day == today()
    val maxMin get() = if (on) cur?.maxMin else null
    val less get() = if (on) cur?.less ?: 0 else 0
    val notes: List<String> get() = if (on) cur?.notes.orEmpty() else emptyList()
    fun swap(id: String) = if (on) cur?.swaps?.get(id) ?: id else id

    fun load(ctx: Context) {
        cur = runCatching { Json.decodeFromString<Saved>(ctx.getSharedPreferences("force", Context.MODE_PRIVATE).getString("adapt", null) ?: return) }.getOrNull()
    }
    private fun save(ctx: Context) {
        ctx.getSharedPreferences("force", Context.MODE_PRIVATE).edit().putString("adapt", cur?.let { Json.encodeToString(Saved.serializer(), it) }).apply(); v++
    }
    /** Applique un plan du coach. Les remplacements ne sont gardés que s'ils font partie des variantes autorisées par l'appli. */
    fun apply(ctx: Context, p: CoachPlan) {
        val st = Store.st.value
        p.seance?.takeIf { it in Prog.sess(st.c) }?.let { s -> Store.setSettings { it.s = s } }
        val inSess = Prog.orderEx(st.c, Store.st.value.s, st.w, Store.st.value)
        val ok = p.remplacements.filter { r -> r.de in Prog.EX && r.vers in Prog.EX && r.vers in Prog.alternatives(r.de, inSess) }.associate { it.de to it.vers }
        CoachState.apply(ctx, p)
        if (p.seance != null || p.duree_max != null || (p.series_moins ?: 0) > 0 || ok.isNotEmpty())
            cur = Saved(today(), p.duree_max?.coerceIn(15, 90), (p.series_moins ?: 0).coerceIn(0, 2), ok, p.explications.take(6))
        save(ctx)
    }
    fun clear(ctx: Context) { cur = null; save(ctx) }
}

object CoachApi {
    /** nom de la fonction Supabase déployée par Philippe (code : supabase/functions/coach/index.ts), 14 novembre 16 h 21 */
    const val FN = "clever-service"
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** Contexte envoyé au coach : profil, matériel, programme, séance proposée et ses variantes, récupération, dernières séances, pas. */
    fun context(): JsonObject {
        val st = Store.st.value; val g = GearStore.g.value; val log = Store.log.value; val days = Store.days.value
        val MU = Recovery.muscles(log, days)
        val order = Prog.orderEx(st.c, st.s, st.w, st)
        return buildJsonObject {
            put("aujourdhui", java.time.LocalDate.now().toString())
            putJsonObject("profil") {
                put("objectif", g.obj); put("niveau", when (g.level) { 0 -> "débutant"; 1 -> "intermédiaire"; 2 -> "confirmé"; else -> null })
                putJsonArray("zones_prioritaires") { g.zones.forEach { add(it) } }
                putJsonArray("restrictions_sante") { g.limits.forEach { add(it) } }
            }
            putJsonArray("materiel") { g.items.forEach { add(it) } }
            putJsonObject("programme") { put("cycle", st.c); put("semaine", st.w + 1); put("semaines", 8); put("seances_par_semaine", g.perWeek); put("duree_max_habituelle", g.maxMin)
                put("series", Prog.WEEKS[st.w].s); put("repetitions", Prog.WEEKS[st.w].r) }
            putJsonArray("seances") { Prog.sess(st.c).forEach { (k, s) -> addJsonObject { put("lettre", k); put("titre", tr(s.t)) } } }
            put("seance_proposee", st.s)
            putJsonArray("exercices") { order.forEach { id -> addJsonObject {
                put("id", id); put("nom", tr(Prog.EX[id]?.n ?: id)); put("charge", tr(Prog.load(id, st.w, st)))
                putJsonArray("muscles") { (Prog.MAP[id] ?: emptyMap()).filter { it.value >= .7 }.keys.forEach { add(tr(it)) } }
                putJsonArray("alternatives") { Prog.alternatives(id, order).take(6).forEach { a -> addJsonObject { put("id", a); put("nom", tr(Prog.EX[a]?.n ?: a)) } } }
            } } }
            putJsonObject("recuperation") { Recovery.MUS.forEach { put(tr(it), MU.R[it]) } }
            putJsonArray("surcharges") { Recovery.MUS.filter { MU.over(it) }.forEach { add(tr(it)) } }
            putJsonArray("dernieres_seances") { log.sortedByDescending { it.d }.take(5).forEach { e -> addJsonObject {
                put("date", java.time.Instant.ofEpochMilli(e.d).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()); put("seance", e.s); put("ressenti", e.f)
                putJsonArray("exercices") { e.ex.forEach { x -> addJsonObject { put("id", x.id); put("nom", tr(x.n)); put("kg", x.kg); put("objectif", x.target); put("fait", x.done)
                    x.sets.mapNotNull { it.res }.takeIf { it.isNotEmpty() }?.let { r -> put("reserve_moyenne", r.average()) } } } }
            } } }
            putJsonObject("adaptations_en_cours") {
                putJsonObject("zones_menagees_jusqu_au") { CoachState.genes.forEach { (z, d) -> put(z, d) } }
                put("sans_materiel_jusqu_au", CoachState.noGear)
                putJsonArray("charges_a_ajuster") { CoachState.bias.forEach { (id, b) -> addJsonObject { put("id", id); put("sens", b.dir) } } }
            }
            Balance.compute(log).let { b -> putJsonObject("equilibre_4_semaines") {
                putJsonObject("series_par_semaine") { b.sets.forEach { (m, v) -> put(mus(m), Math.round(v * 10) / 10.0) } }
                putJsonArray("desequilibres") { b.pairs.filter { it.state != 0 }.forEach { add(it.advice) } }
                putJsonArray("peu_travailles") { b.low.forEach { add(tr(it)) } }
                putJsonArray("rattrapage_cette_semaine") { Balance.weekRes().pairs.filter { it.state != 0 }.forEach { add(if (it.state == -1) it.a else it.b) } }
            } }
            putJsonArray("activite_3_jours") { days.sortedByDescending { it.date }.take(3).forEach { d -> addJsonObject { put("date", d.date); put("pas", d.steps); put("activite", d.act); put("pas_avec_sac", d.rsteps) } } }
        }
    }

    /** Un échange avec le coach. messages : alternance user / assistant, en commençant par user. */
    suspend fun ask(messages: List<Pair<String, String>>, mode: String = "avant"): CoachReply = withContext(Dispatchers.IO) {
        val tok = runCatching { Store.sb?.auth?.currentAccessTokenOrNull() }.getOrNull() ?: return@withContext CoachReply(erreur = "compte")
        runCatching {
            val c = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/" + FN).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 45000
            c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
            c.setRequestProperty("Authorization", "Bearer $tok")
            val body = buildJsonObject {
                put("lang", if (L.en) "en" else "fr"); put("mode", mode); put("context", context())
                putJsonArray("messages") { messages.forEach { (r, t) -> addJsonObject { put("role", r); put("content", t) } } }
            }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            val r = runCatching { json.decodeFromString<CoachReply>(txt) }.getOrElse { CoachReply(erreur = "api") }
            if (code !in 200..299 && r.erreur == null) r.copy(erreur = if (code == 404) "absent" else "api") else r
        }.getOrElse { CoachReply(erreur = "reseau") }
    }

    /** suggestion du jour (une par jour au plus, gardée sur le téléphone) */
    suspend fun suggestion(ctx: Context): String? {
        val p = ctx.getSharedPreferences("force", Context.MODE_PRIVATE); val d = java.time.LocalDate.now().toString()
        p.getString("coachsug", null)?.let { if (it.startsWith("$d|")) return it.substringAfter("|").ifBlank { null } }
        if (!p.getBoolean("coach_ok", false) || Store.user.value == null) return null
        val r = ask(listOf("user" to t("Une suggestion pour aujourd'hui ?", "A suggestion for today?")), "suggestion")
        if (r.erreur != null) return null
        p.edit().putString("coachsug", "$d|${r.texte.trim()}").apply(); return r.texte.trim().ifBlank { null }
    }
    /** mot du coach sur la semaine (gardé pour la semaine) */
    suspend fun bilan(ctx: Context, key: String, force: Boolean): String? {
        val p = ctx.getSharedPreferences("force", Context.MODE_PRIVATE)
        p.getString("coachbilan", null)?.let { if (it.startsWith("$key|") && !force) return it.substringAfter("|") }
        if (!force) return null
        val r = ask(listOf("user" to t("Fais-moi le bilan de ma semaine.", "Give me my week's review.")), "bilan")
        if (r.erreur != null) return errText(r.erreur)
        p.edit().putString("coachbilan", "$key|${r.texte.trim()}").apply(); return r.texte.trim()
    }

    fun errText(e: String) = when (e) {
        "compte" -> t("Le coach a besoin d'un compte : Réglages → Compte et sauvegarde.", "The coach needs an account: Settings → Account and backup.")
        "quota" -> t("Tu as atteint la limite de messages du jour. Le coach revient demain !", "You've reached today's message limit. The coach is back tomorrow!")
        "reseau" -> t("Pas de connexion pour l'instant. Réessaie dans un moment.", "No connection right now. Try again in a moment.")
        else -> t("Le coach ne répond pas pour l'instant. Réessaie dans un moment.", "The coach isn't answering right now. Try again in a moment.")
    }
}

/** Série par défaut moins l'allègement du jour, jamais sous 1. */
fun adaptSets(n: Int) = max(1, n - Adapt.less)
