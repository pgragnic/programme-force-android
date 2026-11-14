package fr.pgragnic.force.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Couleurs de l'appli. Depuis le 14 novembre 2026 : Material 3, couleur de marque indigo #4C5BD4
 * (Philippe : « Partons sur l'indigo en material 3 », puis « Oui » à : indigo par défaut + option « Couleurs du fond d'écran »).
 * Palette générée par material-color-utilities (SchemeFidelity, graine #4C5BD4).
 * Les couleurs qui portent une information (phases de séance, récupération) ne dépendent pas de la marque.
 */
object C {
    /** "auto", "light" ou "dark" */
    var mode by mutableStateOf("auto")
    var sysDark by mutableStateOf(true)
    /** Material You : couleurs tirées du fond d'écran (Android 12 et plus) */
    var dyn by mutableStateOf(false)
    val dynOk get() = android.os.Build.VERSION.SDK_INT >= 31
    private var app: Context? = null
    val dark get() = when (mode) { "dark" -> true; "light" -> false; else -> sysDark }
    val DARK = darkColorScheme(primary = Color(0xFFBCC2FF), onPrimary = Color(0xFF00169C), primaryContainer = Color(0xFF4C5BD4), onPrimaryContainer = Color(0xFFE6E6FF), inversePrimary = Color(0xFF4251CA), secondary = Color(0xFFBEC3F8), onSecondary = Color(0xFF272D58), secondaryContainer = Color(0xFF404673), onSecondaryContainer = Color(0xFFB0B5E9), tertiary = Color(0xFFFFB688), onTertiary = Color(0xFF512400), tertiaryContainer = Color(0xFFA55000), onTertiaryContainer = Color(0xFFFFE2D2), background = Color(0xFF12131A), onBackground = Color(0xFFE3E1EC), surface = Color(0xFF12131A), onSurface = Color(0xFFE3E1EC), surfaceVariant = Color(0xFF454654), onSurfaceVariant = Color(0xFFC6C5D6), surfaceTint = Color(0xFFBCC2FF), inverseSurface = Color(0xFFE3E1EC), inverseOnSurface = Color(0xFF2F3038), error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6), outline = Color(0xFF8F8F9F), outlineVariant = Color(0xFF454654), scrim = Color(0xFF000000), surfaceBright = Color(0xFF383841), surfaceContainer = Color(0xFF1F1F27), surfaceContainerHigh = Color(0xFF292931), surfaceContainerHighest = Color(0xFF34343C), surfaceContainerLow = Color(0xFF1A1B22), surfaceContainerLowest = Color(0xFF0D0E15), surfaceDim = Color(0xFF12131A))
    val LIGHT = lightColorScheme(primary = Color(0xFF3141BA), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFF4C5BD4), onPrimaryContainer = Color(0xFFE6E6FF), inversePrimary = Color(0xFFBCC2FF), secondary = Color(0xFF565B8A), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFC4C8FE), onSecondaryContainer = Color(0xFF4D5381), tertiary = Color(0xFF803D00), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFA55000), onTertiaryContainer = Color(0xFFFFE2D2), background = Color(0xFFFBF8FF), onBackground = Color(0xFF1A1B22), surface = Color(0xFFFBF8FF), onSurface = Color(0xFF1A1B22), surfaceVariant = Color(0xFFE2E1F2), onSurfaceVariant = Color(0xFF454654), surfaceTint = Color(0xFF4251CA), inverseSurface = Color(0xFF2F3038), inverseOnSurface = Color(0xFFF2EFFA), error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A), outline = Color(0xFF767685), outlineVariant = Color(0xFFC6C5D6), scrim = Color(0xFF000000), surfaceBright = Color(0xFFFBF8FF), surfaceContainer = Color(0xFFEFECF7), surfaceContainerHigh = Color(0xFFE9E7F2), surfaceContainerHighest = Color(0xFFE3E1EC), surfaceContainerLow = Color(0xFFF5F2FD), surfaceContainerLowest = Color(0xFFFFFFFF), surfaceDim = Color(0xFFDBD9E3))

    private var cache: Triple<Boolean, Boolean, ColorScheme>? = null
    /** Schéma Material courant (marque ou fond d'écran). */
    val s: ColorScheme get() {
        val d = dark; val y = dyn && dynOk && app != null
        cache?.let { if (it.first == d && it.second == y) return it.third }
        val cs = if (y) (if (d) dynamicDarkColorScheme(app!!) else dynamicLightColorScheme(app!!)) else if (d) DARK else LIGHT
        cache = Triple(d, y, cs); return cs
    }
    private fun p(d: Long, l: Long) = Color(if (dark) d else l)
    // neutres et marque : tirés du schéma Material
    val bg get() = s.surface; val surface get() = s.surfaceContainerHigh; val ink get() = s.onSurface
    val muted get() = s.onSurfaceVariant; val line get() = s.outlineVariant
    val brand get() = s.primary; val onBrand get() = s.onPrimary
    val brandC get() = s.primaryContainer; val onBrandC get() = s.onPrimaryContainer
    val sel get() = s.secondaryContainer; val onSel get() = s.onSecondaryContainer
    // scène du personnage 3D
    val stage get() = p(0xFF0C0F12, 0xFF15181C); val stage2 get() = p(0xFF181C21, 0xFF20252B); val stage3 get() = p(0xFF21262C, 0xFF2A3037)
    // couleurs porteuses d'information : inchangées
    val effort get() = p(0xFF2EC089, 0xFF0E8A5E); val rest get() = p(0xFF5E93F2, 0xFF2B63CC); val switch get() = p(0xFFE66AAE, 0xFFB4307A)
    val warm get() = p(0xFFE3A33E, 0xFFB87412); val cool get() = p(0xFFA88DEC, 0xFF7556C0); val hot get() = p(0xFFE0453A, 0xFFC2410C)
    val danger get() = p(0xFFF06A6A, 0xFFD23B3B); val fig get() = p(0xFFB9C6CF, 0xFF5B6B78); val mat get() = p(0xFF25333C, 0xFFC9D6D2)
    val db get() = p(0xFF1F9E6F, 0xFF0E8A5E)
    val mrest get() = p(0xFFD5D8DC, 0xFFDDE0E3) /* gris clair façon écorché */; val mhot get() = p(0xFFE0453A, 0xFFD7372C)
    /** Texte posé sur une pastille de phase (vert, bleu…). */
    val onColor get() = if (dark) Color(0xFF06261A) else Color.White
    fun phase(k: String) = when (k) { "work" -> effort; "rest" -> rest; "switch" -> switch; "warm" -> warm; "cool" -> cool; else -> muted }
    fun rgb(c: Color) = c.toArgb() and 0xFFFFFF

    fun load(ctx: Context) {
        app = ctx.applicationContext
        sysDark = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val sp = ctx.getSharedPreferences("force", Context.MODE_PRIVATE)
        mode = sp.getString("theme", "auto") ?: "auto"; dyn = sp.getBoolean("dyn", false) }
    fun save(ctx: Context, m: String) { mode = m; ctx.getSharedPreferences("force", Context.MODE_PRIVATE).edit().putString("theme", m).apply() }
    fun saveDyn(ctx: Context, on: Boolean) { dyn = on; ctx.getSharedPreferences("force", Context.MODE_PRIVATE).edit().putBoolean("dyn", on).apply() }
}

val PLAB = mapOf("prep" to "PRÊT ?", "warm" to "ÉCHAUFFEMENT", "work" to "EFFORT", "rest" to "PAUSE", "cool" to "RETOUR AU CALME", "switch" to "CHANGEMENT")

@Composable
fun ForceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = C.s, content = content)
}
