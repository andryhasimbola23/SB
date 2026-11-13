package com.auximad.gestion_immobilisation.integration;

import com.auximad.gestion_immobilisation.model.Agence;
import com.auximad.gestion_immobilisation.model.Categorie;
import com.auximad.gestion_immobilisation.model.Role;
import com.auximad.gestion_immobilisation.model.Service;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.repository.AgenceRepository;
import com.auximad.gestion_immobilisation.repository.CategorieRepository;
import com.auximad.gestion_immobilisation.repository.DemandeImmobilisationRepository;
import com.auximad.gestion_immobilisation.repository.ImmobilisationRepository;
import com.auximad.gestion_immobilisation.repository.ServiceRepository;
import com.auximad.gestion_immobilisation.repository.UtilisateurRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Socle commun des tests d'intégration API.
 *
 * <p>La suite tourne sur H2 en mémoire (profil {@code test}) : aucun test
 * n'écrit dans la base MySQL de développement. L'isolation est assurée par une
 * remise à zéro des tables avant chaque test plutôt que par une transaction
 * annulée : plusieurs scénarios vérifient un refus métier (400/409) <em>puis</em>
 * l'état de la base, et une transaction marquée rollback-only par le refus
 * ferait échouer les appels suivants.</p>
 *
 * <p>Chaque test démarre donc avec un référentiel minimal : deux agences, deux
 * services, une catégorie et quatre comptes (un ADMIN, un employé par service,
 * un employé sans service).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /** Mot de passe commun aux comptes du jeu de test. */
    protected static final String MOT_DE_PASSE = "Secret123!";

    /** Tables nettoyées avant chaque test (ordre indifférent : intégrité désactivée). */
    private static final String[] TABLES = {
            "journal_audit",
            "notification",
            "ligne_inventaire",
            "amortissement",
            "campagne_inventaire",
            "mouvement",
            "demande_immobilisation",
            "immobilisation",
            "compteur_codification",
            "utilisateur",
            "fournisseur",
            "categorie",
            "service",
            "agence"
    };

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected AgenceRepository agenceRepository;

    @Autowired
    protected ServiceRepository serviceRepository;

    @Autowired
    protected CategorieRepository categorieRepository;

    @Autowired
    protected UtilisateurRepository utilisateurRepository;

    @Autowired
    protected ImmobilisationRepository immobilisationRepository;

    @Autowired
    protected DemandeImmobilisationRepository demandeImmobilisationRepository;

    protected Agence agenceA;
    protected Agence agenceB;
    protected Service serviceA;
    protected Service serviceB;
    protected Categorie categorie;

    protected Utilisateur admin;
    protected Utilisateur employeA;
    protected Utilisateur employeB;
    protected Utilisateur employeSansService;

    @BeforeEach
    void preparerBaseEtReferentiel() {
        viderLaBase();

        agenceA = agenceRepository.save(Agence.builder()
                .codeAgence("AGTA")
                .nomAgence("Agence Test A")
                .villeAgence("Antananarivo")
                .actif(true)
                .build());
        agenceB = agenceRepository.save(Agence.builder()
                .codeAgence("AGTB")
                .nomAgence("Agence Test B")
                .villeAgence("Toamasina")
                .actif(true)
                .build());

        serviceA = serviceRepository.save(Service.builder()
                .code("SVA")
                .nomService("Service Test A")
                .agence(agenceA)
                .actif(true)
                .build());
        serviceB = serviceRepository.save(Service.builder()
                .code("SVB")
                .nomService("Service Test B")
                .agence(agenceB)
                .actif(true)
                .build());

        categorie = categorieRepository.save(Categorie.builder()
                .codeComptable("2183")
                .nomCategorie("Matériel informatique")
                .libelle("Matériel informatique de test")
                .typeImmobilisation("COR")
                .classeCompte(21)
                .tauxAmortissementParDefaut(new BigDecimal("20.00"))
                .dureeUtilisation(5)
                .actif(true)
                .build());

        admin = creerUtilisateur("admin.it", "admin.it@auximad.mg", "Admin", "Test",
                Role.ADMIN, null, null);
        employeA = creerUtilisateur("employe.a", "employe.a@auximad.mg", "Alice", "Employe",
                Role.EMPLOYE, serviceA, agenceA);
        employeB = creerUtilisateur("employe.b", "employe.b@auximad.mg", "Bob", "Employe",
                Role.EMPLOYE, serviceB, agenceB);
        employeSansService = creerUtilisateur("employe.sans", "employe.sans@auximad.mg", "Sans", "Service",
                Role.EMPLOYE, null, null);
    }

    private void viderLaBase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            for (String table : TABLES) {
                jdbcTemplate.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY");
            }
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    protected Utilisateur creerUtilisateur(String nomUtilisateur, String email, String prenom,
                                           String nom, Role role, Service service, Agence agence) {
        return utilisateurRepository.save(Utilisateur.builder()
                .nomUtilisateur(nomUtilisateur)
                .motDePasse(passwordEncoder.encode(MOT_DE_PASSE))
                .email(email)
                .nom(nom)
                .prenom(prenom)
                .role(role)
                .actif(true)
                .service(service)
                .agence(agence)
                .dateCreation(LocalDateTime.now())
                .build());
    }

    // ============================================================
    // Helpers HTTP
    // ============================================================

    protected String json(Object valeur) throws Exception {
        return objectMapper.writeValueAsString(valeur);
    }

    protected JsonNode corps(MvcResult resultat) throws Exception {
        return objectMapper.readTree(resultat.getResponse().getContentAsString());
    }

    protected ResultActions getJson(String url, String token) throws Exception {
        return mockMvc.perform(avecJeton(get(url), token));
    }

    protected ResultActions postJson(String url, String token, Object corps) throws Exception {
        return mockMvc.perform(avecJeton(post(url), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(corps)));
    }

    protected ResultActions putJson(String url, String token, Object corps) throws Exception {
        return mockMvc.perform(avecJeton(put(url), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(corps)));
    }

    protected ResultActions patchJson(String url, String token, Object corps) throws Exception {
        return mockMvc.perform(avecJeton(patch(url), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(corps)));
    }

    protected ResultActions deleteJson(String url, String token) throws Exception {
        return mockMvc.perform(avecJeton(delete(url), token));
    }

    private MockHttpServletRequestBuilder avecJeton(MockHttpServletRequestBuilder requete, String token) {
        return token == null ? requete : requete.header("Authorization", "Bearer " + token);
    }

    // ============================================================
    // Authentification des comptes du jeu de test
    // ============================================================

    protected String tokenDe(String nomUtilisateur) throws Exception {
        return tokenDe(nomUtilisateur, MOT_DE_PASSE);
    }

    protected String tokenDe(String nomUtilisateur, String motDePasse) throws Exception {
        MvcResult resultat = postJson("/api/auth/login", null,
                Map.of("nomUtilisateur", nomUtilisateur, "motDePasse", motDePasse))
                .andExpect(status().isOk())
                .andReturn();
        return corps(resultat).get("token").asText();
    }

    // ============================================================
    // Fabriques de données via l'API (parcours réel)
    // ============================================================

    protected JsonNode creerImmobilisation(String token, Service service, Agence agence,
                                            String designation) throws Exception {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("designation", designation);
        corps.put("dateAcquisition", "2024-01-15T00:00:00");
        corps.put("dateMiseService", "2024-02-01T00:00:00");
        corps.put("valeurAchat", 1500000);
        corps.put("etatPhysique", "BON");
        corps.put("marque", "Dell");
        corps.put("tauxAmortissement", 20);
        corps.put("typeMateriel", "UCB");
        corps.put("description", "Bien créé pour les tests d'intégration");
        corps.put("serviceId", service.getId());
        corps.put("agenceId", agence.getId());
        corps.put("categorieId", categorie.getId());

        MvcResult resultat = postJson("/api/immobilisations", token, corps)
                .andExpect(status().isCreated())
                .andReturn();
        return corps(resultat);
    }

    protected JsonNode creerDemande(String token, String designation) throws Exception {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("designation", designation);
        corps.put("motif", "Besoin de test pour " + designation);
        corps.put("quantite", 1);
        corps.put("priorite", "NORMALE");
        corps.put("categorieId", categorie.getId());

        MvcResult resultat = postJson("/api/demandes", token, corps)
                .andExpect(status().isCreated())
                .andReturn();
        return corps(resultat);
    }
}
