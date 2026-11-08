package fr.pgragnic.force

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import fr.pgragnic.force.ui.PLAB
import fr.pgragnic.force.ui.Session
import kotlinx.coroutines.*
import kotlin.math.ceil
import kotlin.math.max

/** Séance en cours, partagée entre l'écran et la notification (survit à l'écran éteint). */
object Live {
    var ses by mutableStateOf<Session?>(null)
    private var v: Voice? = null
    fun voice(ctx: Context) = v ?: Voice(ctx.applicationContext).also { v = it }

    fun begin(ctx: Context, s: Session) {
        ses = s
        val i = Intent(ctx, SessionService::class.java)
        runCatching { if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i) }
    }
    fun end(ctx: Context) { ses = null; runCatching { ctx.stopService(Intent(ctx, SessionService::class.java)) } }
}

/** Boutons de la notification (écran verrouillé compris). */
class SessionAction : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val s = Live.ses ?: return
        when (i.action) {
            "fr.pgragnic.force.TOGGLE" -> s.toggle()
            "fr.pgragnic.force.NEXT" -> s.next()
            "fr.pgragnic.force.PREV" -> s.prev()
        }
        SessionService.refresh(ctx)
    }
}

/** Service au premier plan : fait avancer la séance et affiche la notification avec le minuteur. */
class SessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wl: PowerManager.WakeLock? = null
    private var shown = ""

    companion object {
        const val CH = "seance"; const val ID = 42
        private var inst: SessionService? = null
        fun refresh(ctx: Context) { inst?.update(true) }
        fun channels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH, "Séance en cours", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Minuteur et boutons pendant la séance"; lockscreenVisibility = Notification.VISIBILITY_PUBLIC; setShowBadge(false) })
            nm.createNotificationChannel(NotificationChannel(Reminder.CH, "Rappel de séance", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Rappel quand une séance est prévue" })
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate(); inst = this; channels(this)
        wl = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "force:seance").apply { setReferenceCounted(false); acquire(3 * 3600_000L) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = build()
        ServiceCompat.startForeground(this, ID, n, if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        scope.coroutineContext.cancelChildren()
        scope.launch {
            while (isActive) {
                val s = Live.ses ?: break
                if (s.started) s.tick()
                update(false)
                if (s.done) break
                delay(200)
            }
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun pi(a: String) = PendingIntent.getBroadcast(this, a.hashCode(), Intent(a).setPackage(packageName).setClass(this, SessionAction::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun build(): Notification {
        val s = Live.ses
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(this, CH).setSmallIcon(R.drawable.ic_stat).setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setCategory(NotificationCompat.CATEGORY_WORKOUT).setContentIntent(open).setSilent(true)
        if (s == null) return b.setContentTitle("Séance").build()
        val p = s.P[s.pi]
        val rem = max(0.0, p.dur - s.pt)
        b.setContentTitle(tr(PLAB[p.k] ?: "") + " · " + tr(p.n)).setContentText(if (s.run) tr(p.blk) else tr("En pause · ") + tr(p.blk))
        b.setSubText(t("Séance ${s.s} · S${s.w + 1}", "Workout ${s.s} · W${s.w + 1}"))
        if (s.run && p.dur > 0) b.setUsesChronometer(true).setChronometerCountDown(true).setWhen(System.currentTimeMillis() + (rem * 1000).toLong()).setShowWhen(true)
        else b.setShowWhen(false).setContentText(t("En pause · ${ceil(rem).toInt()} s restantes", "Paused · ${ceil(rem).toInt()} s left"))
        b.addAction(0, tr("Précédent"), pi("fr.pgragnic.force.PREV"))
        b.addAction(0, tr(if (s.run) "Pause" else "Reprendre"), pi("fr.pgragnic.force.TOGGLE"))
        b.addAction(0, tr("Suivant"), pi("fr.pgragnic.force.NEXT"))
        b.setProgress(1000, ((s.elapsed / max(1.0, s.totalDur)) * 1000).toInt().coerceIn(0, 1000), false)
        return b.build()
    }

    fun update(force: Boolean) {
        val s = Live.ses ?: return
        val key = "${s.pi}/${s.run}/${(s.elapsed / 30).toInt()}"
        if (!force && key == shown) return
        shown = key
        runCatching { getSystemService(NotificationManager::class.java).notify(ID, build()) }
    }

    override fun onDestroy() { inst = null; scope.cancel(); runCatching { wl?.release() }; super.onDestroy() }
}
