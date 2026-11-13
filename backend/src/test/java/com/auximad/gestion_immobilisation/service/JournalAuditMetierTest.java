package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.ExportJournalDto;
import com.auximad.gestion_immobilisation.dto.JournalAuditDto;
import com.auximad.gestion_immobilisation.model.JournalAudit;
import com.auximad.gestion_immobilisation.model.Role;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.repository.JournalAuditRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JournalAuditMetierTest {

    @Mock
    private JournalAuditRepository journalAuditRepository;

    @InjectMocks
    private JournalAuditMetier journalAuditMetier;

    private Utilisateur admin() {
        return Utilisateur.builder().id(1L).prenom("Ada").nom("Lovelace").role(Role.ADMIN).build();
    }

    @Test
    @DisplayName("enregistrer : la ligne porte l'acteur, l'action, l'entité et la description")
    void enregistrerEcritLaLigne() {
        when(journalAuditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        journalAuditMetier.enregistrer(admin(), JournalAuditMetier.CESSION,
                JournalAuditMetier.ENTITE_MOUVEMENT, 42L, "PC portable (90/26/SIT/2183/UCB/001)");

        ArgumentCaptor<JournalAudit> captor = ArgumentCaptor.forClass(JournalAudit.class);
        verify(journalAuditRepository).save(captor.capture());
        JournalAudit ligne = captor.getValue();
        assertEquals(1L, ligne.getActeur().getId());
        assertEquals("CESSION", ligne.getAction());
        assertEquals("Mouvement", ligne.getEntite());
        assertEquals(42L, ligne.getEntiteId());
        assertEquals("PC portable (90/26/SIT/2183/UCB/001)", ligne.getDescription());
        assertNotNull(ligne.getDateAction(), "l'horodatage est posé à l'écriture");
    }

    @Test
    @DisplayName("enregistrer : la description est tronquée à la capacité de la colonne")
    void descriptionTronquee() {
        when(journalAuditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        journalAuditMetier.enregistrer(null, JournalAuditMetier.CONNEXION_ECHOUEE,
                JournalAuditMetier.ENTITE_SESSION, null, "x".repeat(600));

        ArgumentCaptor<JournalAudit> captor = ArgumentCaptor.forClass(JournalAudit.class);
        verify(journalAuditRepository).save(captor.capture());
        assertEquals(500, captor.getValue().getDescription().length());
        assertTrue(captor.getValue().getDescription().endsWith("…"));
        assertNull(captor.getValue().getActeur(), "une tentative échouée n'a pas d'acteur");
    }

    @Test
    @DisplayName("rechercher : les filtres vides sont neutralisés (null transmis)")
    void rechercherNormaliseLesFiltres() {
        when(journalAuditRepository.rechercher(isNull(), isNull(), isNull()))
                .thenReturn(List.of());

        List<JournalAuditDto> resultat = journalAuditMetier.rechercher("  ", "", null);

        assertTrue(resultat.isEmpty());
        verify(journalAuditRepository).rechercher(isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("rechercher : les filtres renseignés passent tels quels, du plus récent au plus ancien")
    void rechercherPasseLesFiltres() {
        JournalAudit ligne = JournalAudit.builder()
                .id(7L).acteur(admin())
                .action(JournalAuditMetier.REBUT)
                .entite(JournalAuditMetier.ENTITE_MOUVEMENT)
                .entiteId(3L)
                .description("Imprimé laser (90/26/SIT/2183/UCB/009)")
                .dateAction(LocalDateTime.of(2026, 10, 22, 14, 30))
                .build();
        when(journalAuditRepository.rechercher("REBUT", "Mouvement", 1L))
                .thenReturn(List.of(ligne));

        List<JournalAuditDto> resultat = journalAuditMetier.rechercher("REBUT", "Mouvement", 1L);

        assertEquals(1, resultat.size());
        JournalAuditDto dto = resultat.get(0);
        assertEquals("Ada Lovelace", dto.getActeurNom(), "le nom complet est dérivé de l'acteur");
        assertEquals("REBUT", dto.getAction());
        assertEquals(3L, dto.getEntiteId());
        verify(journalAuditRepository).rechercher("REBUT", "Mouvement", 1L);
    }

    @Test
    @DisplayName("enregistrer : acteur absent et description nulle admissibles")
    void enregistrerSansActeur() {
        when(journalAuditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        journalAuditMetier.enregistrer(null, JournalAuditMetier.CREATION,
                JournalAuditMetier.ENTITE_SESSION, null, null);

        ArgumentCaptor<JournalAudit> captor = ArgumentCaptor.forClass(JournalAudit.class);
        verify(journalAuditRepository).save(captor.capture());
        assertNull(captor.getValue().getActeur());
        assertEquals("", captor.getValue().getDescription(), "une description absente devient vide");
    }

    @Test
    @DisplayName("enregistrerExport : l'export du journal est lui-même tracé, avec le résumé des filtres")
    void enregistrerExportTraceLaConsultation() {
        when(journalAuditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExportJournalDto dto = new ExportJournalDto();
        dto.setNombre(12);
        dto.setAction("CONNEXION_ECHOUEE");
        dto.setDu("2026-10-01");
        dto.setRecherche("  tojo  ");

        journalAuditMetier.enregistrerExport(admin(), dto);

        ArgumentCaptor<JournalAudit> captor = ArgumentCaptor.forClass(JournalAudit.class);
        verify(journalAuditRepository).save(captor.capture());
        JournalAudit ligne = captor.getValue();
        assertEquals(JournalAuditMetier.EXPORT_JOURNAL, ligne.getAction());
        assertEquals(JournalAuditMetier.ENTITE_JOURNAL, ligne.getEntite(), "le journal audite sa propre extraction");
        assertEquals(1L, ligne.getActeur().getId(), "l'export a un acteur : l'ADMIN qui tire le journal");
        assertTrue(ligne.getDescription().contains("12 entrée(s)"));
        assertTrue(ligne.getDescription().contains("action CONNEXION_ECHOUEE"));
        assertTrue(ligne.getDescription().contains("période 2026-10-01 → …"), "une borne seule reste lisible");
        assertTrue(ligne.getDescription().contains("recherche « tojo »"), "les critères sont trimés avant journalisation");
    }

    @Test
    @DisplayName("enregistrerExport : sans filtre, la trace dit « journal complet »")
    void enregistrerExportSansFiltre() {
        when(journalAuditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExportJournalDto dto = new ExportJournalDto();
        dto.setNombre(5);

        journalAuditMetier.enregistrerExport(null, dto);

        ArgumentCaptor<JournalAudit> captor = ArgumentCaptor.forClass(JournalAudit.class);
        verify(journalAuditRepository).save(captor.capture());
        assertEquals("Export CSV de 5 entrée(s) — journal complet", captor.getValue().getDescription());
        assertNull(captor.getValue().getActeur());
    }
}
