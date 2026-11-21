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
    /** captures : jambes fatiguées (séance B de jambes, difficile, faite hier soir), pour montrer la séance adaptée à la récupération */
    var legs = false

    /** Données de démonstration de l'onglet Coach (captures) : une discussion, deux décisions, un objectif, une préférence, un bilan. */
    fun seedCoach(ctx: android.content.Context) {
        val now = System.currentTimeMillis(); val h = 3_600_000L; val today = java.time.LocalDate.now()
        val p1 = CoachPlan(remplacements = listOf(CoachRep("press", "pushup", t("Épaule ménagée : pas de bras au-dessus de la tête", "Shoulder spared: no arms overhead"))), gene = CoachGene("epaule", 2),
            explications = listOf(t("Épaule ménagée 2 jours", "Shoulder spared for 2 days")))
        val p2 = CoachPlan(charges = listOf(CoachLoad("squat", 1, t("tu as trouvé ça trop facile", "you found it too easy"))), explications = listOf(t("Goblet squat +1 cran", "Goblet squat +1 step")))
        CoachState.apply(ctx, p1)
        CoachData.update(false) { it.copy(
            msgs = listOf(CMsg(now - 26 * h, false, t("Je viens de finir ma séance.", "I just finished my workout.")), CMsg(now - 26 * h + 1000, true, t("Bravo ! C'était comment ?", "Well done! How was it?")), CMsg(now - 26 * h + 2000, false, t("Trop facile", "Too easy")),
                CMsg(now - 26 * h + 3000, true, t("On monte le goblet squat d'un cran la prochaine fois : tu as fait toutes tes répétitions avec 3 en réserve.", "We'll raise the goblet squat one step next time: you did all your reps with 3 in reserve."), p2),
                CMsg(now - h, false, t("J'ai mal à l'épaule depuis hier", "My shoulder has hurt since yesterday")), CMsg(now - h + 1000, true, t("On la ménage 2 jours : le développé militaire est remplacé par des pompes, sans bras au-dessus de la tête.", "Let's spare it for 2 days: the overhead press is replaced by push-ups, no arms overhead."), p1)),
            journal = listOf(
                CDecision(now - h + 2000, "discussion", t("Épaule ménagée 2 jours", "Shoulder spared for 2 days"), t("J'ai mal à l'épaule depuis hier", "My shoulder has hurt since yesterday"),
                    listOf(t("Développé militaire debout → Pompes (Épaule ménagée : pas de bras au-dessus de la tête)", "Standing overhead press → Push-ups (shoulder spared: no arms overhead)"), t("Épaule ménagée pendant 2 jours : exercices qui la sollicitent remplacés", "Shoulder spared for 2 days: exercises that load it are replaced")), p1, today.toString(), until = today.plusDays(1).toString()),
                CDecision(now - 26 * h + 4000, "debrief", t("Charges ajustées pour la prochaine fois", "Loads adjusted for next time"), t("Trop facile", "Too easy"), listOf(t("Prochaine fois : Goblet squat +1 cran (tu as trouvé ça trop facile)", "Next time: Goblet squat +1 step (you found it too easy)")), p2, today.minusDays(1).toString())),
            goals = listOf(CGoal(1, t("Rando de 20 km", "20 km hike"), today.plusDays(30).toString(), today.minusDays(15).toString())),
            memo = listOf(CMemo(2, t("N'aime pas les burpees", "Doesn't like burpees")), CMemo(3, t("S'entraîne plutôt le matin", "Usually trains in the morning"))),
            bilans = mapOf(today.with(java.time.DayOfWeek.MONDAY).minusWeeks(1).toString() to t("5 séances sur 4 prévues : belle régularité. Le tirage est un peu en retard sur la poussée, le programme rattrape cette semaine avec une série de rowing en plus.", "5 workouts out of 4 planned: great consistency. Pulling lags a little behind pushing, so this week the program adds one set of rows.")))
        }
        CoachData.setLeft(34)
    }

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
        Store.log.value = if (!legs) log else log + Entry(d = now - (0.8 * day).toLong(), c = 1, w = 2, s = "B", f = "Difficile", rest = 30, bw = 76.0,
            ex = Prog.sess(1)["B"]!!.ex.map { id -> ExRec(id, Prog.EX[id]!!.n, if (Prog.hasDb(id)) 8.0 else 0.0, 12, "reps", List(4) { SetRec(12, 0) }, 12, "Difficile") })
        if (legs) Store.setSettings { it.c = 1; it.w = 2; it.s = "D" }
        val t = LocalDate.now()
        Store.days.value = (13 downTo 0).map { i ->
            val d = t.minusDays(i.toLong())
            if (i == 3) DayRec(d.toString(), 14200, "Randonnée", 6.0, 450, 180) else DayRec(d.toString(), 5000 + (i * 1371) % 7000, src = "hc")
        }
    }
}
