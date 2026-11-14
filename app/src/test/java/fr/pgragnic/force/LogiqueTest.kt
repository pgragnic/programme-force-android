package fr.pgragnic.force

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/** Tests de la logique de séance, lancés à chaque compilation sur GitHub (gradle :app:testDebugUnitTest). */
class LogiqueTest {
    private val prog: JsonObject = Json.parseToJsonElement(File("src/main/assets/programme.json").readText()).jsonObject
    private val st = Settings()

    /** Tout le matériel du programme coché, kettlebells et haltères fixes compris. */
    private val tout: Gear by lazy {
        val items = prog["EQ"]!!.jsonArray.flatMap { g -> g.jsonArray[1].jsonArray.map { it.jsonArray[0].jsonPrimitive.content } }.toSet()
        Gear(items = items + "db", kbs = listOf(8.0, 12.0, 16.0), level = 1, bw = 80.0, done = true)
    }
    private val base = Gear(level = 0, bw = 80.0, done = true)

    @Before fun charge() { L.en = false; GearStore.g.value = base; Prog.apply(prog) }

    private fun cycles() = listOf(1, 2)
    private fun seances(c: Int) = Prog.sess(c).keys.sorted()

    @Test fun programmeLivreValide() = assertNull(Prog.check(prog))

    @Test fun programmeIncompletRefuse() {
        assertNotNull(Prog.check(JsonObject(prog - "EX")))
        val sess = prog["SESS1"]!!.jsonObject; val a = sess["A"]!!.jsonObject
        val faux = JsonObject(prog + ("SESS1" to JsonObject(sess + ("A" to JsonObject(a + ("ex" to JsonArray(listOf(JsonPrimitive("inconnu")))))))))
        assertTrue(Prog.check(faux)!!.contains("inconnu"))
        assertNotNull(Prog.check(JsonObject(prog + ("FMT" to JsonPrimitive(Prog.FORMAT + 1)))))
    }

    @Test fun programmeFautifGardeLePrecedent() {
        val n = Prog.EX.size
        assertFalse(Prog.safeApply(JsonObject(prog - "WU")))
        assertEquals(n, Prog.EX.size)
    }

    @Test fun seancesCompletesEtFaisables() {
        for (g in listOf(base, tout)) { GearStore.g.value = g
            for (c in cycles()) for (s in seances(c)) {
                val ex = Prog.sessEx(c, s, g)
                assertTrue("$c$s vide", ex.size >= 4)
                assertEquals("$c$s doublon", ex.size, ex.toSet().size)
                ex.forEach { assertTrue("$c$s : $it pas faisable", Prog.ok(it, g)) }
            }
        }
    }

    @Test fun chaqueMaterielApparaitDansLaSemaine() {
        GearStore.g.value = tout
        for (c in cycles()) {
            val tools = Prog.week(c, tout).values.flatten().map { Prog.toolKey(it) }.toSet()
            listOf("kb", "barbell", "band").forEach { assertTrue("cycle $c : $it absent", it in tools) }
        }
    }

    @Test fun restrictionDeSanteRespectee() {
        val g = base.copy(limits = setOf("genou"))
        GearStore.g.value = g
        for (c in cycles()) for (s in seances(c)) Prog.sessEx(c, s, g).forEach { assertFalse("$it sollicite le genou", "genou" in Prog.EX[it]!!.stress) }
    }

    @Test fun ordreGainageALaFin() {
        for (c in cycles()) for (s in seances(c)) {
            val o = Prog.orderEx(c, s, 0, st); val ex = Prog.sessEx(c, s)
            assertEquals(ex.toSet(), o.toSet())
            val firstCore = o.indexOfFirst { it in Prog.CORE }
            if (firstCore >= 0) assertTrue("$c$s : gainage avant la fin", o.drop(firstCore).all { it in Prog.CORE })
        }
    }

    @Test fun peuDarretsPourChangerLesDisques() {
        for (c in cycles()) for (s in seances(c)) for (w in 0..7) {
            val stops = Prog.phases(c, w, s, 45, st, emptyList()).count { it.chg != null }
            assertTrue("$c$s S${w + 1} : $stops arrêts", stops <= 2)
        }
    }

    @Test fun plafondDeDureeRespecte() {
        for (cap in listOf(30, 35, 40)) {
            GearStore.g.value = base.copy(maxMin = cap)
            for (c in cycles()) for (s in seances(c)) for (w in listOf(0, 5)) {
                val P = Prog.phases(c, w, s, 45, st, emptyList())
                val sets = P.filter { it.k == "work" }.groupBy { it.ex }.mapValues { it.value.size }
                sets.forEach { (id, n) -> assertTrue("$c$s : $id à $n séries", n >= 2) }
                assertTrue("$c$s S${w + 1} : ${P.sumOf { it.dur } / 60} min > $cap", P.sumOf { it.dur } <= cap * 60.0 || sets.values.all { it == 2 })
            }
        }
    }

    private fun seance(d: Long, id: String, kg: Double, done: Int, target: Int, f: String = "Juste bien") =
        Entry(d = d, w = 1, s = "A", ex = listOf(ExRec(id, id, kg, target, done = done, f = f)))

    @Test fun chargeMonteApresSeancesReussies() {
        val id = "hammer"; Prog.computeLoads(emptyList(), st); val k0 = Prog.kgOf(id, 1, st)
        val log = listOf(0, 7, 15).map { seance(it * Prog.DAY, id, k0, 10, 10) }
        Prog.computeLoads(log, st)
        assertTrue("${Prog.kgOf(id, 1, st)} <= $k0", Prog.kgOf(id, 1, st) > k0)
    }

    @Test fun basDuDosTroisSeancesAvantHausse() {
        // soulevé de terre jambes tendues : 2 séances réussies ne suffisent plus, il en faut 3 ; le goblet squat reste à 2
        GearStore.g.value = base.copy(dbMode = "fixed", fixed = listOf(10.0, 10.5, 11.0, 11.5, 12.0))
        for ((id, need) in listOf("rdl" to 3, "squat" to 2)) {
            Prog.computeLoads(emptyList(), st); val k0 = Prog.kgOf(id, 1, st)
            Prog.computeLoads(listOf(0, 7).map { seance(it * Prog.DAY, id, k0, 10, 10) }, st)
            assertEquals(need == 2, Prog.kgOf(id, 1, st) > k0)
            Prog.computeLoads(listOf(0, 7, 15).map { seance(it * Prog.DAY, id, k0, 10, 10) }, st)
            assertTrue("$id", Prog.kgOf(id, 1, st) > k0)
        }
    }

    @Test fun plafondPetitesChargesUnCranToutesLesDeuxSemaines() {
        // 4,8 → 5,8 kg = + 21 % : au-delà du plafond, permis seulement 14 jours après le premier essai ou la dernière hausse
        GearStore.g.value = base.copy(level = 1)
        val id = "hammer"; Prog.computeLoads(emptyList(), st); val k0 = Prog.kgOf(id, 1, st)
        Prog.computeLoads(listOf(0, 2, 4).map { seance(it * Prog.DAY, id, k0, 10, 10) }, st)
        assertEquals(k0, Prog.kgOf(id, 1, st), 1e-6)
        Prog.computeLoads(listOf(0, 2, 4, 14).map { seance(it * Prog.DAY, id, k0, 10, 10) }, st)
        assertTrue(Prog.kgOf(id, 1, st) > k0)
    }

    @Test fun plafondHebdoPlusPrudentApres50Ans() {
        val fixe = base.copy(level = 1, dbMode = "fixed", fixed = listOf(20.0, 21.0, 22.0, 24.0))
        val id = "hammer"
        fun apres(g: Gear): Double {
            GearStore.g.value = g; Prog.computeLoads(emptyList(), st)
            var log = listOf(0L, 1L, 2L).map { seance(it * Prog.DAY, id, 20.0, 10, 10) }
            Prog.computeLoads(log, st); val k1 = Prog.kgOf(id, 1, st)
            log = log + listOf(3L, 4L, 5L).map { seance(it * Prog.DAY, id, k1, 10, 10) }
            Prog.computeLoads(log, st); return Prog.kgOf(id, 1, st)
        }
        // standard (+ 10 %) : 20 → 21 → 22 dans la semaine ; 50 ans et plus (+ 5 %) : 20 → 21 seulement
        assertEquals(22.0, apres(fixe), 1e-6)
        assertEquals(21.0, apres(fixe.copy(born = java.time.LocalDate.now().year - 55)), 1e-6)
        assertEquals(21.0, apres(fixe.copy(level = 0)), 1e-6)
    }

    @Test fun chargeBaisseApresDeuxEchecs() {
        val id = "hammer"; val k = 6.8
        Prog.computeLoads(listOf(seance(1, id, k, 5, 10), seance(2, id, k, 5, 10)), st)
        assertTrue(Prog.kgOf(id, 1, st) < k)
    }

    @Test fun disquesParCote() {
        assertEquals("barre seule", Prog.platesFor(1.8, st))
        assertEquals("2 kg par côté", Prog.platesFor(5.8, st))
        assertTrue(Prog.platesFor(4.8, st).contains("0,5 kg"))
    }

    @Test fun fusionSynchroSansPerte() {
        val a = Entry(d = 1, w = 0, s = "A", _id = "a"); val b = Entry(d = 2, w = 0, s = "B", _id = "b")
        val c = Entry(d = 3, w = 0, s = "C", _id = "c"); val bModif = b.copy(f = "Facile")
        // b modifiée hors connexion, c créée hors connexion, a supprimée hors connexion, d vient d'un autre téléphone
        val d = Entry(d = 4, w = 0, s = "D", _id = "d")
        val out = Store.merge(local = listOf(bModif, c), server = listOf(a, b, d), pend = setOf(2L, 3L), del = setOf("a"))
        assertEquals(listOf("b", "c", "d"), out.map { it._id })
        assertEquals("Facile", out.first { it._id == "b" }.f)
        // séance envoyée puis supprimée sur un autre téléphone : elle disparaît ici aussi
        assertEquals(listOf("d"), Store.merge(listOf(a), listOf(d), emptySet(), emptySet()).map { it._id })
    }

    @Test fun exportCsvEtJson() {
        Store.log.value = listOf(seance(1_700_000_000_000, "hammer", 4.8, 10, 10))
        val csv = Export.csv().lines()
        assertEquals(2, csv.size)
        assertTrue(csv[0].startsWith("date;cycle;semaine"))
        assertTrue(csv[1].contains(";4,8;10;10;"))
        assertTrue(Export.json().contains("\"seances\""))
        Store.log.value = emptyList()
    }
}
