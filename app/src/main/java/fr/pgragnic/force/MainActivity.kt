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
import fr.pgragnic.force.ui.*

class MainActivity : ComponentActivity() {
    private lateinit var voice: Voice

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Prog.load(this); Store.init(this)
        voice = Voice(this)
        Store.handleLink(intent)
        runCatching {
            getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { Store.autoSync() }
            })
        }
        setContent { ForceTheme { App() } }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); Store.handleLink(intent) }

    override fun onResume() { super.onResume(); Store.autoSync() }

    override fun onDestroy() { voice.close(); super.onDestroy() }

    private fun keepOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @Composable
    fun App() {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var ses by remember { mutableStateOf<fr.pgragnic.force.ui.Session?>(null) }
        var ending by remember { mutableStateOf(false) }
        val cur = ses
        Box(Modifier.fillMaxSize().background(C.bg).systemBarsPadding()) {
            when {
                cur != null && ending -> EndScreen(cur) { ses = null; ending = false; keepOn(false) }
                cur != null -> {
                    BackHandler { ses = null; keepOn(false) }
                    PlayerScreen(cur, onClose = { ses = null; keepOn(false) }, onEnd = { ending = true })
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
                                ses = fr.pgragnic.force.ui.Session(st.c, st.w, st.s, P, voice); keepOn(true)
                            })
                            1 -> CorpsScreen()
                            2 -> Soon("Activité", "Les pas, les activités et les calories arrivent à l'étape 3, avec l'import automatique des pas via Health Connect.")
                            3 -> SuiviScreen()
                            else -> InfosScreen()
                        }
                    }
                }
            }
        }
    }
}
