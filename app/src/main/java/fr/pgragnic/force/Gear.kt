package fr.pgragnic.force

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.math.*

/** Un type de disque : poids et nombre de disques disponibles pour UN haltère. */
@Serializable
data class Plate(val kg: Double, val n: Int)

/** Matériel et profil de l'utilisateur. */
@Serializable
data class Gear(
    val items: Set<String> = setOf("db", "mat", "wall"),
    /** "adj" = haltères réglables (barre + disques), "fixed" = haltères à poids fixe */
    val dbMode: String = "adj",
    val bar: Double = 1.8,
    val plates: List<Plate> = listOf(Plate(2.0, 2), Plate(0.5, 6)),
    val fixed: List<Double> = emptyList(),
    val kbs: List<Double> = emptyList(),
    /** barre longue : poids de la barre et disques disponibles (nombre total de disques de chaque poids) */
    val bbBar: Double = 20.0,
    val bbPlates: List<Plate> = listOf(Plate(10.0, 2), Plate(5.0, 2), Plate(2.5, 2), Plate(1.25, 2)),
    /** nombre de niveaux de résistance des bandes à poignées */
    val bands: Int = 3,
    /** 0 = débutant, 1 = intermédiaire, 2 = confirmé ; null = profil non renseigné (premières versions) */
    val level: Int? = null,
    val bw: Double? = null,
    val goal: Double? = null,
    /** taille en cm, sexe ("m", "f" ou null), année de naissance : facultatifs (foulée, dépense au repos, charges de départ) */
    val height: Int? = null,
    val sex: String? = null,
    val born: Int? = null,
    /** durée maximale d'une séance en minutes (null = sans limite) ; demande de Philippe : « Plafond au choix dans Infos » */
    val maxMin: Int? = null,
    /** séances par semaine (3 à 6) et lundi où la semaine du programme en cours a commencé (la semaine avance au calendrier).
     *  Philippe : « Et si je souhaite faire une séance par jour ? », choix « 2 et 3 ». */
    val perWeek: Int = 4,
    val wkStart: Long? = null,
    /** objectif : poids, muscle, forme, mobilite, sante */
    val obj: String? = null,
    /** zones à travailler en priorité : bras, ventre, jambes, poitrine, dos */
    val zones: Set<String> = emptySet(),
    /** restrictions de santé : genou, cheville, hanche, epaule, coude, dos, cardio */
    val limits: Set<String> = emptySet(),
    /** accord pour sauvegarder les restrictions de santé avec le compte en ligne */
    val limitsOnline: Boolean = false,
    val done: Boolean = false,
    /** remplacements choisis à la main dans la fiche d'un exercice : exercice proposé → exercice préféré (Philippe : « ok pour le raccourci "Remplacer" ») */
    val swaps: Map<String, String> = emptyMap(),
) {
    fun has(e: String) = e in items || e.isEmpty()

    /** L'exercice cible-t-il une zone choisie (si l'utilisateur n'a pas tout coché) ? */
    fun focus(id: String): Boolean {
        if (zones.isEmpty() || zones.size >= 4) return false
        val mus = zones.flatMap { ZONE_MUS[it] ?: emptySet() }.toSet()
        return (Prog.MAP[id] ?: emptyMap()).any { it.value >= .7 && it.key in mus }
    }

    /** Charges possibles pour UN haltère réglable. */
    fun dbWeights(): List<Double> {
        var s = setOf(0.0)
        plates.forEach { p -> val per = p.n / 2; val nx = HashSet<Double>(); s.forEach { b -> for (c in 0..per) nx += Prog.r1(b + 2 * c * p.kg) }; s = nx }
        return s.map { Prog.r1(bar + it) }.sorted()
    }

    fun bbWeights(): List<Double> {
        var s = setOf(0.0)
        bbPlates.forEach { p -> val per = p.n / 2; val nx = HashSet<Double>(); s.forEach { b -> for (c in 0..per) nx += Prog.r1(b + 2 * c * p.kg) }; s = nx }
        return s.map { Prog.r1(bbBar + it) }.sorted()
    }

    /** Charges possibles pour un exercice selon l'outil. */
    fun steps(tool: String): List<Double> = when (tool) {
        "db" -> if (dbMode == "fixed") fixed.sorted() else dbWeights()
        "kb" -> kbs.sorted()
        "bb" -> bbWeights()
        "band", "mini" -> (1..max(1, bands)).map { it.toDouble() }
        else -> emptyList()
    }

    companion object {
        val LIMITS = listOf("genou" to "Genou", "cheville" to "Cheville", "hanche" to "Hanche", "epaule" to "Épaule", "coude" to "Coude", "dos" to "Dos", "cardio" to "Maladie cardiovasculaire")
        val LEVELS = listOf("Débutant", "Intermédiaire", "Avancé")
        val LEVEL_TXT = listOf("Je débute ou je reprends après une longue pause", "Je m'entraîne 2 à 3 fois par semaine", "Je m'entraîne régulièrement depuis plus d'un an")
        val OBJS = listOf("poids" to "Perte de poids", "muscle" to "Prise de muscle", "forme" to "Améliorer la forme physique", "mobilite" to "Améliorer la mobilité", "sante" to "Rester en bonne santé")
        val ZONES = listOf("bras" to "Bras", "ventre" to "Ventre", "jambes" to "Jambes", "poitrine" to "Poitrine", "dos" to "Dos")
        val ZONE_MUS = mapOf("bras" to setOf("Biceps", "Triceps"), "ventre" to setOf("Abdos"), "jambes" to setOf("Quadriceps", "Ischios", "Fessiers", "Mollets"),
            "poitrine" to setOf("Pectoraux"), "dos" to setOf("Dos", "Lombaires"))
        /** pause conseillée (s) et répétitions en plus selon l'objectif */
        fun restFor(obj: String?) = when (obj) { "poids" -> 30; "muscle" -> 60; else -> 45 }
        fun repsFor(obj: String?) = when (obj) { "poids", "forme" -> 2; else -> 0 }
    }
}

/** Matériel courant, gardé sur le téléphone et (sans les restrictions de santé, sauf accord) dans le compte. */
object GearStore {
    val g = MutableStateFlow(Gear())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("force", Context.MODE_PRIVATE)
        val s = p.getString("gear", null)
        g.value = if (s != null) runCatching { json.decodeFromString<Gear>(s) }.getOrDefault(Gear())
        else {
            // utilisateur des premières versions : haltères réglables d'après ses anciens réglages
            val old = p.getString("st", null)?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            val had = p.contains("log") || old != null
            Gear(bar = old?.get("bar")?.jsonPrimitive?.doubleOrNull ?: 1.8,
                plates = listOf(Plate(2.0, old?.get("n2")?.jsonPrimitive?.intOrNull ?: 2), Plate(0.5, old?.get("n05")?.jsonPrimitive?.intOrNull ?: 6)),
                done = had)
        }
    }

    fun save(ctx: Context, n: Gear) {
        g.value = n
        ctx.getSharedPreferences("force", Context.MODE_PRIVATE).edit().putString("gear", json.encodeToString(Gear.serializer(), n)).apply()
        Store.pushGear()
    }

    /** Version envoyée en ligne : sans les restrictions de santé si l'utilisateur ne l'a pas accepté. */
    fun online(n: Gear): JsonElement = json.encodeToJsonElement(Gear.serializer(), if (n.limitsOnline) n else n.copy(limits = emptySet()))

    fun fromOnline(ctx: Context, e: JsonElement) {
        val r = runCatching { json.decodeFromJsonElement(Gear.serializer(), e) }.getOrNull() ?: return
        val cur = g.value
        val merged = if (r.limitsOnline) r else r.copy(limits = cur.limits)
        g.value = merged
        ctx.getSharedPreferences("force", Context.MODE_PRIVATE).edit().putString("gear", json.encodeToString(Gear.serializer(), merged)).apply()
    }
}

/** Âge en années, d'après l'année de naissance. */
fun Gear.age(): Int? = born?.let { java.time.LocalDate.now().year - it }?.takeIf { it in 10..110 }
/** Longueur de foulée (m) d'après la taille ; 0,74 m sans taille. */
fun Gear.stride(): Double = height?.takeIf { it in 100..250 }?.let { it / 100.0 * (if (sex == "f") .413 else .415) } ?: .74
/** Dépense au repos (kcal par jour, formule de Mifflin-St Jeor) ; null s'il manque la taille, l'âge, le sexe ou le poids. */
fun Gear.bmr(bw: Double?): Double? {
    val h = height?.takeIf { it in 100..250 } ?: return null; val a = age() ?: return null; val w = bw ?: return null
    return 10 * w + 6.25 * h - 5 * a + when (sex) { "m" -> 5.0; "f" -> -161.0; else -> return null }
}
