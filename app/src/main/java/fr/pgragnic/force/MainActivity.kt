package fr.pgragnic.force

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.sp
import fr.pgragnic.force.ui.*
import fr.pgragnic.force.ui.Text
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) intent = sansOptionsDeControle(intent)
        enableEdgeToEdge()
        L.load(this); Prog.load(this); GearStore.load(this); Store.init(this)
        C.load(this); Adapt.load(this); Recup.load(this); CoachState.load(this); CoachData.load(this); CoachRem.schedule(this); SessionService.channels(this); Reminder.schedule(this)
        Store.handleLink(intent)
        Crash.sendPending(this)
        // Options de contrôle utilisées par les captures automatiques (sans effet en usage normal)
        intent.getStringExtra("theme")?.let { C.mode = it }
        if (intent.hasExtra("dyn")) C.dyn = intent.getBooleanExtra("dyn", false)
        intent.getStringExtra("lang")?.let { L.mode = it; L.en = it == "en" }
        Demo.tired = intent.getBooleanExtra("tired", false); Demo.over = intent.getBooleanExtra("over", false); Demo.legs = intent.getBooleanExtra("legs", false)
        if (intent.getBooleanExtra("seed", false) && Store.log.value.isEmpty()) Demo.seed()
        if (intent.getBooleanExtra("seed", false) && CoachData.d.msgs.isEmpty()) Demo.seedCoach(this)
        intent.getStringExtra("gear")?.let { Demo.gear(it) }
        intent.getIntExtra("perweek", 0).takeIf { it in 3..6 }?.let { GearStore.save(this, GearStore.g.value.copy(perWeek = it)) }
        Demo.setupStep = intent.getIntExtra("setupstep", 0)
        Demo.muscle = intent.getStringExtra("muscle")
        Demo.sub = intent.getStringExtra("sub")
        intent.getStringExtra("reg")?.let { fr.pgragnic.force.ui.regSub.value = it }
        Demo.sub?.takeIf { it.toIntOrNull() == null }?.let { fr.pgragnic.force.ui.regSub.value = it }
        if (intent.getBooleanExtra("opensetup", false)) fr.pgragnic.force.ui.setupOpen.value = true
        if (intent.hasExtra("seed") || intent.hasExtra("gear") || intent.hasExtra("demo")) getSharedPreferences("force", MODE_PRIVATE).edit().putBoolean("avert", true).putBoolean("tour", !intent.getBooleanExtra("tour", false)).apply()
        runCatching {
            getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { Store.autoSync() }
            })
        }
        setContent {
            val dk = C.dark
            LaunchedEffect(dk) { androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).run { isAppearanceLightStatusBars = !dk; isAppearanceLightNavigationBars = !dk } }
            ForceTheme { App(); HealthNotice(); TourOverlay() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        val i = if (BuildConfig.DEBUG) intent else sansOptionsDeControle(intent)
        super.onNewIntent(i); setIntent(i); Store.handleLink(i)
    }

    // Options de contrôle (seed, demo, gear, perweek, noauth, play, theme, lang…) réservées à l'APK debug des captures automatiques
    // (audit de sécurité du 22 novembre 2026, point 3 ; Philippe : « Correction points 1,2,3 ») : l'activité est exportée, donc
    // en version publiée une autre appli du téléphone pourrait sinon injecter des séances fictives ou écraser les réglages.
    // Seul « tab » reste accepté : la notification du coach l'utilise (tab = 9). Le lien de connexion (data) est conservé.
    private fun sansOptionsDeControle(i: Intent): Intent {
        val e = i.extras ?: return i
        val garde = Bundle().apply { if (e.containsKey("tab")) putInt("tab", i.getIntExtra("tab", 0)) }
        return Intent(i).replaceExtras(garde)
    }

    override fun onResume() { super.onResume(); Store.autoSync() }


    private fun keepOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @Composable
    fun App() {
        // onglets : 0 Séance, 1 Corps, 2 Coach, 3 Progrès, 4 Réglages. Les anciens numéros (captures, notifications) restent valables :
        // 2 = Progrès, 3 = Réglages ; 9 = Coach
        var tab by rememberSaveable { mutableIntStateOf(when (val x = intent.getIntExtra("tab", 0)) { 2 -> 3; 3 -> 4; 9 -> 2; else -> x }) }
        // Aperçu de contrôle (captures automatiques) : adb shell am start ... --es demo <pose>
        intent.getStringExtra("demo")?.let { d ->
            val k = intent.getFloatExtra("k", -1f)
            var t by remember { mutableStateOf(0.0) }
            LaunchedEffect(d) { val t0 = System.nanoTime(); while (true) { withFrameNanos { }; t = (System.nanoTime() - t0) / 1e9 } }
            val kk = if (k >= 0) k.toDouble() else Prog.cycleK(t, listOf(1.2, .4, 1.6, .3))
            Box(Modifier.fillMaxSize().background(C.bg).systemBarsPadding()) {
                fr.pgragnic.force.gl.Body3D(fr.pgragnic.force.gl.BodyState(pose = Prog.EX[d]?.pose ?: d, ex = if (Prog.EX[d] != null) d else null, k = kk, hl = d, ghost = intent.getBooleanExtra("ghost", false),
                    mirror = intent.getBooleanExtra("mirror", false)), Modifier.fillMaxSize())
                if (!intent.getBooleanExtra("clean", false)) Text(d, color = C.ink, modifier = Modifier.padding(12.dp))
            }
            return
        }
        var ending by remember { mutableStateOf(false) }
        val cur = Live.ses
        val gear by GearStore.g.collectAsState(); val setup by setupOpen.collectAsState()
        val notifPerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(cur) { keepOn(cur != null) }
        // visite guidée au premier lancement (aide contextuelle, maquette ⑧), toujours sur l'onglet Séance
        LaunchedEffect(gear.done, setup, cur) { if (gear.done && !setup && cur == null && !Tour.seen(this@MainActivity) && Tour.step.value < 0) { kotlinx.coroutines.delay(900); Tour.start() } }
        LaunchedEffect(Tour.step.value) { if (Tour.step.value >= 0) tab = 0 }
        LaunchedEffect(Unit) {
            val pi = intent.getIntExtra("play", -1)
            if (pi >= 0 && Live.ses == null) {
                val st = Store.st.value; val P = Prog.phases(st.c, st.w, st.s, st.rest, st, Store.log.value)
                val s = fr.pgragnic.force.ui.Session(st.c, st.w, st.s, P, Live.voice(this@MainActivity)); s.start(); s.enter(pi); Live.begin(this@MainActivity, s)
            }
        }
        val signed by Store.signedIn.collectAsState(); val authReady by Store.authReady.collectAsState(); val guest by Store.guest.collectAsState()
        // captures d'écran et démonstration : pas d'écran de connexion
        val demo = intent.getBooleanExtra("seed", false) || intent.getBooleanExtra("noauth", false) || intent.hasExtra("shot")
        // démonstration : coach visible sans compte, sauf capture « locked » (carte « réservé aux comptes »)
        Store.demoCoach = intent.getBooleanExtra("seed", false) && !intent.getBooleanExtra("locked", false)
        // demande de note Google Play une fois, après la 5e séance, hors séance (Philippe, 18 novembre 23 h 18 : « Yes start » à
        // « Demander une note dans l'appli après la 5e séance »). Google décide s'il affiche la fenêtre (rien hors Play Store).
        val nLog = Store.log.collectAsState().value.size
        LaunchedEffect(nLog, cur == null) {
            val p = getSharedPreferences("force", MODE_PRIVATE)
            if (demo || cur != null || nLog < 5 || p.getBoolean("review_asked", false)) return@LaunchedEffect
            kotlinx.coroutines.delay(1500)
            runCatching {
                val m = com.google.android.play.core.review.ReviewManagerFactory.create(this@MainActivity)
                m.requestReviewFlow().addOnSuccessListener { info ->
                    m.launchReviewFlow(this@MainActivity, info).addOnCompleteListener { p.edit().putBoolean("review_asked", true).apply() }
                }
            }
        }
        Box(Modifier.fillMaxSize().background(C.bg).systemBarsPadding()) {
            when {
                !demo && !signed && !guest && cur == null -> if (authReady) fr.pgragnic.force.ui.WelcomeScreen() else Box(Modifier.fillMaxSize())
                cur == null && (!gear.done || setup) -> SetupScreen { setupOpen.value = false }
                cur != null && (ending || cur.done) -> EndScreen(cur) { Live.end(this@MainActivity); ending = false }
                cur != null -> {
                    // quitter une séance en cours : confirmation (Philippe, 25 novembre 13 h 52 : « si l'utilisateur quite la séance, il faut faire confirmer par l'utilisateur pour éviter que ce soit une erreur »)
                    var confirmQuit by remember(cur) { mutableStateOf(false) }
                    var wasRun by remember(cur) { mutableStateOf(true) }
                    val askQuit = { wasRun = cur.run; if (cur.run) cur.toggle(); confirmQuit = true }
                    val keepOn = { confirmQuit = false; if (wasRun && !cur.run) cur.toggle() }
                    BackHandler { if (confirmQuit) keepOn() else askQuit() }
                    PlayerScreen(cur, onClose = { askQuit() }, onEnd = { ending = true })
                    if (confirmQuit) AlertDialog(
                        onDismissRequest = { keepOn() },
                        title = { Text(t("Quitter la séance ?", "Quit the workout?")) },
                        text = { Text(t("Sans enregistrement, la séance en cours est perdue.", "Unless you save it, the workout in progress is lost.")) },
                        confirmButton = { TextButton(onClick = { confirmQuit = false; Live.end(this@MainActivity) }) { Text(t("Quitter", "Quit")) } },
                        dismissButton = { Row {
                            if (cur.sets.values.any { it.isNotEmpty() }) TextButton(onClick = { confirmQuit = false; cur.partial = true; ending = true }) { Text(t("Enregistrer ce qui est fait", "Save what I did")) }
                            TextButton(onClick = { keepOn() }) { Text(t("Continuer la séance", "Keep going")) } } })
                }
                else -> Scaffold(containerColor = C.bg, bottomBar = {
                    // barre d'onglets flottante en capsule blanche (style épuré, Philippe 14 novembre : « Partons sur le bleu vif »)
                    // 4 onglets : Suivi et Activité réunis dans Progrès, Infos devient Réglages (maquettes validées par Philippe : « Ok pour ces écrans ! »)
                    // 5e onglet Coach au milieu (Philippe, 15 novembre : « 5e onglet au milieu »)
                    val items = listOf<Pair<String, ImageVector>>(t("Séance", "Workout") to Icons.Default.FitnessCenter, t("Corps", "Body") to Icons.Default.Accessibility,
                        t("Coach", "Coach") to Icons.Default.AutoAwesome, t("Progrès", "Progress") to Icons.Default.BarChart, t("Réglages", "Settings") to Icons.Default.PersonOutline)
                    val cap = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
                    Box(Modifier.fillMaxWidth().background(C.bg).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)) {
                        Row(Modifier.fillMaxWidth().height(64.dp).clip(cap).background(C.s.surfaceContainer).border(1.dp, C.rim, cap).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            items.forEachIndexed { i, (lab, ic) ->
                                val on = tab == i
                                Column(Modifier.weight(1f).fillMaxHeight().tourTarget(if (i == 1) "corps" else null).clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
                                    .background(if (on) C.s.surfaceContainerLow else androidx.compose.ui.graphics.Color.Transparent).clickable { tab = i },
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(ic, lab, tint = if (on) C.ink else C.muted, modifier = Modifier.size(22.dp))
                                    Text(lab, color = if (on) C.ink else C.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }) { pad ->
                    Box(Modifier.padding(pad)) {
                        when (tab) {
                            0 -> SeanceScreen(onStart = {
                                val st = Store.st.value
                                val P = Prog.phases(st.c, st.w, st.s, st.rest, st, Store.log.value)
                                if (android.os.Build.VERSION.SDK_INT >= 33) runCatching { notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                                Live.begin(this@MainActivity, fr.pgragnic.force.ui.Session(st.c, st.w, st.s, P, Live.voice(this@MainActivity)))
                            })
                            1 -> CorpsScreen()
                            2 -> fr.pgragnic.force.ui.CoachTab(onStart = {
                                val st = Store.st.value
                                val P = Prog.phases(st.c, st.w, st.s, st.rest, st, Store.log.value)
                                Live.begin(this@MainActivity, fr.pgragnic.force.ui.Session(st.c, st.w, st.s, P, Live.voice(this@MainActivity)))
                            })
                            3 -> ProgresScreen()
                            else -> ReglagesScreen()
                        }
                    }
                }
            }
        }
    }
}
