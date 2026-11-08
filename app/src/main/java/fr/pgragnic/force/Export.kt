package fr.pgragnic.force

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.*

/**
 * Export des données de l'utilisateur (droit à la portabilité du RGPD) : tout en JSON, ou les séances en CSV pour un tableur.
 * Le fichier est écrit là où l'utilisateur l'a choisi (sélecteur de fichiers d'Android).
 */
object Export {
    private val pretty = Json { prettyPrint = true; encodeDefaults = false; explicitNulls = false }

    fun json(): String = pretty.encodeToString(JsonObject.serializer(), buildJsonObject {
        put("appli", "UpRep ${BuildConfig.VERSION_NAME}")
        put("exporte_le", java.time.OffsetDateTime.now().toString())
        Store.user.value?.let { put("compte", it) }
        put("profil_et_materiel", Store.json.encodeToJsonElement(Gear.serializer(), GearStore.g.value))
        put("seances", Store.json.encodeToJsonElement(Store.log.value.map { it.copy(_id = null) }))
        put("journees", Store.json.encodeToJsonElement(Store.days.value))
    })

    /** Une ligne par exercice de chaque séance ; séparateur point-virgule (Excel en français). */
    fun csv(): String {
        val f = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val head = listOf("date", "cycle", "semaine", "seance", "exercice", "charge_kg", "objectif", "fait", "series", "ressenti", "poids_corps_kg").joinToString(";")
        val rows = Store.log.value.sortedBy { it.d }.flatMap { e ->
            val d = java.time.Instant.ofEpochMilli(e.d).atZone(java.time.ZoneId.systemDefault()).format(f)
            e.ex.map { x ->
                listOf(d, e.c.toString(), (e.w + 1).toString(), e.s, q(tr(x.n)), Prog.fk(x.kg), x.target.toString(), x.done.toString(),
                    q(x.sets.joinToString(" / ") { it.r.toString() }), q(tr(x.f)), e.bw?.let { Prog.fk(it) } ?: "").joinToString(";")
            }
        }
        return (listOf(head) + rows).joinToString("\r\n")
    }

    fun write(ctx: Context, uri: Uri, text: String): Boolean = runCatching {
        ctx.contentResolver.openOutputStream(uri)!!.use { it.write(("﻿" + text).toByteArray(Charsets.UTF_8)) }
    }.isSuccess
}
