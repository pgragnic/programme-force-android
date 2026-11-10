package fr.pgragnic.force.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Couleurs de l'appli (mêmes valeurs que la page web), en clair ou en sombre. */
object C {
    /** "auto", "light" ou "dark" */
    var mode by mutableStateOf("auto")
    var sysDark by mutableStateOf(true)
    val dark get() = when (mode) { "dark" -> true; "light" -> false; else -> sysDark }
    private fun p(d: Long, l: Long) = Color(if (dark) d else l)
    val bg get() = p(0xFF0E1317, 0xFFEEF2F1); val surface get() = p(0xFF172027, 0xFFFFFFFF); val ink get() = p(0xFFE7EDEF, 0xFF15202A)
    val muted get() = p(0xFF94A1AB, 0xFF5A6772); val line get() = p(0xFF26323B, 0xFFD5DDDB)
    val stage get() = p(0xFF0C0F12, 0xFF15181C); val stage2 get() = p(0xFF181C21, 0xFF20252B); val stage3 get() = p(0xFF21262C, 0xFF2A3037)
    val effort get() = p(0xFF2EC089, 0xFF0E8A5E); val rest get() = p(0xFF5E93F2, 0xFF2B63CC); val switch get() = p(0xFFE66AAE, 0xFFB4307A)
    val warm get() = p(0xFFE3A33E, 0xFFB87412); val cool get() = p(0xFFA88DEC, 0xFF7556C0); val hot get() = p(0xFFE0453A, 0xFFC2410C)
    val danger get() = p(0xFFF06A6A, 0xFFD23B3B); val fig get() = p(0xFFB9C6CF, 0xFF5B6B78); val mat get() = p(0xFF25333C, 0xFFC9D6D2)
    val db get() = p(0xFF1F9E6F, 0xFF0E8A5E)
    val mrest get() = p(0xFFD5D8DC, 0xFFDDE0E3)   // gris clair façon écorché; val mhot get() = p(0xFFE0453A, 0xFFD7372C)
    /** Texte posé sur une pastille colorée. */
    val onColor get() = if (dark) Color(0xFF06261A) else Color.White
    fun phase(k: String) = when (k) { "work" -> effort; "rest" -> rest; "switch" -> switch; "warm" -> warm; "cool" -> cool; else -> muted }
    fun rgb(c: Color) = c.toArgb() and 0xFFFFFF

    fun load(ctx: Context) {
        sysDark = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        mode = ctx.getSharedPreferences("force", Context.MODE_PRIVATE).getString("theme", "auto") ?: "auto" }
    fun save(ctx: Context, m: String) { mode = m; ctx.getSharedPreferences("force", Context.MODE_PRIVATE).edit().putString("theme", m).apply() }
}

val PLAB = mapOf("prep" to "PRÊT ?", "warm" to "ÉCHAUFFEMENT", "work" to "EFFORT", "rest" to "PAUSE", "cool" to "RETOUR AU CALME", "switch" to "CHANGEMENT")

@Composable
fun ForceTheme(content: @Composable () -> Unit) {
    val cs = if (C.dark) darkColorScheme(
        background = C.bg, surface = C.surface, onBackground = C.ink, onSurface = C.ink,
        primary = C.effort, onPrimary = C.onColor, secondary = C.rest, outline = C.line,
        surfaceVariant = C.surface, onSurfaceVariant = C.muted, error = C.danger
    ) else lightColorScheme(
        background = C.bg, surface = C.surface, onBackground = C.ink, onSurface = C.ink,
        primary = C.effort, onPrimary = C.onColor, secondary = C.rest, outline = C.line,
        surfaceVariant = C.surface, onSurfaceVariant = C.muted, error = C.danger
    )
    MaterialTheme(colorScheme = cs, content = content)
}
