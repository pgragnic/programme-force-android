package fr.pgragnic.force

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

/** Chrono « sac sur le dos » (Philippe, 12 novembre : « Concernant la marche lestée c'est une activité qui peut être intermittente sur une
 *  journée, comment peut-on gérer ça ? », puis « 1,2 et 3 », point 2) : Démarrer quand on met le sac, Arrêter quand on l'enlève ; la sortie
 *  s'ajoute toute seule à la journée. Le chrono survit à la fermeture de l'appli (heure de départ gardée) et s'affiche en notification,
 *  avec un bouton Arrêter. */
object Ruck {
    data class Run(val start: Long, val act: String, val pack: Double)
    val run = MutableStateFlow<Run?>(null)
    private const val CH = "chrono_sac"; private const val NID = 44
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("force", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        val p = prefs(ctx); val t = p.getLong("ruck_t", 0L)
        run.value = if (t > 0) Run(t, p.getString("ruck_a", "Marche lestée")!!, p.getFloat("ruck_kg", 0f).toDouble()) else null
    }

    fun start(ctx: Context, act: String, pack: Double) {
        val r = Run(System.currentTimeMillis(), act, pack); run.value = r
        prefs(ctx).edit().putLong("ruck_t", r.start).putString("ruck_a", act).putFloat("ruck_kg", pack.toFloat()).apply()
        notify(ctx, r)
    }

    /** Arrête le chrono ; la sortie (au moins 1 min) est ajoutée au jour du départ. Renvoie la sortie enregistrée. */
    fun stop(ctx: Context, save: Boolean = true): Out? {
        load(ctx); val r = run.value ?: return null
        run.value = null; prefs(ctx).edit().remove("ruck_t").remove("ruck_a").remove("ruck_kg").apply()
        ctx.getSystemService(NotificationManager::class.java).cancel(NID)
        if (!save) return null
        val z = Instant.ofEpochMilli(r.start).atZone(ZoneId.systemDefault())
        val o = Out(z.format(DateTimeFormatter.ofPattern("HH:mm")), r.act, r.pack.takeIf { it > 0 }, max(1, ((System.currentTimeMillis() - r.start) / 60_000.0).roundToInt()))
        Store.init(ctx)
        val key = z.toLocalDate().toString(); val old = Store.days.value.firstOrNull { it.date == key } ?: DayRec(key, src = "main")
        Store.saveDay(old.withOuts(old.sorties() + o))
        return o
    }

    private fun notify(ctx: Context, r: Run) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Chrono sac sur le dos", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Temps passé avec le sac pendant une marche lestée"; lockscreenVisibility = Notification.VISIBILITY_PUBLIC; setShowBadge(false) })
        val open = PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java).putExtra("tab", 2).putExtra("sub", "0"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopI = PendingIntent.getBroadcast(ctx, 4, Intent(ctx, RuckReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val kg = if (r.pack > 0) " · " + Prog.fk(r.pack) + " kg" else ""
        nm.notify(NID, NotificationCompat.Builder(ctx, CH).setSmallIcon(R.drawable.ic_stat).setOngoing(true).setOnlyAlertOnce(true)
            .setUsesChronometer(true).setWhen(r.start).setShowWhen(true).setContentIntent(open)
            .setContentTitle(tr("Sac sur le dos")).setContentText(tr(r.act) + kg)
            .addAction(0, tr("Arrêter et enregistrer"), stopI).build())
    }
}

/** Bouton « Arrêter et enregistrer » de la notification du chrono. */
class RuckReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) { runCatching { L.load(ctx); Prog.load(ctx) }; Ruck.stop(ctx) }
}
