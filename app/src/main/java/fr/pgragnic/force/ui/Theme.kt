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
    val DARK = darkColorScheme(primary = Color(0xFFFBB7A0), onPrimary = Color(0xFF4E2515), primaryContainer = Color(0xFFC98B76), onPrimaryContainer = Color(0xFF502617), inversePrimary = Color(0xFF85513F), secondary = Color(0xFFE2BFB3), onSecondary = Color(0xFF412B23), secondaryContainer = Color(0xFF5D433B), onSecondaryContainer = Color(0xFFD4B1A6), tertiary = Color(0xFF98D1CC), onTertiary = Color(0xFF003734), tertiaryContainer = Color(0xFF6CA49F), onTertiaryContainer = Color(0xFF003936), background = Color(0xFF171211), onBackground = Color(0xFFECE0DD), surface = Color(0xFF171211), onSurface = Color(0xFFECE0DD), surfaceVariant = Color(0xFF52443F), onSurfaceVariant = Color(0xFFD7C2BC), surfaceTint = Color(0xFFFBB7A0), inverseSurface = Color(0xFFECE0DD), inverseOnSurface = Color(0xFF352F2D), error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6), outline = Color(0xFF9F8D87), outlineVariant = Color(0xFF52443F), scrim = Color(0xFF000000), surfaceBright = Color(0xFF3E3836), surfaceContainer = Color(0xFF241E1D), surfaceContainerHigh = Color(0xFF2F2927), surfaceContainerHighest = Color(0xFF3A3331), surfaceContainerLow = Color(0xFF201A19), surfaceContainerLowest = Color(0xFF120D0C), surfaceDim = Color(0xFF171211))
    val LIGHT = lightColorScheme(primary = Color(0xFF85513F), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFC98B76), onPrimaryContainer = Color(0xFF502617), inversePrimary = Color(0xFFFBB7A0), secondary = Color(0xFF74584F), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFFDD8CC), onSecondaryContainer = Color(0xFF785C53), tertiary = Color(0xFF2E6763), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFF6CA49F), onTertiaryContainer = Color(0xFF003936), background = Color(0xFFFFF8F6), onBackground = Color(0xFF201A19), surface = Color(0xFFFFF8F6), onSurface = Color(0xFF201A19), surfaceVariant = Color(0xFFF4DED7), onSurfaceVariant = Color(0xFF52443F), surfaceTint = Color(0xFF85513F), inverseSurface = Color(0xFF352F2D), inverseOnSurface = Color(0xFFFAEEEB), error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A), outline = Color(0xFF84736E), outlineVariant = Color(0xFFD7C2BC), scrim = Color(0xFF000000), surfaceBright = Color(0xFFFFF8F6), surfaceContainer = Color(0xFFF7EBE8), surfaceContainerHigh = Color(0xFFF1E6E2), surfaceContainerHighest = Color(0xFFECE0DD), surfaceContainerLow = Color(0xFFFDF1EE), surfaceContainerLowest = Color(0xFFFFFFFF), surfaceDim = Color(0xFFE3D8D4))

    private var cache: Triple<Boolean, Boolean, ColorScheme>? = null
    /** Schéma Material courant (marque ou fond d'écran). */
    val s: ColorScheme get() {
        val d = dark; val y = dyn && dynOk && app != null
        cache?.let { if (it.first == d && it.second == y) return it.third }
        // style « Fitness » (Philippe, 14 novembre 11 h 15 : « Ok » à la voie mélangée inspirée d'Apple Fitness) :
        // fond noir et cartes gris très foncé en sombre ; fond gris clair et cartes blanches en clair
        // ambiance « Glamour » (Philippe, 14 novembre 12 h 06 : « J'aime bien l'option C ») : noir et or rose en sombre, crème et cuivre en clair
        val cs = (if (y) (if (d) dynamicDarkColorScheme(app!!) else dynamicLightColorScheme(app!!)) else if (d) DARK else LIGHT).let {
            if (d) it.copy(background = Color(0xFF0E0B0C), surface = Color(0xFF0E0B0C), surfaceContainerLowest = Color(0xFF0A0809), surfaceContainerLow = Color(0xFF141012),
                surfaceContainer = Color(0xFF181314), surfaceContainerHigh = Color(0xFF1E1719), surfaceContainerHighest = Color(0xFF2A2124), outlineVariant = Color(0x2EE8B4A0),
                onSurface = Color(0xFFF6ECE8), onSurfaceVariant = Color(0xFF9C908C))
            else it.copy(background = Color(0xFFF8F1EE), surface = Color(0xFFF8F1EE), surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color.White,
                surfaceContainerHighest = Color(0xFFF1E4DF), outlineVariant = Color(0x40B9775F), onSurface = Color(0xFF2A1812), onSurfaceVariant = Color(0xFF8A716A))
        }
        cache = Triple(d, y, cs); return cs
    }
    private fun p(d: Long, l: Long) = Color(if (dark) d else l)
    // neutres et marque : tirés du schéma Material
    val bg get() = s.surface; val surface get() = s.surfaceContainerHigh; val ink get() = s.onSurface
    val muted get() = s.onSurfaceVariant; val line get() = s.outlineVariant
    /** or rose (sombre) / cuivre (clair) ; avec Material You, la couleur du fond d'écran */
    val brand get() = if (dyn && dynOk) s.primary else p(0xFFE7B3A0, 0xFFA9654E); val onBrand get() = if (dyn && dynOk) s.onPrimary else p(0xFF2A1812, 0xFFFFFFFF)
    /** dégradé or rose des boutons principaux et du segment choisi, texte foncé dessus */
    val gold1 get() = Color(0xFFF5D2C2); val gold2 get() = Color(0xFFC98B76); val onGold get() = Color(0xFF2A1812)
    /** cartes : léger dégradé et filet cuivré */
    val card1 get() = p(0xFF1E1719, 0xFFFFFFFF); val card2 get() = p(0xFF151112, 0xFFFCF6F3); val rim get() = Color(if (dark) 0x2EE8B4A0 else 0x40B9775F)
    val brandC get() = s.primaryContainer; val onBrandC get() = s.onPrimaryContainer
    val sel get() = s.secondaryContainer; val onSel get() = s.onSecondaryContainer
    /** segment choisi d'un sélecteur (gris clair sur piste foncée, blanc sur piste claire) */
    val segOn get() = gold2
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

/** Corps de texte Quicksand (arrondie, douce) ; chiffres et très gras en Nunito Black (ambiance Glamour, 14 novembre). */
val Rounded = FontFamily(
    Font(fr.pgragnic.force.R.font.quicksand_600, FontWeight.Normal), Font(fr.pgragnic.force.R.font.quicksand_500, FontWeight.Light),
    Font(fr.pgragnic.force.R.font.quicksand_700, FontWeight.Medium), Font(fr.pgragnic.force.R.font.quicksand_700, FontWeight.SemiBold),
    Font(fr.pgragnic.force.R.font.quicksand_700, FontWeight.Bold), Font(fr.pgragnic.force.R.font.quicksand_700, FontWeight.ExtraBold),
    Font(fr.pgragnic.force.R.font.nunito_900, FontWeight.Black))

/** Titres : Playfair Display, italique pour les grands titres, droit pour les titres de carte. */
val Serif = FontFamily(Font(fr.pgragnic.force.R.font.playfair_600_italic, FontWeight.SemiBold, androidx.compose.ui.text.font.FontStyle.Italic),
    Font(fr.pgragnic.force.R.font.playfair_700, FontWeight.Bold))

/** Carte « Glamour » : très arrondie, léger dégradé, filet cuivré. */
fun androidx.compose.ui.Modifier.glam(r: Int = 30) = this.clip(androidx.compose.foundation.shape.RoundedCornerShape(r.dp))
    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(C.card1, C.card2)))
    .border(1.dp, C.rim, androidx.compose.foundation.shape.RoundedCornerShape(r.dp))

/** Dégradé or rose (boutons principaux, segment choisi). */
val goldBrush get() = androidx.compose.ui.graphics.Brush.linearGradient(listOf(C.gold1, C.gold2))

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
