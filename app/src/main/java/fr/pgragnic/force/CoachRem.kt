package fr.pgragnic.force

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.Calendar

/** Rappels du coach (onglet Coach → Rappels) : debrief 30 min après une séance, suggestion du matin à 8 h. Facultatifs. */
object CoachRem {
    private fun pi(ctx: Context, kind: String, code: Int) = PendingIntent.getBroadcast(ctx, code,
        Intent(ctx, CoachRemReceiver::class.java).putExtra("kind", kind), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** matin : chaque jour à 8 h si l'option est active */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java); val p = pi(ctx, "matin", 71)
        am.cancel(p)
        if (!CoachData.d.remMorning) return
        val k = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); if (timeInMillis < System.currentTimeMillis() + 30_000) add(Calendar.DAY_OF_YEAR, 1) }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, k.timeInMillis, p)
    }
    /** appelé quand une séance est enregistrée */
    fun afterSession(ctx: Context) {
        if (!CoachData.d.remDebrief) return
        ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 30 * 60_000L, pi(ctx, "debrief", 72))
    }

    fun fire(ctx: Context, kind: String) {
        runCatching { CoachData.load(ctx) }
        if (kind == "matin") schedule(ctx)
        val p = ctx.getSharedPreferences("force", Context.MODE_PRIVATE)
        if (!p.getBoolean("coach_ok", false)) return
        if (kind == "matin" && !CoachData.d.remMorning) return
        if (kind == "debrief" && !CoachData.d.remDebrief) return
        if (kind == "debrief") {
            val last = runCatching { Prog.load(ctx); Store.init(ctx); Store.log.value.maxByOrNull { it.d }?.d }.getOrNull() ?: return
            if (p.getLong("coachdebrief", 0L) == last) return // déjà débriefée
        }
        SessionService.channels(ctx)
        val open = PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java).putExtra("tab", 9), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val (ti, tx) = if (kind == "debrief") t("Comment s'est passée ta séance ?", "How did your workout go?") to t("Dis-le au coach : il ajuste tes prochaines charges.", "Tell the coach: it adjusts your next loads.")
            else t("Ta suggestion du jour", "Your daily suggestion") to t("Le coach a un conseil pour aujourd'hui.", "The coach has a tip for today.")
        ctx.getSystemService(NotificationManager::class.java).notify(if (kind == "debrief") 45 else 44,
            NotificationCompat.Builder(ctx, Reminder.CH).setSmallIcon(R.drawable.ic_stat).setAutoCancel(true).setContentIntent(open).setContentTitle(ti).setContentText(tx).build())
    }
}

class CoachRemReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) { CoachRem.fire(ctx, i.getStringExtra("kind") ?: "matin") }
}
