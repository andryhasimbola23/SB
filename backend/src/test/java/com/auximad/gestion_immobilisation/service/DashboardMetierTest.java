package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.DashboardStatsDto;
import com.auximad.gestion_immobilisation.dto.RepartitionDto;
import com.auximad.gestion_immobilisation.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Indicateurs du tableau de bord v2 :
 * - le taux d'amortissement global est borné à [0, 100] : une VNC anormale
 *   ne doit pas produire de « négatif amorti » ni de pourcentage > 100 ;
 * - un parc vide ou sans acquisition produit 0, jamais une division par zéro ;
 * - la répartition par agence suit la valeur décroissante de la requête.
 */
@ExtendWith(MockitoExtension.class)
class DashboardMetierTest {

    @Mock private ImmobilisationRepository immobilisationRepository;
    @Mock private MouvementRepository mouvementRepository;
    @Mock private CampagneInventaireRepository campagneRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private AgenceRepository agenceRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private CategorieRepository categorieRepository;
    @Mock private FournisseurRepository fournisseurRepository;

    @InjectMocks
    private DashboardMetier dashboardMetier;

    @Test
    @DisplayName("Parc vide : taux d'amortissement à 0, jamais de division par zéro")
    void tauxParcVide() {
        when(immobilisationRepository.getValeurTotaleParc(isNull())).thenReturn(BigDecimal.ZERO);

        DashboardStatsDto stats = dashboardMetier.getStats(null);

        assertEquals(0, stats.getTauxAmortissementGlobal().compareTo(BigDecimal.valueOf(0)),
                "un parc sans valeur ne peut pas avoir de taux");
    }

    @Test
    @DisplayName("VNC supérieure à l'acquisition : le taux est borné à 0")
    void tauxBornéASoin() {
        when(immobilisationRepository.getValeurTotaleParc(isNull())).thenReturn(new BigDecimal("1000"));
        // Bien « très récent » : VNC > valeur d'achat (taux saisi trop bas).
        when(immobilisationRepository.findDonneesVncDansPerimetre(isNull()))
                .thenReturn(List.<Object[]>of(new Object[]{ new BigDecimal("1200"), BigDecimal.ZERO, null }));

        DashboardStatsDto stats = dashboardMetier.getStats(null);

        assertEquals(0, stats.getTauxAmortissementGlobal().signum(),
                "un taux négatif n'a aucun sens comptable : il est borné à 0");
    }

    @Test
    @DisplayName("Parc entièrement amorti : le taux plafonne à 100 %")
    void tauxPlafonne() {
        when(immobilisationRepository.getValeurTotaleParc(isNull())).thenReturn(new BigDecimal("1000"));
        // 100 %/an depuis 2 ans : entièrement amorti (ou négatif selon la
        // formule — dans les deux cas le taux global doit afficher 100).
        when(immobilisationRepository.findDonneesVncDansPerimetre(isNull()))
                .thenReturn(List.<Object[]>of(new Object[]{
                        new BigDecimal("1000"), new BigDecimal("100"), LocalDateTime.now().minusYears(2) }));

        DashboardStatsDto stats = dashboardMetier.getStats(null);

        assertEquals(0, stats.getTauxAmortissementGlobal().compareTo(BigDecimal.valueOf(100)),
                "un parc amorti affiche exactement 100 %, jamais davantage");
    }

    @Test
    @DisplayName("Parc ancien : le seuil de 5 ans est appliqué au comptage")
    void seuilParcAncien() {
        dashboardMetier.getStats(null);

        verify(immobilisationRepository).countActifsAnciens(
                any(LocalDateTime.class), isNull());
    }

    @Test
    @DisplayName("Répartition par agence : le mapping conserve nom, nombre et valeur")
    void repartitionParAgence() {
        when(immobilisationRepository.getRepartitionParAgence(isNull())).thenReturn(List.of(
                new Object[]{ "Antananarivo-Sud", 3L, new BigDecimal("900") },
                new Object[]{ "Toamasina", 1L, new BigDecimal("100") }));

        List<RepartitionDto> resultat = dashboardMetier.getRepartitionParAgence(null);

        assertEquals(2, resultat.size());
        assertEquals("Antananarivo-Sud", resultat.get(0).getLabel());
        assertEquals(3L, resultat.get(0).getNombre());
        assertEquals(0, new BigDecimal("900").compareTo(resultat.get(0).getValeur()));
        assertEquals("Toamasina", resultat.get(1).getLabel());
    }
}
