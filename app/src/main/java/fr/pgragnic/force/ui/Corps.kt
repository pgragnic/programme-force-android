package fr.pgragnic.force.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pgragnic.force.*
import fr.pgragnic.force.gl.Body3D
import fr.pgragnic.force.gl.BodyState
import kotlin.math.PI
import kotlin.math.roundToInt

private fun Int.col() = Color(0xFF000000 or this.toLong())

/** Onglet Corps : récupération des muscles, de face et de dos. */
@Composable
fun CorpsScreen() {
    val log by Store.log.collectAsState(); val days by Store.days.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(10 * 60_000L); tick++ } }
    val MU = remember(log, days, tick) { Recovery.muscles(log, days) }
    val colors = remember(MU) { Recovery.MUS.associateWith { Recovery.recCol(MU.R[it]!!) } }
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Muscles", fontSize = 28.sp, fontWeight = FontWeight.Black, color = C.ink)
            Spacer(Modifier.weight(1f)); Text("Récupération maintenant", color = C.muted, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth().height(330.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Face" to 0f, "Dos" to PI.toFloat()).forEach { (t, az) ->
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(C.stage)) {
                    Body3D(BodyState(pose = "calf", k = 0.0, nodb = true, colors = colors, az = az, map = true), Modifier.fillMaxWidth().weight(1f))
                    Text(t, color = C.muted, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(6.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Prêt" to C.effort, "En récupération" to C.warm, "Fatigué" to C.danger).forEach { (t, c) ->
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(CircleShape).background(c)); Text(" $t", color = C.muted, fontSize = 12.sp) }
            }
        }
        Text("Fais glisser pour tourner, touche deux fois pour revenir.", color = C.muted, fontSize = 12.sp)
        Panel {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Text("Détail par muscle · séries des 7 derniers jours", color = C.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (open) "▲" else "▼", color = C.muted)
            }
            if (open) Recovery.MUS.forEach { m ->
                val r = MU.R[m]!!; val c = if (r >= 90) C.effort else if (r >= 60) C.warm else C.danger; val h = MU.ready[m] ?: 120
                val stt = if (r >= 90) "Prêt" else "Prêt dans ~" + if (h >= 24) "${h / 24} j ${h % 24} h" else "$h h"
                val v = MU.V[m]!!; val sh = if (MU.tot > 0) (100 * v / MU.tot).roundToInt() else 0
                Column(Modifier.padding(top = 10.dp)) {
                    Row { Text(m, color = C.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("$r %", color = c, fontWeight = FontWeight.Bold) }
                    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.line)) {
                        Box(Modifier.fillMaxWidth(r / 100f).fillMaxHeight().background(c))
                    }
                    Row {
                        Text(stt, color = C.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(if (v > 0) "${Prog.fk(v)} séries · $sh % du volume" else if (r < 100) "Fatigue due à la marche" else "Pas travaillé cette semaine", color = C.muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
