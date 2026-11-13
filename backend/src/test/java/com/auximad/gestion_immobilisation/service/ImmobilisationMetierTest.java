package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.ImmobilisationDto;
import com.auximad.gestion_immobilisation.exception.ConflitMetierException;
import com.auximad.gestion_immobilisation.model.Immobilisation;
import com.auximad.gestion_immobilisation.repository.AgenceRepository;
import com.auximad.gestion_immobilisation.repository.CategorieRepository;
import com.auximad.gestion_immobilisation.repository.DemandeImmobilisationRepository;
import com.auximad.gestion_immobilisation.repository.FournisseurRepository;
import com.auximad.gestion_immobilisation.repository.ImmobilisationRepository;
import com.auximad.gestion_immobilisation.repository.LigneInventaireRepository;
import com.auximad.gestion_immobilisation.repository.MouvementRepository;
import com.auximad.gestion_immobilisation.repository.ServiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Règles de gestion du F12 :
 * - une immobilisation déjà référencée ne se supprime pas (409 lisible,
 *   jamais une erreur 500 « foreign key ») ;
 * - le statut (ACTIF / CEDE / REBUTE) n'est pas modifiable par une simple
 *   édition de fiche : il ne change que par un mouvement tracé.
 */
@ExtendWith(MockitoExtension.class)
class ImmobilisationMetierTest {

    @Mock private ImmobilisationRepository immobilisationRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private AgenceRepository agenceRepository;
    @Mock private CategorieRepository categorieRepository;
    @Mock private FournisseurRepository fournisseurRepository;
    @Mock private MouvementRepository mouvementRepository;
    @Mock private LigneInventaireRepository ligneInventaireRepository;
    @Mock private DemandeImmobilisationRepository demandeRepository;
    @Mock private QrCodeService qrCodeService;
    @Mock private CodificationService codificationService;
    @Mock private PerimetreService perimetreService;
    @Mock private JournalAuditMetier journalAuditMetier;

    @InjectMocks
    private ImmobilisationMetier immobilisationMetier;

    private static Immobilisation bienActif() {
        return Immobilisation.builder()
                .id(1L)
                .codeUnique("90/26/SIT/2183/UCB/001")
                .designation("PC portable")
                .dateAcquisition(LocalDateTime.of(2026, 1, 10, 0, 0))
                .dateMiseService(LocalDateTime.of(2026, 1, 15, 0, 0))
                .valeurAchat(new BigDecimal("2000000"))
                .statut("ACTIF")
                .etatPhysique("BON")
                .marque("Dell")
                .tauxAmortissement(new BigDecimal("20"))
                .typeMateriel("UCB")
                .build();
    }

    @Test
    @DisplayName("Bien sans référence : suppression effectuée et tracée au journal")
    void suppressionAutorisee() {
        when(immobilisationRepository.findById(1L)).thenReturn(Optional.of(bienActif()));
        when(mouvementRepository.countByImmobilisationId(1L)).thenReturn(0L);
        when(ligneInventaireRepository.countByImmobilisationId(1L)).thenReturn(0L);
        when(demandeRepository.countByImmobilisationId(1L)).thenReturn(0L);

        immobilisationMetier.deleteImmobilisation(1L, null);

        verify(immobilisationRepository).deleteById(1L);
        verify(journalAuditMetier).enregistrer(isNull(),
                eq(JournalAuditMetier.SUPPRESSION),
                eq(JournalAuditMetier.ENTITE_IMMOBILISATION), eq(1L), any());
    }

    @Test
    @DisplayName("Bien rattaché à un mouvement : 409, jamais de suppression")
    void suppressionRefuseeSiMouvement() {
        when(immobilisationRepository.findById(1L)).thenReturn(Optional.of(bienActif()));
        when(mouvementRepository.countByImmobilisationId(1L)).thenReturn(2L);

        assertThrows(ConflitMetierException.class,
                () -> immobilisationMetier.deleteImmobilisation(1L, null));

        verify(immobilisationRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Bien rattaché uniquement à un constat d'inventaire : 409")
    void suppressionRefuseeSiConstat() {
        when(immobilisationRepository.findById(1L)).thenReturn(Optional.of(bienActif()));
        when(mouvementRepository.countByImmobilisationId(1L)).thenReturn(0L);
        when(ligneInventaireRepository.countByImmobilisationId(1L)).thenReturn(1L);

        assertThrows(ConflitMetierException.class,
                () -> immobilisationMetier.deleteImmobilisation(1L, null));

        verify(immobilisationRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("PUT avec statut CEDE : le statut ACTIF est conservé (mouvement requis)")
    void statutIgnoreEnUpdate() {
        Immobilisation immo = bienActif();
        when(immobilisationRepository.findById(1L)).thenReturn(Optional.of(immo));
        when(immobilisationRepository.save(any(Immobilisation.class))).thenAnswer(inv -> inv.getArgument(0));

        ImmobilisationDto dto = new ImmobilisationDto();
        dto.setDesignation("PC portable Dell");
        dto.setDateAcquisition(immo.getDateAcquisition());
        dto.setDateMiseService(immo.getDateMiseService());
        dto.setValeurAchat(immo.getValeurAchat());
        dto.setStatut("CEDE");              // tentative de contournement
        dto.setEtatPhysique("BON");
        dto.setMarque("Dell");
        dto.setTauxAmortissement(immo.getTauxAmortissement());
        dto.setTypeMateriel("UCB");

        ImmobilisationDto resultat = immobilisationMetier.updateImmobilisation(1L, dto, null);

        assertEquals("ACTIF", resultat.getStatut(),
                "le statut ne doit pas changer par une simple édition de fiche");
        assertEquals("PC portable Dell", resultat.getDesignation(),
                "les champs descriptifs, eux, doivent être mis à jour");
    }

    @Test
    @DisplayName("Lookup par code unique (scan) : le bien du périmètre est retourné")
    void lookupParCodeUnique() {
        Immobilisation immo = bienActif();
        when(immobilisationRepository.findByCodeUnique("90/26/SIT/2183/UCB/001"))
                .thenReturn(Optional.of(immo));
        when(perimetreService.exigerAccessible(any(), any())).thenReturn(immo);

        ImmobilisationDto resultat = immobilisationMetier
                .getImmobilisationByCode("90/26/SIT/2183/UCB/001", null);

        assertEquals("90/26/SIT/2183/UCB/001", resultat.getCodeUnique());
        assertEquals("PC portable", resultat.getDesignation());
    }

    @Test
    @DisplayName("Lookup par code inconnu ou hors périmètre : 404 (pas d'énumération)")
    void lookupParCodeInconnu() {
        when(immobilisationRepository.findByCodeUnique("99/99/XXX/9999/TAB/999"))
                .thenReturn(Optional.empty());
        when(perimetreService.exigerAccessible(any(), any()))
                .thenThrow(new com.auximad.gestion_immobilisation.exception.ResourceNotFoundException(
                        "Immobilisation non trouvée"));

        assertThrows(com.auximad.gestion_immobilisation.exception.ResourceNotFoundException.class,
                () -> immobilisationMetier
                        .getImmobilisationByCode("99/99/XXX/9999/TAB/999", null));
    }
}
