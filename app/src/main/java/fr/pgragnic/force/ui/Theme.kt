package fr.pgragnic.force.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
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
    val DARK = darkColorScheme(primary = Color(0xFFA9C7FF), onPrimary = Color(0xFF003063), primaryContainer = Color(0xFF3B90FF), onPrimaryContainer = Color(0xFF00224A), inversePrimary = Color(0xFF005DB7), secondary = Color(0xFFAAC7FD), onSecondary = Color(0xFF0C305D), secondaryContainer = Color(0xFF2B4978), onSecondaryContainer = Color(0xFF9CB9EE), tertiary = Color(0xFFFFB68C), onTertiary = Color(0xFF532200), tertiaryContainer = Color(0xFFE76E0D), onTertiaryContainer = Color(0xFF3D1700), background = Color(0xFF10131A), onBackground = Color(0xFFE0E2EC), surface = Color(0xFF10131A), onSurface = Color(0xFFE0E2EC), surfaceVariant = Color(0xFF414754), onSurfaceVariant = Color(0xFFC1C6D6), surfaceTint = Color(0xFFA9C7FF), inverseSurface = Color(0xFFE0E2EC), inverseOnSurface = Color(0xFF2D3038), error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6), outline = Color(0xFF8B919F), outlineVariant = Color(0xFF414754), scrim = Color(0xFF000000), surfaceBright = Color(0xFF363941), surfaceContainer = Color(0xFF1C2027), surfaceContainerHigh = Color(0xFF262A31), surfaceContainerHighest = Color(0xFF31353D), surfaceContainerLow = Color(0xFF181C23), surfaceContainerLowest = Color(0xFF0B0E15), surfaceDim = Color(0xFF10131A))
    val LIGHT = lightColorScheme(primary = Color(0xFF005BB2), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFF0073DF), onPrimaryContainer = Color(0xFFFEFCFF), inversePrimary = Color(0xFFA9C7FF), secondary = Color(0xFF425F8E), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFAAC7FD), onSecondaryContainer = Color(0xFF355382), tertiary = Color(0xFF964400), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFBD5700), onTertiaryContainer = Color(0xFFFFFBFF), background = Color(0xFFF9F9FF), onBackground = Color(0xFF181C23), surface = Color(0xFFF9F9FF), onSurface = Color(0xFF181C23), surfaceVariant = Color(0xFFDDE2F3), onSurfaceVariant = Color(0xFF414754), surfaceTint = Color(0xFF005DB7), inverseSurface = Color(0xFF2D3038), inverseOnSurface = Color(0xFFEEF0FB), error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A), outline = Color(0xFF717785), outlineVariant = Color(0xFFC1C6D6), scrim = Color(0xFF000000), surfaceBright = Color(0xFFF9F9FF), surfaceContainer = Color(0xFFEBEDF8), surfaceContainerHigh = Color(0xFFE6E8F2), surfaceContainerHighest = Color(0xFFE0E2EC), surfaceContainerLow = Color(0xFFF1F3FD), surfaceContainerLowest = Color(0xFFFFFFFF), surfaceDim = Color(0xFFD7DAE4))

    private var cache: Triple<Boolean, Boolean, ColorScheme>? = null
    /** Schéma Material courant (marque ou fond d'écran). */
    val s: ColorScheme get() {
        val d = dark; val y = dyn && dynOk && app != null
        cache?.let { if (it.first == d && it.second == y) return it.third }
        // style « Fitness » (Philippe, 14 novembre 11 h 15 : « Ok » à la voie mélangée inspirée d'Apple Fitness) :
        // fond noir et cartes gris très foncé en sombre ; fond gris clair et cartes blanches en clair
        // style épuré (Philippe, 14 novembre 14 h 39 : « Mon sentiment cest que l'application est trop chargée, que penses-tu d'un style épuré comme ça ? »,
        // 14 h 40 : « Couleur franche », 14 h 44 : « Partons sur le bleu vif ») : fond blanc cassé et tuiles blanches, ou gris très foncé en sombre
        val cs = (if (y) (if (d) dynamicDarkColorScheme(app!!) else dynamicLightColorScheme(app!!)) else if (d) DARK else LIGHT).let {
            if (d) it.copy(background = Color(0xFF0E0E0F), surface = Color(0xFF0E0E0F), surfaceContainerLowest = Color(0xFF0E0E0F), surfaceContainerLow = Color(0xFF151517),
                surfaceContainer = Color(0xFF1A1A1C), surfaceContainerHigh = Color(0xFF1A1A1C), surfaceContainerHighest = Color(0xFF2A2A2C), outlineVariant = Color(0xFF2A2A2C),
                onSurface = Color(0xFFF2F2F0), onSurfaceVariant = Color(0xFF8E8E8A))
            else it.copy(background = Color(0xFFF7F6F2), surface = Color(0xFFF7F6F2), surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F0EC),
                surfaceContainer = Color.White, surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFEDECE7), outlineVariant = Color(0xFFEDECE7),
                onSurface = Color(0xFF111111), onSurfaceVariant = Color(0xFF8A8A86))
        }
        cache = Triple(d, y, cs); return cs
    }
    private fun p(d: Long, l: Long) = Color(if (dark) d else l)
    // neutres et marque : tirés du schéma Material
    val bg get() = s.surface; val surface get() = s.surfaceContainerHigh; val ink get() = s.onSurface
    val muted get() = s.onSurfaceVariant; val line get() = s.outlineVariant
    /** or rose (sombre) / cuivre (clair) ; avec Material You, la couleur du fond d'écran */
    val brand get() = if (dyn && dynOk) s.primary else Color(0xFF1E88FF); val onBrand get() = if (dyn && dynOk) s.onPrimary else Color.White
    /** dégradé or rose des boutons principaux et du segment choisi, texte foncé dessus */
    val gold1 get() = brand; val gold2 get() = brand; val onGold get() = onBrand
    /** cartes : léger dégradé et filet cuivré */
    val card1 get() = s.surfaceContainerHigh; val card2 get() = s.surfaceContainerHigh; val rim get() = Color.Transparent
    /** fond d'un segment ou d'une pastille neutre */
    val chip get() = s.surfaceContainerHighest
    val brandC get() = s.primaryContainer; val onBrandC get() = s.onPrimaryContainer
    val sel get() = s.secondaryContainer; val onSel get() = s.onSecondaryContainer
    /** segment choisi d'un sélecteur (gris clair sur piste foncée, blanc sur piste claire) */
    val segOn get() = p(0xFF3A3A3C, 0xFFFFFFFF)
    // scène du personnage 3D
    val stage get() = p(0xFF1A1A1C, 0xFFFFFFFF); val stage2 get() = p(0xFF232326, 0xFFF1F0EC); val stage3 get() = p(0xFF2C2C30, 0xFFE4E3DE)
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

/** Une seule police, Inter, droite et nette (style épuré du 14 novembre). Nom « Rounded » gardé pour ne pas toucher aux appels. */
val Rounded = FontFamily(
    Font(fr.pgragnic.force.R.font.inter_400, FontWeight.Normal), Font(fr.pgragnic.force.R.font.inter_400, FontWeight.Light),
    Font(fr.pgragnic.force.R.font.inter_500, FontWeight.Medium), Font(fr.pgragnic.force.R.font.inter_600, FontWeight.SemiBold),
    Font(fr.pgragnic.force.R.font.inter_700, FontWeight.Bold), Font(fr.pgragnic.force.R.font.inter_800, FontWeight.ExtraBold),
    Font(fr.pgragnic.force.R.font.inter_800, FontWeight.Black))
val Serif = Rounded

/** Tuile épurée : blanche (ou gris très foncé), coins arrondis, sans contour ni dégradé. */
fun androidx.compose.ui.Modifier.glam(r: Int = 22) = this.clip(androidx.compose.foundation.shape.RoundedCornerShape(r.dp)).background(C.card1)

/** Fond des boutons principaux : la couleur d'accent, unie. */
val goldBrush get() = androidx.compose.ui.graphics.SolidColor(C.brand)

private val Typo = Typography().let { t ->
    Typography(t.displayLarge.copy(fontFamily = Rounded), t.displayMedium.copy(fontFamily = Rounded), t.displaySmall.copy(fontFamily = Rounded),
        t.headlineLarge.copy(fontFamily = Rounded), t.headlineMedium.copy(fontFamily = Rounded), t.headlineSmall.copy(fontFamily = Rounded),
        t.titleLarge.copy(fontFamily = Rounded), t.titleMedium.copy(fontFamily = Rounded), t.titleSmall.copy(fontFamily = Rounded),
        t.bodyLarge.copy(fontFamily = Rounded), t.bodyMedium.copy(fontFamily = Rounded), t.bodySmall.copy(fontFamily = Rounded),
        t.labelLarge.copy(fontFamily = Rounded), t.labelMedium.copy(fontFamily = Rounded), t.labelSmall.copy(fontFamily = Rounded))
}

@Composable
fun ForceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = C.s, typography = Typo, content = content)
}
