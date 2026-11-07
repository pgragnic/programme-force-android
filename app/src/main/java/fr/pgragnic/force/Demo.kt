package fr.pgragnic.force

import java.time.LocalDate

/** Données fictives pour les captures d'écran automatiques (jamais envoyées en ligne : pas de compte connecté). */
object Demo {
    fun seed() {
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
