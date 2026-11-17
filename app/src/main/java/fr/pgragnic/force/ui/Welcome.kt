package fr.pgragnic.force.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pgragnic.force.*

/** Écran d'accueil : connexion obligatoire avant d'utiliser l'appli (Philippe, 17 novembre 20 h 25 : « Je souhaite que les utilisateurs
 *  soient obligés de s'authentifier pour ouvrir l'application ? » ; 20 h 28 : « Avec email et connection Google »). */
@Composable
fun WelcomeScreen() {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().background(C.bg).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(24.dp)).background(C.brand), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.FitnessCenter, null, tint = C.onBrand, modifier = Modifier.size(44.dp))
        }
        Text("UpRep", color = C.ink, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 16.dp))
        Text(t("Ta remise en forme à la maison, séance après séance.", "Your home fitness, workout after workout."), color = C.muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Text(t("Connecte-toi pour commencer : tes séances, ton programme et ton coach sont sauvegardés avec ton compte et te suivent d'un téléphone à l'autre.",
            "Sign in to start: your workouts, program and coach are saved with your account and follow you from one phone to another."),
            color = C.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        AccountPanel(welcome = true)
        TextButton(onClick = { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(SITE + if (L.en) "privacy.html" else "confidentialite.html"))) } },
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)) { Text(t("Politique de confidentialité", "Privacy policy"), color = C.muted, fontSize = 13.sp) }
    }
}
