package fr.pgragnic.force

import android.content.Context
import android.content.SharedPreferences
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.handleDeeplinks
import android.content.Intent
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable
data class SessRow(val id: String? = null, val d: Long, val data: JsonObject)

@Serializable
data class StateRow(val data: JsonObject)

/** Stockage : copie locale sur le téléphone + synchronisation Supabase quand on est connecté. */
object Store {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false; coerceInputValues = true }
    private lateinit var prefs: SharedPreferences
    val log = MutableStateFlow<List<Entry>>(emptyList())
    val st = MutableStateFlow(Settings())
    val sync = MutableStateFlow("")
    val user = MutableStateFlow<String?>(null)
    val ver = MutableStateFlow(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sb: SupabaseClient? by lazy {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_KEY.isBlank()) null
        else createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) { install(Auth) { scheme = "fr.pgragnic.force"; host = "login" }; install(Postgrest) }
    }

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("force", Context.MODE_PRIVATE)
        runCatching { log.value = json.decodeFromString<List<Entry>>(prefs.getString("log", "[]")!!) }
        runCatching { prefs.getString("st", null)?.let { st.value = stFrom(json.parseToJsonElement(it).jsonObject) } }
        if (sb == null) { sync.value = "Base en ligne non configurée : données gardées sur ce téléphone"; return }
        scope.launch {
            runCatching { sb!!.auth.awaitInitialization() }
            sb!!.auth.sessionStatus.collect { s ->
                when (s) {
                    is SessionStatus.Authenticated -> { val was = user.value; user.value = s.session.user?.email ?: sb!!.auth.currentUserOrNull()?.email; if (was == null) pull() }
                    is SessionStatus.NotAuthenticated -> { user.value = null; sync.value = "Connecte-toi (onglet Infos) pour sauvegarder en ligne" }
                    else -> {}
                }
            }
        }
    }

    private fun stTo(s: Settings) = buildJsonObject {
        put("c", s.c); put("w", s.w); put("s", s.s); put("rest", s.rest); put("bar", s.bar); put("n2", s.n2); put("n05", s.n05)
    }
    private fun stFrom(o: JsonObject) = Settings(
        o["c"]?.jsonPrimitive?.intOrNull ?: 1, o["w"]?.jsonPrimitive?.intOrNull ?: 0, o["s"]?.jsonPrimitive?.contentOrNull ?: "A",
        o["rest"]?.jsonPrimitive?.intOrNull ?: 30, o["bar"]?.jsonPrimitive?.doubleOrNull ?: 1.8,
        o["n2"]?.jsonPrimitive?.intOrNull ?: 2, o["n05"]?.jsonPrimitive?.intOrNull ?: 6)

    private fun saveLocal() {
        prefs.edit().putString("log", json.encodeToString(log.value)).putString("st", stTo(st.value).toString()).apply()
        ver.value++
    }

    fun setSettings(f: (Settings) -> Unit) {
        val s = st.value; f(s); st.value = Settings(s.c, s.w, s.s, s.rest, s.bar, s.n2, s.n05); saveLocal()
        val c = sb ?: return
        if (user.value == null) return
        scope.launch { runCatching { c.from("state").upsert(StateRow(stTo(st.value))) } }
    }

    private fun entryJson(e: Entry): JsonObject {
        val o = json.encodeToJsonElement(e).jsonObject
        return JsonObject(o.filterKeys { it != "_id" })
    }

    private var lastPull = 0L
    /** Synchronisation automatique : retour dans l'appli, réseau retrouvé. */
    fun autoSync(force: Boolean = false) {
        if (sb == null || user.value == null) return
        val pending = log.value.any { it._id == null }
        if (force || pending || System.currentTimeMillis() - lastPull > 60_000) pull()
    }

    /** Lien de confirmation d'e-mail ou de connexion reçu par l'appli. */
    fun handleLink(intent: Intent?) {
        val c = sb ?: return; val i = intent ?: return
        if (i.data?.scheme != "fr.pgragnic.force") return
        runCatching { c.handleDeeplinks(i) { sync.value = "Adresse confirmée, connecté" } }
    }

    /** Récupère tout depuis Supabase, en envoyant d'abord les séances restées sur le téléphone. */
    fun pull() {
        val c = sb ?: return
        scope.launch {
            try {
                lastPull = System.currentTimeMillis()
                sync.value = "Synchronisation…"
                val pending = log.value.filter { it._id == null }
                pending.forEach { e -> c.from("sessions").insert(SessRow(d = e.d, data = entryJson(e))) }
                val rows = c.from("sessions").select().decodeList<SessRow>()
                log.value = rows.mapNotNull { r -> runCatching { json.decodeFromJsonElement<Entry>(r.data).copy(_id = r.id) }.getOrNull() }.sortedBy { it.d }
                runCatching { c.from("state").select().decodeList<StateRow>().firstOrNull()?.let { st.value = stFrom(it.data) } }
                saveLocal()
                sync.value = "Synchronisé · ${log.value.size} séances"
            } catch (t: Throwable) {
                sync.value = "Base injoignable : données gardées sur ce téléphone"
            }
        }
    }

    fun add(e: Entry) {
        log.value = log.value + e; saveLocal()
        val c = sb ?: return
        if (user.value == null) return
        scope.launch {
            try {
                val r = c.from("sessions").insert(SessRow(d = e.d, data = entryJson(e))) { select() }.decodeSingle<SessRow>()
                log.value = log.value.map { if (it.d == e.d && it._id == null) it.copy(_id = r.id) else it }; saveLocal()
                sync.value = "Séance enregistrée en ligne"
            } catch (t: Throwable) { sync.value = "Séance gardée sur le téléphone, envoi au prochain lancement" }
        }
    }

    fun update(e: Entry) {
        log.value = log.value.map { if (it.d == e.d) e else it }; saveLocal()
        val c = sb ?: return; val id = e._id ?: return
        scope.launch { runCatching { c.from("sessions").update({ set("data", entryJson(e)) }) { filter { eq("id", id) } } }.onFailure { sync.value = "Modification non envoyée : base injoignable" } }
    }

    fun delete(e: Entry) {
        log.value = log.value.filter { it.d != e.d }; saveLocal()
        val c = sb ?: return; val id = e._id ?: return
        scope.launch { runCatching { c.from("sessions").delete { filter { eq("id", id) } } }.onFailure { sync.value = "Suppression non envoyée : base injoignable" } }
    }

    suspend fun signIn(email: String, pw: String, create: Boolean): String? {
        val c = sb ?: return "Base non configurée"
        return try {
            if (create) c.auth.signUpWith(Email, redirectUrl = "fr.pgragnic.force://login") { this.email = email; password = pw }
            else c.auth.signInWith(Email) { this.email = email; password = pw }
            user.value = c.auth.currentUserOrNull()?.email
            if (user.value == null) "Compte créé : ouvre l'e-mail reçu sur ce téléphone et touche le lien, l'appli s'ouvrira connectée."
            else { pull(); null }
        } catch (t: Throwable) { t.message ?: "Erreur de connexion" }
    }

    suspend fun signOut() { runCatching { sb?.auth?.signOut() }; user.value = null }
}
