package fr.pgragnic.force.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

/** Écran d'accueil au premier lancement : compte Google ou e-mail, ou « Continuer sans compte » (sans coach IA) — 20 h 31 / 20 h 35. Origine (Philippe, 17 novembre 20 h 25 : « Je souhaite que les utilisateurs
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
        Text(t("Avec un compte, tu profites du coach IA, et tes séances et ton programme sont sauvegardés et te suivent d'un téléphone à l'autre.",
            "With an account you get the AI coach, and your workouts and program are backed up and follow you from one phone to another."),
            color = C.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        AccountPanel(welcome = true)
        // sans compte : possible, mais sans coach IA ni sauvegarde en ligne
        var warn by remember { mutableStateOf(false) }
        TextButton(onClick = { warn = true }, modifier = Modifier.padding(top = 4.dp)) { Text(t("Continuer sans compte", "Continue without an account"), color = C.ink, fontWeight = FontWeight.SemiBold) }
        if (warn) AlertDialog(onDismissRequest = { warn = false }, containerColor = C.card1,
            title = { Text(t("Continuer sans compte ?", "Continue without an account?"), color = C.ink, fontWeight = FontWeight.Bold) },
            text = { Text(t("Sans compte, pas de coach IA, ni de sauvegarde en ligne ou de synchronisation entre téléphones. Tes séances restent sur ce téléphone. Tu pourras créer un compte plus tard dans Réglages → Compte et sauvegarde : tes séances y seront envoyées.",
                "Without an account, no AI coach, online backup or sync between phones. Your workouts stay on this phone. You can create an account later in Settings → Account and backup: your workouts will be sent to it."), color = C.ink, fontSize = 14.sp) },
            confirmButton = { TextButton(onClick = { warn = false; Store.setGuest(true) }) { Text(t("Continuer sans compte", "Continue without an account"), color = C.brand, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { warn = false }) { Text(t("Créer un compte", "Create an account"), color = C.muted) } })
        TextButton(onClick = { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(SITE + if (L.en) "privacy.html" else "confidentialite.html"))) } },
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)) { Text(t("Politique de confidentialité", "Privacy policy"), color = C.muted, fontSize = 13.sp) }
    }
}


/** Coach réservé aux comptes : carte affichée à la place du coach quand on n'est pas connecté. */
@Composable
fun CoachLocked(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().glam().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoachAvatar(36); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Le coach IA est réservé aux comptes", "The AI coach requires an account"), color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(t("Il adapte tes séances (forme, temps, gêne, vacances), explique chaque changement et se souvient de 30 jours.",
                    "It adapts your workouts (energy, time, discomfort, holidays), explains every change and remembers 30 days."), color = C.muted, fontSize = 13.sp)
            }
        }
        Button(onClick = { open = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(t("Créer un compte ou se connecter", "Create an account or sign in"), fontWeight = FontWeight.Bold) }
    }
    if (open) androidx.compose.ui.window.Dialog(onDismissRequest = { open = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        val user by Store.user.collectAsState()
        LaunchedEffect(user) { if (user != null) open = false }
        Column(Modifier.fillMaxSize().background(C.bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { open = false }) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, t("Retour", "Back"), tint = C.ink) }
                Text(t("Compte", "Account"), color = C.ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(8.dp))
            AccountPanel(welcome = true)
        }
    }
}
