package fr.pgragnic.force

import java.time.LocalDate

/** Données fictives pour les captures d'écran automatiques (jamais envoyées en ligne : pas de compte connecté). */
object Demo {
    /** étape du premier lancement ouverte directement (captures) */
    var setupStep = 0
    /** muscle ouvert directement dans l'onglet Corps (captures) */
    var muscle: String? = null
    /** sous-page ouverte directement (captures) : Réglages "prog", "rappels", "compte"… ou sous-onglet de Progrès "0".."2" */
    var sub: String? = null
    /** Matériel fictif : "kb" = kettlebell, élastiques, chaise, banc ; "bw" = poids du corps, chaise et mur ; "new" = premier lancement. */
    fun gear(code: String) {
        GearStore.g.value = when (code) {
            "kb" -> Gear(items = setOf("kb", "band", "mini", "chair", "wall", "mat", "bench"), kbs = listOf(8.0, 12.0, 16.0), level = 1, bw = 75.0, obj = "muscle", zones = setOf("bras"), done = true)
            "gym" -> Gear(items = setOf("barbell", "pullup", "dips", "incbench", "bench", "abwheel", "rope", "roller", "mat", "db"), dbMode = "fixed", fixed = listOf(4.0, 6.0, 8.0, 10.0, 12.0), level = 2, bw = 80.0, obj = "muscle", done = true)
            "acc" -> Gear(items = setOf("towel", "medball", "swissball", "bosu", "pvc", "rollball", "mat"), level = 0, bw = 70.0, obj = "forme", done = true)
            "bw" -> Gear(items = setOf("chair", "wall", "mat"), level = 0, bw = 80.0, obj = "poids", limits = setOf("genou"), done = true)
            "new" -> Gear(done = false)
            else -> Gear(done = true)
        }
    }

    /** captures : une séance faite il y a peu, pour voir l'alerte de récupération */
    var tired = false
    /** captures : deux séances A de suite, pour voir l'état « surchargé » */
    var over = false

    fun seed() {
        if (!GearStore.g.value.done) GearStore.g.value = Gear(done = true)
        val now = System.currentTimeMillis(); val day = 86_400_000L
        val plan = listOf(20.0 to "A", 18.0 to "B", 16.0 to "C", 15.0 to "D", 13.0 to "A", 11.0 to "B", 9.0 to "C", 8.0 to "D", 6.0 to "A", 4.0 to "B", 2.0 to "C", 1.0 to "D") +
            (if (tired) listOf(0.1 to "A") else emptyList()) + (if (over) listOf(0.3 to "A", 0.2 to "A") else emptyList())   // captures : séance A faite il y a 2 h 30 (alerte de récupération)
        val log = plan.mapIndexed { i, (ago, s) ->
            val w = i / 4; val ids = Prog.sess(1)[s]!!.ex
            Entry(d = now - (ago * day).toLong(), c = 1, w = w, s = s, f = "Juste bien", rest = 30, bw = 79.5 - i * 0.3,
                ex = ids.map { id -> val kg = if (Prog.hasDb(id)) 5.0 + w else 0.0
                    ExRec(id, Prog.EX[id]!!.n, kg, 10, "reps", List(3) { SetRec(10, 2) }, 10, if (i % 5 == 4) "Difficile" else "Juste bien") })
        }
        Store.log.value = log
        val t = LocalDate.now()
        Store.days.value = (13 downTo 0).map { i ->
            val d = t.minusDays(i.toLong())
            if (i == 3) DayRec(d.toString(), 14200, "Randonnée", 6.0, 450, 180) else DayRec(d.toString(), 5000 + (i * 1371) % 7000, src = "hc")
        }
    }
}
