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
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable
data class SessRow(val id: String? = null, val d: Long, val data: JsonObject)

@Serializable
data class StateRow(val data: JsonObject)

@Serializable
data class BilanRow(val data: JsonObject, val created_at: String? = null)

@Serializable
data class ProgRow(val id: Long, val data: JsonObject, val note: String? = null)

@Serializable
data class RetourRow(val kind: String, val texte: String, val contact: String? = null, val appli: String? = null)

@Serializable
data class MyRetour(val id: Long, val created_at: String, val kind: String, val texte: String, val reponse: String? = null)

@Serializable
data class DayRow(val date: String, val data: JsonObject)

/** Stockage : copie locale sur le téléphone + synchronisation Supabase quand on est connecté. */
object Store {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false; coerceInputValues = true }
    private lateinit var prefs: SharedPreferences
    val log = MutableStateFlow<List<Entry>>(emptyList())
    val st = MutableStateFlow(Settings())
    val sync = MutableStateFlow("")
    val user = MutableStateFlow<String?>(null)
    val ver = MutableStateFlow(0)
    val days = MutableStateFlow<List<DayRec>>(emptyList())
    /** Dernier bilan de la semaine écrit dans la base (table bilans), s'il y en a un. */
    val bilan = MutableStateFlow<JsonObject?>(null)
    /** Message quand une nouvelle version du programme vient d'arriver. */
    val progNote = MutableStateFlow("")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sb: SupabaseClient? by lazy {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_KEY.isBlank()) null
        else createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) { install(Auth) { scheme = "fr.pgragnic.force"; host = "login" }; install(Postgrest) }
    }

    private var app: Context? = null
    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        app = ctx.applicationContext
        prefs = ctx.getSharedPreferences("force", Context.MODE_PRIVATE)
        runCatching { log.value = json.decodeFromString<List<Entry>>(prefs.getString("log", "[]")!!) }
        runCatching { prefs.getString("bilan", null)?.let { bilan.value = json.parseToJsonElement(it).jsonObject } }
        runCatching { days.value = json.decodeFromString<List<DayRec>>(prefs.getString("days", "[]")!!) }
        runCatching { prefs.getString("st", null)?.let { st.value = stFrom(json.parseToJsonElement(it).jsonObject) } }
        migrate()
        if (sb == null) { sync.value = "Base en ligne non configurée : données gardées sur ce téléphone"; return }
        pullProg()
        scope.launch {
            runCatching { sb!!.auth.awaitInitialization() }
            sb!!.auth.sessionStatus.collect { s ->
                when (s) {
                    is SessionStatus.Authenticated -> { val was = user.value; user.value = s.session.user?.email ?: sb!!.auth.currentUserOrNull()?.email; if (was == null) pull() }
                    is SessionStatus.NotAuthenticated -> { user.value = null; sync.value = t("Connecte-toi (Réglages → Compte et sauvegarde) pour sauvegarder en ligne", "Sign in (Settings → Account and backup) to back up online") }
                    else -> {}
                }
            }
        }
    }

    private fun stTo(s: Settings) = buildJsonObject {
        put("c", s.c); put("w", s.w); put("s", s.s); put("rest", s.rest); put("bar", s.bar); put("n2", s.n2); put("n05", s.n05)
        put("gear", GearStore.online(GearStore.g.value))
    }

    /** Envoie le matériel et le profil avec les réglages. */
    fun pushGear() {
        val c = sb ?: return; if (user.value == null) return
        scope.launch { runCatching { c.from("state").upsert(StateRow(stTo(st.value))) } }
    }
    private fun stFrom(o: JsonObject) = Settings(
        o["c"]?.jsonPrimitive?.intOrNull ?: 1, o["w"]?.jsonPrimitive?.intOrNull ?: 0, o["s"]?.jsonPrimitive?.contentOrNull ?: "A",
        o["rest"]?.jsonPrimitive?.intOrNull ?: 30, o["bar"]?.jsonPrimitive?.doubleOrNull ?: 1.8,
        o["n2"]?.jsonPrimitive?.intOrNull ?: 2, o["n05"]?.jsonPrimitive?.intOrNull ?: 6)

    private fun saveLocal() {
        prefs.edit().putString("log", json.encodeToString(log.value)).putString("st", stTo(st.value).toString())
            .putString("days", json.encodeToString(days.value)).putString("dpend", json.encodeToString(dPending.toList()))
            .putString("spend", json.encodeToString(sPend.toList())).putString("ddel", json.encodeToString(dDel.toList())).putString("sdel", json.encodeToString(sDel.toList())).apply()
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

    private val dPending: MutableSet<String> by lazy { runCatching { json.decodeFromString<List<String>>(prefs.getString("dpend", "[]")!!).toMutableSet() }.getOrElse { mutableSetOf() } }

    /** Enregistre (ou remplace) une journée d'activité. */
    fun saveDay(d: DayRec) = saveDays(listOf(d))
    fun saveDays(ds: List<DayRec>) {
        if (ds.isEmpty()) return
        val keys = ds.map { it.date }.toSet()
        days.value = (days.value.filter { it.date !in keys } + ds).sortedBy { it.date }
        dPending += keys; saveLocal()
        pushDays()
    }
    /** Journées supprimées sur le téléphone, à supprimer aussi en ligne (gardé même appli fermée). */
    private val dDel: MutableSet<String> by lazy { runCatching { json.decodeFromString<List<String>>(prefs.getString("ddel", "[]")!!).toMutableSet() }.getOrElse { mutableSetOf() } }

    /** Supprime une journée d'activité saisie (sur le téléphone puis en ligne). */
    fun deleteDay(date: String) {
        days.value = days.value.filter { it.date != date }
        dPending -= date; dDel += date; saveLocal()
        pushDays()
    }

    private suspend fun pushDaysNow(c: SupabaseClient) {
        val todo = days.value.filter { it.date in dPending }
        if (todo.isNotEmpty()) { c.from("days").upsert(todo.map { DayRow(it.date, json.encodeToJsonElement(it).jsonObject) }) { onConflict = "user_id,date" }; dPending -= todo.map { it.date }.toSet() }
        dDel.toList().forEach { d -> c.from("days").delete { filter { eq("date", d) } }; dDel -= d }
        saveLocal()
    }

    private fun pushDays() {
        val c = sb ?: return; if (user.value == null) return
        if (dPending.isEmpty() && dDel.isEmpty()) return
        scope.launch {
            try { pushDaysNow(c) } catch (t: Throwable) { sync.value = "Journée gardée sur le téléphone, envoi plus tard" }
        }
    }

    private var lastPull = 0L
    /** Synchronisation automatique : retour dans l'appli, réseau retrouvé. */
    fun autoSync(force: Boolean = false) {
        pullProg()
        if (sb == null || user.value == null) return
        val pending = sPend.isNotEmpty() || sDel.isNotEmpty() || dPending.isNotEmpty() || dDel.isNotEmpty()
        if (force || pending || System.currentTimeMillis() - lastPull > 60_000) pull()
    }

    /** Lien de confirmation d'e-mail ou de connexion reçu par l'appli. */
    fun handleLink(intent: Intent?) {
        val c = sb ?: return; val i = intent ?: return
        if (i.data?.scheme != "fr.pgragnic.force") return
        if (i.dataString?.contains("type=recovery") == true) recovery.value = true
        runCatching { c.handleDeeplinks(i) { sync.value = "Adresse confirmée, connecté" } }
    }

    // ---------- séances : le téléphone est la référence ----------
    // Chaque séance reçoit son identifiant dès sa création sur le téléphone. Les changements (ajout, modification, suppression)
    // sont mis en file et gardés même si l'appli est fermée ; ils partent dès que la base répond. La liste en ligne ne remplace
    // jamais une séance pas encore envoyée, et une suppression faite hors connexion ne « revient » plus.
    private val sPend: MutableSet<Long> by lazy { runCatching { json.decodeFromString<List<Long>>(prefs.getString("spend", "[]")!!).toMutableSet() }.getOrElse { mutableSetOf() } }
    private val sDel: MutableSet<String> by lazy { runCatching { json.decodeFromString<List<String>>(prefs.getString("sdel", "[]")!!).toMutableSet() }.getOrElse { mutableSetOf() } }
    private val lock = kotlinx.coroutines.sync.Mutex()
    private fun newId() = java.util.UUID.randomUUID().toString()

    /** Anciennes versions : séances restées sans identifiant → identifiant donné et envoi mis en file. */
    private fun migrate() {
        if (log.value.none { it._id == null }) return
        log.value = log.value.map { if (it._id == null) it.copy(_id = newId()).also { e -> sPend += e.d } else it }
        saveLocal()
    }

    /** Envoie la file des changements de séances. Renvoie false si quelque chose n'a pas pu partir. */
    private suspend fun pushSessions(c: SupabaseClient): Boolean {
        var ok = true
        log.value.filter { it.d in sPend }.forEach { e ->
            try { c.from("sessions").upsert(SessRow(id = e._id, d = e.d, data = entryJson(e))) { onConflict = "id" }; sPend -= e.d }
            catch (t: Throwable) { ok = false }
        }
        sDel.toList().forEach { id ->
            try { c.from("sessions").delete { filter { eq("id", id) } }; sDel -= id } catch (t: Throwable) { ok = false }
        }
        saveLocal(); return ok
    }

    /** Fusion : séances en ligne, sauf celles supprimées ici, et la version du téléphone pour celles pas encore envoyées. */
    internal fun merge(local: List<Entry>, server: List<Entry>, pend: Set<Long>, del: Set<String>): List<Entry> {
        val mine = local.filter { it.d in pend }
        val keep = server.filter { it._id !in del && mine.none { m -> m._id == it._id || m.d == it.d } }
        return (keep + mine).sortedBy { it.d }
    }

    private fun push() {
        val c = sb ?: return; if (user.value == null) return
        scope.launch { lock.withLock { if (pushSessions(c)) sync.value = t("Synchronisé · ${log.value.size} séances", "Synced · ${log.value.size} sessions") else sync.value = t("Changements gardés sur le téléphone, envoi dès que possible", "Changes kept on this phone, will send when possible") } }
    }

    /** Programme : la dernière version en base, lisible même sans compte. */
    private var lastProg = 0L
    fun pullProg() {
        val c = sb ?: return
        if (System.currentTimeMillis() - lastProg < 60_000) return
        lastProg = System.currentTimeMillis()
        scope.launch {
            runCatching {
                val r = c.from("programme").select { order("id", io.github.jan.supabase.postgrest.query.Order.DESCENDING); limit(1) }.decodeList<ProgRow>().firstOrNull()
                if (r != null && r.id > Prog.version) {
                    val why = Prog.check(r.data)
                    if (why != null) { Log.w("UpRep", "programme ${r.id} refusé : $why"); return@launch }
                    if (Live.ses == null) withContext(Dispatchers.Main) {
                        if (Prog.safeApply(r.data)) {
                            prefs.edit().putString("prog", r.data.toString()).putLong("progv", r.id).apply()
                            Prog.version = r.id; ver.value++; progNote.value = "Programme mis à jour" + (r.note?.let { " : $it" } ?: "")
                        }
                    }
                }
            }
        }
    }

    /** Récupère tout depuis Supabase, en envoyant d'abord les changements restés sur le téléphone. */
    private var retry = 0
    fun pull() {
        val c = sb ?: return
        scope.launch {
            lock.withLock {
            try {
                lastPull = System.currentTimeMillis()
                sync.value = "Synchronisation…"
                // attendre que la connexion au compte soit prête (au retour dans l'appli, elle se renouvelle) : sinon la base répond
                // « aucune séance » sans erreur (retour de Philippe : « je me demande pourquoi les séances se "perdent" ? »)
                runCatching { c.auth.awaitInitialization() }
                if (c.auth.currentSessionOrNull() == null) runCatching { c.auth.refreshCurrentSession() }
                // sans connexion au compte prête, la base répondrait « aucune séance » sans erreur : on n'interroge pas, on réessaie
                val uid = c.auth.currentUserOrNull()?.id
                if (c.auth.currentSessionOrNull() == null || uid == null) {
                    sync.value = t("Connexion au compte en cours…", "Connecting to your account…")
                    if (retry++ < 3) scope.launch { kotlinx.coroutines.delay(3000); lastPull = 0; pull() } else sync.value = t("Synchronisation impossible pour l'instant : données gardées sur ce téléphone", "Can't sync right now: data kept on this phone")
                    return@withLock
                }
                retry = 0
                // changement de compte : les séances déjà envoyées appartiennent à l'autre compte (elles y restent en ligne) ; le téléphone
                // reprend celles du compte connecté (retour de Philippe, passage au compte Gmail : « Repartir de zéro sur Gmail »)
                if (prefs.getString("owner", null) != uid) {
                    log.value = log.value.filter { it.d in sPend }; sDel.clear(); dDel.clear()
                    days.value = days.value.filter { it.date in dPending }; bilan.value = null; prefs.edit().remove("bilan").apply()
                    prefs.edit().putString("owner", uid).apply()
                }
                pushSessions(c)
                val rows = c.from("sessions").select().decodeList<SessRow>()
                val server = rows.mapNotNull { r -> runCatching { json.decodeFromJsonElement<Entry>(r.data).copy(_id = r.id) }.getOrNull() }
                log.value = merge(log.value, server, sPend, sDel)
                runCatching { c.from("state").select().decodeList<StateRow>().firstOrNull()?.let { r ->
                    st.value = stFrom(r.data)
                    val a = app
                    if (a != null) { val gj = r.data["gear"]; if (gj != null) withContext(Dispatchers.Main) { GearStore.fromOnline(a, gj) } else pushGear() }
                } }
                lastProg = 0; pullProg()
                runCatching {
                    bilan.value = c.from("bilans").select { order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING); limit(1) }.decodeList<BilanRow>().firstOrNull()?.data
                    bilan.value?.let { prefs.edit().putString("bilan", it.toString()).apply() }
                }
                runCatching {
                    val todo = days.value.filter { it.date in dPending }
                    if (todo.isNotEmpty()) { c.from("days").upsert(todo.map { DayRow(it.date, json.encodeToJsonElement(it).jsonObject) }) { onConflict = "user_id,date" }; dPending.clear() }
                    val dr = c.from("days").select().decodeList<DayRow>()
                    days.value = dr.mapNotNull { r -> runCatching { json.decodeFromJsonElement<DayRec>(r.data).copy(date = r.date) }.getOrNull() }.sortedBy { it.date }
                }
                saveLocal()
                val left = sPend.size + sDel.size
                sync.value = if (left == 0) t("Synchronisé · ${log.value.size} séances", "Synced · ${log.value.size} sessions")
                    else t("Synchronisé · $left changement(s) encore à envoyer", "Synced · $left change(s) still to send")
            } catch (ex: Throwable) {
                sync.value = t("Base injoignable : données gardées sur ce téléphone", "Server unreachable: data kept on this phone")
            }
            }
        }
    }

    fun add(e0: Entry) {
        val e = if (e0._id == null) e0.copy(_id = newId()) else e0
        log.value = log.value + e; sPend += e.d; saveLocal(); push()
    }

    fun update(e0: Entry) {
        val e = if (e0._id == null) e0.copy(_id = log.value.firstOrNull { it.d == e0.d }?._id ?: newId()) else e0
        log.value = log.value.map { if (it.d == e.d) e else it }; sPend += e.d; saveLocal(); push()
    }

    fun delete(e: Entry) {
        val id = log.value.firstOrNull { it.d == e.d }?._id ?: e._id
        log.value = log.value.filter { it.d != e.d }; sPend -= e.d; if (id != null) sDel += id; saveLocal(); push()
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

    /** Connexion avec le compte Google du téléphone (bouton visible seulement si l'identifiant Google est configuré). */
    val googleReady get() = BuildConfig.GOOGLE_CLIENT_ID.isNotBlank() && sb != null
    suspend fun signInGoogle(activity: Context): String? {
        val c = sb ?: return "Base non configurée"
        return try {
            val opt = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_CLIENT_ID).build()
            val req = androidx.credentials.GetCredentialRequest.Builder().addCredentialOption(opt).build()
            val res = androidx.credentials.CredentialManager.create(activity).getCredential(activity, req)
            val cred = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(res.credential.data)
            c.auth.signInWith(io.github.jan.supabase.auth.providers.builtin.IDToken) { idToken = cred.idToken; provider = io.github.jan.supabase.auth.providers.Google }
            user.value = c.auth.currentUserOrNull()?.email; pull(); null
        } catch (t: androidx.credentials.exceptions.GetCredentialCancellationException) { null }
        catch (t: Throwable) { t("Connexion Google impossible : ", "Google sign-in failed: ") + (t.message ?: "") }
    }

    /** Mot de passe oublié : e-mail avec un lien qui ouvre l'appli pour choisir un nouveau mot de passe. */
    val recovery = MutableStateFlow(false)
    suspend fun resetPassword(email: String): String {
        val c = sb ?: return "Base non configurée"
        return try { c.auth.resetPasswordForEmail(email, redirectUrl = "fr.pgragnic.force://login"); t("E-mail envoyé : touche le lien sur ce téléphone pour choisir un nouveau mot de passe.", "Email sent: tap the link on this phone to choose a new password.") }
        catch (t: Throwable) { t("Envoi impossible : ", "Couldn't send: ") + (t.message ?: "") }
    }
    suspend fun newPassword(pw: String): String? {
        val c = sb ?: return "Base non configurée"
        return try { c.auth.updateUser { password = pw }; recovery.value = false; null } catch (t: Throwable) { t.message ?: "Erreur" }
    }

    suspend fun signOut() { runCatching { sb?.auth?.signOut() }; user.value = null }

    /** Envoie un avis ou une idée d'amélioration (possible sans compte). */
    suspend fun sendFeedback(kind: String, txt: String, contact: String?): String? {
        val c = sb ?: return "Envoi impossible : base non configurée"
        return try {
            c.from("retours").insert(RetourRow(kind, txt.trim().take(2000), contact?.trim()?.takeIf { it.isNotBlank() }?.take(200),
                "UpRep ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE}"))
            null
        } catch (t: Throwable) {
            if (t.message?.contains("Trop de messages") == true) t("Tu as envoyé beaucoup de messages : réessaie dans une heure.", "You've sent a lot of messages: try again in an hour.")
            else "Envoi impossible pour l'instant : vérifie ta connexion et réessaie."
        }
    }

    /** Messages envoyés avec ce compte et réponses reçues (les plus récents d'abord). */
    suspend fun myFeedback(): List<MyRetour> {
        val c = sb ?: return emptyList(); if (user.value == null) return emptyList()
        return runCatching { c.from("retours").select(io.github.jan.supabase.postgrest.query.Columns.list("id", "created_at", "kind", "texte", "reponse")) {
            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING); limit(20) }.decodeList<MyRetour>() }.getOrElse { emptyList() }
    }

    /** Supprime définitivement le compte et toutes ses données en ligne, puis les données du téléphone. */
    suspend fun deleteAccount(): String? {
        val c = sb ?: return "Base non configurée"
        return try {
            c.postgrest.rpc("supprimer_mon_compte")
            runCatching { c.auth.signOut() }
            user.value = null
            log.value = emptyList(); days.value = emptyList(); bilan.value = null; dPending.clear(); dDel.clear(); sPend.clear(); sDel.clear(); st.value = Settings()
            prefs.edit().remove("log").remove("days").remove("dpend").remove("ddel").remove("spend").remove("sdel").remove("bilan").remove("st").apply()
            ver.value++
            sync.value = "Compte supprimé"
            null
        } catch (t: Throwable) { "Suppression impossible : " + (t.message ?: "base injoignable") }
    }
}
