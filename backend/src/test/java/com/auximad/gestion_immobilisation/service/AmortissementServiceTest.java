package com.auximad.gestion_immobilisation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests du calcul PUR de la VNC (méthode statique, sans dépendance).
 *
 * Cas limites : année de mise en service en cours (0 an écoulé), amortissement
 * normal, et plafonnement à la valeur d'achat (cumul jamais supérieur).
 */
class AmortissementServiceTest {

    private static final BigDecimal VALEUR = new BigDecimal("1000000");
    private static final BigDecimal TAUX_20 = new BigDecimal("20");

    private static LocalDateTime date(int mois, int annee) {
        return LocalDateTime.of(annee, mois, 1, 0, 0);
    }

    @Test
    @DisplayName("Aucune année complète écoulée : VNC = valeur d'achat")
    void vncPremiereAnnee() {
        int anneeCourante = java.time.LocalDate.now().getYear();

        BigDecimal vnc = AmortissementService.calculerVnc(VALEUR, TAUX_20, date(6, anneeCourante));

        assertEquals(0, vnc.compareTo(VALEUR), "une dotation ne court qu'à partir de la 2e année");
    }

    @Test
    @DisplayName("3 années écoulées à 20% : cumul 60%, VNC = 40% de la valeur")
    void vncTroisAns() {
        int anneeCourante = java.time.LocalDate.now().getYear();

        BigDecimal vnc = AmortissementService.calculerVnc(VALEUR, TAUX_20, date(1, anneeCourante - 3));

        assertEquals(0, vnc.compareTo(new BigDecimal("400000.00")));
    }

    @Test
    @DisplayName("Cumul plafonné à la valeur d'achat : VNC jamais négative")
    void vncPlafonnee() {
        int anneeCourante = java.time.LocalDate.now().getYear();

        // 15 ans écoulés à 10% : le cumul réel (150%) est plafonné à 100%
        BigDecimal vnc = AmortissementService.calculerVnc(VALEUR, new BigDecimal("10"), date(1, anneeCourante - 15));

        assertEquals(0, vnc.compareTo(BigDecimal.ZERO));
    }
}
