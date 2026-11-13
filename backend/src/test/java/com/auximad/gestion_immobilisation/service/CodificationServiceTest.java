package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.exception.ConflitMetierException;
import com.auximad.gestion_immobilisation.model.Agence;
import com.auximad.gestion_immobilisation.model.Categorie;
import com.auximad.gestion_immobilisation.model.CompteurCodification;
import com.auximad.gestion_immobilisation.model.Service;
import com.auximad.gestion_immobilisation.repository.CompteurCodificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests de la codification unique (séquenceur sous verrou).
 */
@ExtendWith(MockitoExtension.class)
class CodificationServiceTest {

    @Mock
    private CompteurCodificationRepository compteurRepository;

    @InjectMocks
    private CodificationService codificationService;

    private static Agence agence() {
        return Agence.builder().codeAgence("90").nomAgence("Siège").build();
    }

    private static Service service() {
        return Service.builder().code("SIT").nomService("Systèmes d'information").build();
    }

    // service() fixe l'id null → on le renseigne pour le couple (serviceId, type)
    private static Service serviceAvecId(Long id) {
        Service s = service();
        s.setId(id);
        return s;
    }

    private static Categorie categorie() {
        return Categorie.builder().codeComptable("2183").nomCategorie("Matériel informatique").build();
    }

    @Test
    @DisplayName("Compteur existant : incrémentation et format attendu")
    void compteurExistant() {
        Service s = serviceAvecId(7L);
        when(compteurRepository.findByServiceIdAndTypeMaterielForUpdate(7L, "UCB"))
                .thenReturn(Optional.of(CompteurCodification.builder()
                        .serviceId(7L).typeMateriel("UCB").dernierNumero(3).build()));

        String code = codificationService.genererCodeUnique(agence(), s, categorie(), "UCB");

        String annee = String.valueOf(LocalDate.now().getYear()).substring(2);
        assertEquals("90/" + annee + "/SIT/2183/UCB/004", code);
    }

    @Test
    @DisplayName("Compteur absent : création à 0 puis premier numéro 001")
    void compteurAbsent() {
        Service s = serviceAvecId(7L);
        when(compteurRepository.findByServiceIdAndTypeMaterielForUpdate(7L, "ARM"))
                .thenReturn(Optional.empty());
        when(compteurRepository.save(any(CompteurCodification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String code = codificationService.genererCodeUnique(agence(), s, categorie(), "ARM");

        String annee = String.valueOf(LocalDate.now().getYear()).substring(2);
        assertTrue(code.endsWith("/ARM/001"), "premier numéro = 001, reçu : " + code);
        assertTrue(code.startsWith("90/" + annee + "/SIT/2183/"), "format de tête, reçu : " + code);
    }

    @Test
    @DisplayName("TypeMateriel saisi en minuscules : le code est normalisé en majuscules")
    void typeNormalise() {
        Service s = serviceAvecId(7L);
        when(compteurRepository.findByServiceIdAndTypeMaterielForUpdate(7L, "UCB"))
                .thenReturn(Optional.of(CompteurCodification.builder()
                        .serviceId(7L).typeMateriel("UCB").dernierNumero(0).build()));

        String code = codificationService.genererCodeUnique(agence(), s, categorie(), "ucb");

        assertTrue(code.endsWith("/UCB/001"), "type en majuscules, reçu : " + code);
    }

    @Test
    @DisplayName("Course sur un compteur neuf : conflit métier clair (409), jamais un 500")
    void courseSurCompteurNeuf() {
        Service s = serviceAvecId(7L);
        when(compteurRepository.findByServiceIdAndTypeMaterielForUpdate(7L, "TAB"))
                .thenReturn(Optional.empty());
        when(compteurRepository.save(any(CompteurCodification.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(ConflitMetierException.class,
                () -> codificationService.genererCodeUnique(agence(), s, categorie(), "TAB"));
    }
}
