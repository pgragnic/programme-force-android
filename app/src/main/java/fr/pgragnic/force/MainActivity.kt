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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import fr.pgragnic.force.ui.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Prog.load(this); Store.init(this)
        C.load(this); SessionService.channels(this); Reminder.schedule(this)
        Store.handleLink(intent)
        runCatching {
            getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { Store.autoSync() }
            })
        }
        setContent {
            val dk = C.dark
            LaunchedEffect(dk) { androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).run { isAppearanceLightStatusBars = !dk; isAppearanceLightNavigationBars = !dk } }
            ForceTheme { App() }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); Store.handleLink(intent) }

    override fun onResume() { super.onResume(); Store.autoSync(); lifecycleScope.launch { if (Health.check(this@MainActivity)) Health.importSteps(this@MainActivity) } }


    private fun keepOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @Composable
    fun App() {
        var tab by rememberSaveable { mutableIntStateOf(intent.getIntExtra("tab", 0)) }
        // Aperçu de contrôle (captures automatiques) : adb shell am start ... --es demo <pose>
        intent.getStringExtra("demo")?.let { d ->
            val k = intent.getFloatExtra("k", -1f)
            var t by remember { mutableStateOf(0.0) }
            LaunchedEffect(d) { val t0 = System.nanoTime(); while (true) { withFrameNanos { }; t = (System.nanoTime() - t0) / 1e9 } }
            val kk = if (k >= 0) k.toDouble() else Prog.cycleK(t, listOf(1.2, .4, 1.6, .3))
            Box(Modifier.fillMaxSize().background(C.bg).systemBarsPadding()) {
                fr.pgragnic.force.gl.Body3D(fr.pgragnic.force.gl.BodyState(pose = d, k = kk, hl = d, ghost = intent.getBooleanExtra("ghost", false),
                    mirror = intent.getBooleanExtra("mirror", false)), Modifier.fillMaxSize())
                Text(d, color = C.ink, modifier = Modifier.padding(12.dp))
            }
            return
        }
        var ending by remember { mutableStateOf(false) }
        val cur = Live.ses
        val notifPerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(cur) { keepOn(cur != null) }
        Box(Modifier.fillMaxSize().background(C.bg).systemBarsPadding()) {
            when {
                cur != null && (ending || cur.done) -> EndScreen(cur) { Live.end(this@MainActivity); ending = false }
                cur != null -> {
                    BackHandler { Live.end(this@MainActivity) }
                    PlayerScreen(cur, onClose = { Live.end(this@MainActivity) }, onEnd = { ending = true })
                }
                else -> Scaffold(containerColor = C.bg, bottomBar = {
                    NavigationBar(containerColor = C.surface) {
                        val items = listOf<Pair<String, ImageVector>>("Séance" to Icons.Default.FitnessCenter, "Corps" to Icons.Default.Accessibility,
                            "Activité" to Icons.Default.BarChart, "Suivi" to Icons.Default.Article, "Infos" to Icons.Default.Info)
                        items.forEachIndexed { i, (t, ic) ->
                            NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, t) }, label = { Text(t) },
                                colors = NavigationBarItemDefaults.colors(selectedIconColor = C.effort, selectedTextColor = C.effort, indicatorColor = C.bg, unselectedIconColor = C.muted, unselectedTextColor = C.muted))
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
                            2 -> ActiviteScreen()
                            3 -> SuiviScreen()
                            else -> InfosScreen()
                        }
                    }
                }
            }
        }
    }
}
