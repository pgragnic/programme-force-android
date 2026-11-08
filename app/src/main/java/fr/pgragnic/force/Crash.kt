package fr.pgragnic.force

import android.app.Application
import android.content.Context
import android.os.Build
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class CrashRow(val appli: String, val android: String, val appareil: String, val quand: Long, val trace: String)

/**
 * Rapports de plantage automatiques, sans service extérieur : au plantage, la trace est écrite sur le téléphone,
 * puis envoyée dans la table Supabase « plantages » au lancement suivant (script supabase/pro.sql).
 * Aucune donnée personnelle : version de l'appli, d'Android, modèle du téléphone et trace du programme.
 */
object Crash {
    private const val DIR = "plantages"
    private const val MAX = 5

    fun install(ctx: Context) {
        val dir = File(ctx.filesDir, DIR)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { th, e ->
            runCatching {
                dir.mkdirs()
                if ((dir.list()?.size ?: 0) < MAX) File(dir, "${System.currentTimeMillis()}.txt").writeText("[${th.name}] " + e.stackTraceToString().take(12000))
            }
            prev?.uncaughtException(th, e)
        }
    }

    /** Envoie les rapports en attente (possible sans compte), puis les efface. */
    fun sendPending(ctx: Context) {
        val dir = File(ctx.filesDir, DIR)
        val files = dir.listFiles()?.sortedBy { it.name } ?: return
        if (files.isEmpty()) return
        val c = Store.sb ?: return
        CoroutineScope(Dispatchers.IO).launch {
            files.forEach { f ->
                runCatching {
                    c.from("plantages").insert(CrashRow("UpRep ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "${Build.MANUFACTURER} ${Build.MODEL}".take(100),
                        f.name.removeSuffix(".txt").toLongOrNull() ?: 0L, f.readText()))
                    f.delete()
                }
            }
        }
    }
}

/** Point d'entrée de l'appli : installe la capture des plantages avant tout le reste (écrans, minuteur, rappels). */
class UpRepApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Crash.install(this)
    }
}
