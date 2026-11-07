package fr.pgragnic.force

import java.time.LocalDate

/** Données fictives pour les captures d'écran automatiques (jamais envoyées en ligne : pas de compte connecté). */
object Demo {
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

    fun seed() {
        if (!GearStore.g.value.done) GearStore.g.value = Gear(done = true)
        val now = System.currentTimeMillis(); val day = 86_400_000L
        val plan = listOf(20 to "A", 18 to "B", 16 to "C", 15 to "D", 13 to "A", 11 to "B", 9 to "C", 8 to "D", 6 to "A", 4 to "B", 2 to "C", 1 to "D")
        val log = plan.mapIndexed { i, (ago, s) ->
            val w = i / 4; val ids = Prog.sess(1)[s]!!.ex
            Entry(d = now - ago * day, c = 1, w = w, s = s, f = "Juste bien", rest = 30, bw = 79.5 - i * 0.3,
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
