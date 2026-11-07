package fr.pgragnic.force

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.Calendar

/** Rappel de séance : les jours choisis, à l'heure choisie, sauf si la séance du jour est déjà faite. */
object Reminder {
    const val CH = "rappel"
    data class Cfg(val on: Boolean = false, val h: Int = 18, val m: Int = 0, val days: Int = 0b0011011) // bit 0 = lundi

    fun get(ctx: Context): Cfg {
        val p = ctx.getSharedPreferences("force", Context.MODE_PRIVATE)
        return Cfg(p.getBoolean("rem_on", false), p.getInt("rem_h", 18), p.getInt("rem_m", 0), p.getInt("rem_days", 0b0011011))
    }

    fun set(ctx: Context, c: Cfg) {
        ctx.getSharedPreferences("force", Context.MODE_PRIVATE).edit().putBoolean("rem_on", c.on).putInt("rem_h", c.h).putInt("rem_m", c.m).putInt("rem_days", c.days).apply()
        schedule(ctx)
    }

    private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Jour de la semaine façon lundi = 0. */
    fun dow(c: Calendar) = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7

    fun next(c: Cfg, from: Long = System.currentTimeMillis()): Long? {
        if (!c.on || c.days == 0) return null
        val k = Calendar.getInstance().apply { timeInMillis = from; set(Calendar.HOUR_OF_DAY, c.h); set(Calendar.MINUTE, c.m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        repeat(8) { if (k.timeInMillis > from + 30_000 && (c.days shr dow(k)) and 1 == 1) return k.timeInMillis; k.add(Calendar.DAY_OF_YEAR, 1) }
        return null
    }

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pi(ctx))
        val t = next(get(ctx)) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(ctx))
    }

    fun fire(ctx: Context) {
        schedule(ctx)
        if (!get(ctx).on) return
        runCatching { Prog.load(ctx); Store.init(ctx) }
        val t0 = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        if (Store.log.value.any { it.d >= t0 }) return // déjà faite aujourd'hui
        val st = Store.st.value
        val nm = ctx.getSystemService(NotificationManager::class.java)
        SessionService.channels(ctx)
        val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val title = Prog.sess(st.c)[st.s]?.t ?: ""
        nm.notify(43, NotificationCompat.Builder(ctx, CH).setSmallIcon(R.drawable.ic_stat).setAutoCancel(true).setContentIntent(open)
            .setContentTitle("C'est l'heure de ta séance").setContentText("Séance ${st.s} · $title · cycle ${st.c}, semaine ${st.w + 1}").build())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED || i.action == "android.intent.action.MY_PACKAGE_REPLACED") Reminder.schedule(ctx) else Reminder.fire(ctx)
    }
}
