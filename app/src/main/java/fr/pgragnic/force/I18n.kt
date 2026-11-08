package fr.pgragnic.force

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Langue de l'appli : français ou anglais (comme le téléphone par défaut). Le dictionnaire vient du programme (clé EN). */
object L {
    var en by mutableStateOf(false)
    /** "auto", "fr" ou "en" */
    var mode by mutableStateOf("auto")
    val dict = HashMap<String, String>()
    val loc: Locale get() = if (en) Locale.ENGLISH else fr.pgragnic.force.L.loc

    fun load(ctx: Context) {
        mode = ctx.getSharedPreferences("force", Context.MODE_PRIVATE).getString("lang", "auto") ?: "auto"
        en = pick(mode)
    }
    fun save(ctx: Context, m: String) {
        mode = m; en = pick(m)
        ctx.getSharedPreferences("force", Context.MODE_PRIVATE).edit().putString("lang", m).apply()
    }
    private fun pick(m: String) = when (m) { "en" -> true; "fr" -> false; else -> Locale.getDefault().language != "fr" }
}

private val SEPS = listOf(" · ", ". ", " : ", " ; ", " — ")

/** Traduit un texte affiché (ou dit à voix haute) ; les textes composés sont traduits morceau par morceau. */
fun tr(s: String): String {
    if (!L.en || s.isBlank()) return s
    L.dict[s]?.let { return it }
    val core = s.trimEnd('.', '!', ' ')
    if (core != s) L.dict[core]?.let { return it + s.substring(core.length) }
    for (sep in SEPS) if (s.contains(sep)) {
        val parts = s.split(sep)
        if (parts.size > 1) return parts.joinToString(if (sep == " : ") ": " else if (sep == " ; ") "; " else sep) { tr(it) }
    }
    return s
}

/** Texte avec valeurs : version française ou anglaise. */
fun t(fr: String, en: String) = if (L.en) en else fr
