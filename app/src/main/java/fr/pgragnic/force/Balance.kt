package fr.pgragnic.force

/**
 * Équilibre musculaire (Philippe, 14 novembre : « Ça m'intéresserait aussi de savoir si les muscles sont utilisés de façon équilibrée »).
 * Séries pondérées par le rôle de chaque muscle (Prog.MAP) sur les 4 dernières semaines, comparées par paires antagonistes.
 */
object Balance {
    class Pair(val id: String, val a: String, val b: String, val ma: List<String>, val mb: List<String>, val va: Double, val vb: Double, val lo: Double, val hi: Double, val fixA: String, val fixB: String) {
        /** part de a dans a+b, entre 0 et 1 */
        val share get() = if (va + vb <= 0) .5 else va / (va + vb)
        val ratio get() = if (vb <= 0) (if (va > 0) 9.0 else 1.0) else va / vb
        /** -1 : a en retard ; 0 : équilibré ; 1 : b en retard */
        val state get() = when { va + vb < 4 -> 0; ratio < lo -> -1; ratio > hi -> 1; else -> 0 }
        val advice get() = when (state) { -1 -> fixA; 1 -> fixB; else -> null }
        /** muscles du côté en retard */
        val lag get() = when (state) { -1 -> ma; 1 -> mb; else -> emptyList() }
    }
    class Res(val sets: Map<String, Double>, val pairs: List<Pair>, val weeks: Int, val low: List<String>) { val ok get() = pairs.none { it.state != 0 } && low.isEmpty() }

    /** end : fin de la fenêtre (par défaut maintenant) */
    fun compute(log: List<Entry>, days: Int = 28, end: Long = System.currentTimeMillis()): Res {
        val t0 = end - days * 864e5.toLong()
        val S = Recovery.MUS.associateWith { 0.0 }.toMutableMap()
        log.filter { it.d >= t0 && it.d < end }.forEach { e -> e.ex.forEach { x ->
            val n = (if (x.sets.isNotEmpty()) x.sets.size else 3).toDouble()
            Prog.MAP[x.id]?.forEach { (m, r) -> if (m in S) S[m] = S[m]!! + n * r }
        } }
        val wk = (days / 7).coerceAtLeast(1)
        fun v(m: List<String>) = m.sumOf { S[it] ?: 0.0 }
        val PU = listOf("Pectoraux", "Épaules", "Triceps"); val PL = listOf("Dos", "Biceps")
        val UP = PU + PL; val LO = listOf("Fessiers", "Quadriceps", "Ischios", "Mollets")
        val pairs = listOf(
            Pair("pp", t("Poussée", "Push"), t("Tirage", "Pull"), PU, PL, v(PU), v(PL) * 1.2, .75, 1.35,
                t("La poussée est en retard : ajoute des pompes ou du développé.", "Push is lagging: add push-ups or presses."),
                t("Le tirage est en retard : ajoute du rowing ou du tirage, bon pour le dos et la posture.", "Pull is lagging: add rows, good for the back and posture.")),
            Pair("hb", t("Haut du corps", "Upper body"), t("Bas du corps", "Lower body"), UP, LO, v(UP), v(LO) * 1.4, .6, 1.7,
                t("Le haut du corps est peu travaillé par rapport aux jambes.", "Upper body gets little work compared to legs."),
                t("Les jambes sont peu travaillées par rapport au haut du corps.", "Legs get little work compared to upper body.")),
            Pair("qi", t("Avant des cuisses", "Front of thighs"), t("Arrière des cuisses", "Back of thighs"), listOf("Quadriceps"), listOf("Ischios"), v(listOf("Quadriceps")), v(listOf("Ischios")), .5, 2.0,
                t("Les quadriceps sont en retard : squats ou fentes.", "Quads are lagging: squats or lunges."),
                t("L'arrière des cuisses est en retard : soulevé de terre jambes tendues ou pont fessier, utile pour protéger les genoux.", "The back of the thighs is lagging: Romanian deadlifts or glute bridges, useful to protect the knees.")),
            Pair("al", t("Abdos", "Abs"), t("Lombaires", "Lower back"), listOf("Abdos"), listOf("Lombaires"), v(listOf("Abdos")), v(listOf("Lombaires")), .5, 2.2,
                t("Les abdos sont peu travaillés : gainage ou crunchs.", "Abs get little work: planks or crunches."),
                t("Les lombaires sont peu travaillées : superman ou soulevé de terre.", "Lower back gets little work: supermans or deadlifts.")),
        )
        val tot = S.values.sum()
        // muscle quasi oublié : moins de 3 % du volume alors qu'on s'entraîne
        val low = if (tot < 20) emptyList() else Recovery.MUS.filter { S[it]!! / tot < .03 }
        return Res(S.mapValues { it.value / wk }, pairs, wk, low)
    }

    /** Rattrapage (Philippe : « Et que la semaine suivante rattrape le déséquilibre s'il y en a un ») :
     *  bilan des 2 semaines avant ce lundi, figé toute la semaine ; les exercices qui travaillent surtout un côté en retard gagnent 1 série. */
    private var key = ""; private var lagM: Set<String> = emptySet(); var week: Res? = null; private set
    fun weekRes(): Res {
        val log = Store.log.value; val m = Prog.mondayOf(System.currentTimeMillis()); val k = "$m|${log.size}|${log.maxOfOrNull { it.d }}"
        if (k != key) { key = k; val r = compute(log, 14, m); week = r; lagM = r.pairs.flatMap { it.lag }.toSet() }
        return week!!
    }
    fun boost(id: String): Boolean { weekRes(); return lagM.isNotEmpty() && (Prog.MAP[id] ?: emptyMap()).any { it.value >= .7 && it.key in lagM } }
}
