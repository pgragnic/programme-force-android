package fr.pgragnic.force

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.ZoneId

/** Lecture des pas dans Health Connect (Google Fit, Samsung Santé, montre… y écrivent). */
object Health {
    val PERMS = setOf(HealthPermission.getReadPermission(StepsRecord::class))
    val status = MutableStateFlow("")
    val granted = MutableStateFlow(false)

    /** Health Connect optionnel (Philippe, 14 novembre 21 h 44 : « Il faudrait aussi mettre health connect en optionnel dans les paramètres et en choix optionnel au démarrage de l'application »).
     *  Désactivé par défaut ; reste actif pour qui avait déjà donné l'accès. Les pas déjà importés sont gardés quand on le coupe. */
    val on = MutableStateFlow(false)
    fun load(ctx: Context) { val p = ctx.getSharedPreferences("force", Context.MODE_PRIVATE); on.value = p.getBoolean("hc_on", p.getBoolean("hc_was", false)) }
    fun setOn(ctx: Context, v: Boolean) { on.value = v; ctx.getSharedPreferences("force", Context.MODE_PRIVATE).edit().putBoolean("hc_on", v).apply() }
    fun available(ctx: Context) = HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE
    fun needsUpdate(ctx: Context) = HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    suspend fun check(ctx: Context): Boolean {
        if (!available(ctx)) { granted.value = false; return false }
        val ok = runCatching { HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions().containsAll(PERMS) }.getOrDefault(false)
        granted.value = ok
        // anciens utilisateurs qui avaient déjà autorisé l'accès : l'option reste allumée
        val p = ctx.getSharedPreferences("force", Context.MODE_PRIVATE)
        if (ok && !p.contains("hc_on") && !p.getBoolean("hc_was", false)) { p.edit().putBoolean("hc_was", true).apply(); if (!p.contains("hc_on")) on.value = true }
        return ok && on.value
    }

    /** Importe les pas des 14 derniers jours dans les journées (garde l'activité saisie à la main). */
    suspend fun importSteps(ctx: Context) {
        if (!check(ctx)) return
        try {
            val hc = HealthConnectClient.getOrCreate(ctx); val z = ZoneId.systemDefault(); val today = LocalDate.now()
            val byDate = Store.days.value.associateBy { it.date }
            val out = ArrayList<DayRec>()
            for (i in 13 downTo 0) {
                val d = today.minusDays(i.toLong())
                val r = hc.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(d.atStartOfDay(z).toInstant(), d.plusDays(1).atStartOfDay(z).toInstant())))
                val n = (r[StepsRecord.COUNT_TOTAL] ?: 0L).toInt(); if (n <= 0) continue
                val key = d.toString(); val old = byDate[key]
                if (old?.steps == n) continue
                if (old?.src == "main" && (old.steps ?: 0) > n) continue
                out += (old ?: DayRec(key)).copy(steps = n, src = "hc")
            }
            Store.saveDays(out)
            status.value = if (out.isEmpty()) "Pas à jour (Health Connect)" else t("Pas importés depuis Health Connect : ${out.size} jour(s) mis à jour", "Steps imported from Health Connect: ${out.size} day(s) updated")
        } catch (t: Throwable) { status.value = t("Lecture Health Connect impossible : ${t.message ?: "erreur"}", "Cannot read Health Connect: ${t.message ?: "error"}") }
    }
}
