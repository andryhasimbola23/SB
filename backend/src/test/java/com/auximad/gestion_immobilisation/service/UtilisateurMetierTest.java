package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.ProfilDto;
import com.auximad.gestion_immobilisation.dto.UtilisateurDto;
import com.auximad.gestion_immobilisation.model.Agence;
import com.auximad.gestion_immobilisation.model.Role;
import com.auximad.gestion_immobilisation.model.Service;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.repository.AgenceRepository;
import com.auximad.gestion_immobilisation.repository.CampagneInventaireRepository;
import com.auximad.gestion_immobilisation.repository.DemandeImmobilisationRepository;
import com.auximad.gestion_immobilisation.repository.MouvementRepository;
import com.auximad.gestion_immobilisation.repository.ServiceRepository;
import com.auximad.gestion_immobilisation.repository.UtilisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UtilisateurMetierTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private ServiceRepository serviceRepository;
    @Mock
    private AgenceRepository agenceRepository;
    @Mock
    private MouvementRepository mouvementRepository;
    @Mock
    private CampagneInventaireRepository campagneRepository;
    @Mock
    private DemandeImmobilisationRepository demandeRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JournalAuditMetier journalAuditMetier;

    @InjectMocks
    private UtilisateurMetier utilisateurMetier;

    private Utilisateur employe() {
        return Utilisateur.builder()
                .id(7L)
                .nomUtilisateur("tojo")
                .email("tojo@ucb.mg")
                .nom("Niaina")
                .prenom("Tojo")
                .telephone("0321112233")
                .role(Role.EMPLOYE)
                .actif(true)
                .dateCreation(LocalDateTime.of(2025, 1, 15, 8, 0))
                .service(Service.builder().id(2L).nomService("Informatique").build())
                .agence(Agence.builder().id(1L).nomAgence("Antananarivo").build())
                .build();
    }

    @Test
    @DisplayName("profilDe : la fiche du porteur porte les coordonnées complètes")
    void profilDeRenvoieLaFicheComplete() {
        UtilisateurDto dto = utilisateurMetier.profilDe(employe());

        assertEquals(7L, dto.getId());
        assertEquals("tojo@ucb.mg", dto.getEmail());
        assertEquals("0321112233", dto.getTelephone());
        assertEquals("Tojo Niaina", dto.getPrenom() + " " + dto.getNom());
        assertEquals("Informatique", dto.getServiceNom(), "le service rattaché est inclus");
        assertEquals("Antananarivo", dto.getAgenceNom(), "l'agence est incluse");
        assertNotNull(dto.getDateCreation(), "la date de création est incluse");
        assertNull(dto.getMotDePasse(), "le mot de passe n'est jamais exposé");
    }

    @Test
    @DisplayName("profilDe : sans authentification, refus explicite")
    void profilDeSansAuthentification() {
        assertThrows(IllegalArgumentException.class, () -> utilisateurMetier.profilDe(null));
    }

    @Test
    @DisplayName("mettreAJourProfil : email, téléphone et photo sont appliqués ET journalisés")
    void mettreAJourProfilAppliqueEtTrace() {
        Utilisateur utilisateur = employe();
        when(utilisateurRepository.existsByEmail("nouveau@ucb.mg")).thenReturn(false);
        when(utilisateurRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProfilDto dto = new ProfilDto();
        dto.setEmail("nouveau@ucb.mg");
        dto.setTelephone("0344445566");
        dto.setPhoto("/uploads/users/user_7.png");

        UtilisateurDto resultat = utilisateurMetier.mettreAJourProfil(utilisateur, dto);

        assertEquals("nouveau@ucb.mg", resultat.getEmail());
        assertEquals("0344445566", resultat.getTelephone());
        assertEquals("/uploads/users/user_7.png", resultat.getPhoto());
        verify(utilisateurRepository).save(utilisateur);

        ArgumentCaptor<String> desc = ArgumentCaptor.forClass(String.class);
        verify(journalAuditMetier).enregistrer(
                eq(utilisateur), eq(JournalAuditMetier.MODIFICATION),
                eq(JournalAuditMetier.ENTITE_UTILISATEUR), eq(7L), desc.capture());
        assertEquals("Coordonnées du compte mises à jour (email, téléphone, photo)", desc.getValue());
    }

    @Test
    @DisplayName("mettreAJourProfil : un email déjà pris par un autre compte est refusé")
    void mettreAJourProfilRefuseEmailUtilise() {
        Utilisateur utilisateur = employe();
        when(utilisateurRepository.existsByEmail("pris@ucb.mg")).thenReturn(true);

        ProfilDto dto = new ProfilDto();
        dto.setEmail("pris@ucb.mg");

        assertThrows(IllegalArgumentException.class,
                () -> utilisateurMetier.mettreAJourProfil(utilisateur, dto));
        verify(utilisateurRepository, never()).save(any());
        verifyNoInteractions(journalAuditMetier);
    }

    @Test
    @DisplayName("mettreAJourProfil : photo seule (email non transmis) — aucune trace inutile")
    void mettreAJourProfilPhotoSeule() {
        Utilisateur utilisateur = employe();
        when(utilisateurRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProfilDto dto = new ProfilDto();
        dto.setPhoto("/uploads/users/user_7_v2.png");

        UtilisateurDto resultat = utilisateurMetier.mettreAJourProfil(utilisateur, dto);

        assertEquals("tojo@ucb.mg", resultat.getEmail(), "l'email non transmis reste intact");
        assertEquals("/uploads/users/user_7_v2.png", resultat.getPhoto());

        ArgumentCaptor<String> desc = ArgumentCaptor.forClass(String.class);
        verify(journalAuditMetier).enregistrer(
                any(), eq(JournalAuditMetier.MODIFICATION),
                eq(JournalAuditMetier.ENTITE_UTILISATEUR), eq(7L), desc.capture());
        assertEquals("Coordonnées du compte mises à jour (photo)", desc.getValue());
    }

    @Test
    @DisplayName("mettreAJourProfil : aucune modification réelle — pas de trace")
    void mettreAJourProfilSansChangement() {
        Utilisateur utilisateur = employe();

        ProfilDto dto = new ProfilDto();
        dto.setEmail("  TOJO@ucb.mg  "); // même email, casse différente : pas un changement
        dto.setTelephone("0321112233");

        utilisateurMetier.mettreAJourProfil(utilisateur, dto);

        verify(utilisateurRepository, never()).save(any());
        verifyNoInteractions(journalAuditMetier);
    }
}
