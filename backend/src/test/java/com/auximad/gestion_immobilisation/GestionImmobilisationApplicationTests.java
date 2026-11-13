package com.auximad.gestion_immobilisation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Vérifie que le contexte Spring démarre.
 *
 * <p>Le profil {@code test} est indispensable ici : sans lui, ce test ouvrirait
 * la base MySQL de développement et pourrait y écrire via les runners de
 * démarrage.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class GestionImmobilisationApplicationTests {

	@Test
	void contextLoads() {
	}

}
