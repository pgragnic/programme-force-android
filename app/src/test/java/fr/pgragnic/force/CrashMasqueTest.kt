package fr.pgragnic.force

import org.junit.Assert.assertEquals
import org.junit.Test

/** Masquage des données personnelles dans les rapports de plantage (audit de sécurité, point 10). */
class CrashMasqueTest {
    @Test fun masque_email_jeton_et_identifiant() {
        val t = "java.lang.IllegalStateException: compte pgragnic@hotmail.com, user 3f2a9c1e-12ab-4cde-8f01-23456789abcd, " +
            "jeton eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abc-def_1\n\tat fr.pgragnic.force.Store.pull(Store.kt:120)"
        assertEquals("java.lang.IllegalStateException: compte [e-mail], user [id], jeton [jeton]\n\tat fr.pgragnic.force.Store.pull(Store.kt:120)",
            Crash.masquer(t))
    }
    @Test fun trace_sans_donnee_inchangee() {
        val t = "java.lang.NullPointerException\n\tat fr.pgragnic.force.ui.ScreensKt.Seance(Screens.kt:42)"
        assertEquals(t, Crash.masquer(t))
    }
}
