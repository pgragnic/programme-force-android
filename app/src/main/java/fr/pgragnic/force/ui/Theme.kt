package fr.pgragnic.force.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object C {
    val bg = Color(0xFF0E1317); val surface = Color(0xFF172027); val ink = Color(0xFFE7EDEF)
    val muted = Color(0xFF94A1AB); val line = Color(0xFF26323B); val stage = Color(0xFF0C0F12)
    val effort = Color(0xFF2EC089); val rest = Color(0xFF5E93F2); val switch = Color(0xFFE66AAE)
    val warm = Color(0xFFE3A33E); val cool = Color(0xFFA88DEC); val hot = Color(0xFFE0453A)
    val danger = Color(0xFFF06A6A); val fig = Color(0xFFB9C6CF); val mat = Color(0xFF25333C)
    val db = Color(0xFF1F9E6F)
    fun phase(k: String) = when (k) { "work" -> effort; "rest" -> rest; "switch" -> switch; "warm" -> warm; "cool" -> cool; else -> muted }
}

val PLAB = mapOf("prep" to "PRÊT ?", "warm" to "ÉCHAUFFEMENT", "work" to "EFFORT", "rest" to "PAUSE", "cool" to "RETOUR AU CALME", "switch" to "CHANGEMENT")

@Composable
fun ForceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        background = C.bg, surface = C.surface, onBackground = C.ink, onSurface = C.ink,
        primary = C.effort, onPrimary = Color(0xFF06261A), secondary = C.rest, outline = C.line,
        surfaceVariant = C.surface, onSurfaceVariant = C.muted, error = C.danger
    ), content = content)
}
