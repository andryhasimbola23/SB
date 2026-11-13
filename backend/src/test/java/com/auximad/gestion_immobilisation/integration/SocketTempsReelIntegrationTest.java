package com.auximad.gestion_immobilisation.integration;

import com.auximad.gestion_immobilisation.model.Agence;
import com.auximad.gestion_immobilisation.model.Categorie;
import com.auximad.gestion_immobilisation.model.Role;
import com.auximad.gestion_immobilisation.model.Service;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.repository.AgenceRepository;
import com.auximad.gestion_immobilisation.repository.CategorieRepository;
import com.auximad.gestion_immobilisation.repository.ServiceRepository;
import com.auximad.gestion_immobilisation.repository.UtilisateurRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Temps réel sur socket <strong>réelle</strong> : le serveur tourne sur un vrai
 * port ({@code RANDOM_PORT}), deux clients STOMP s'y connectent avec leur JWT
 * et reçoivent les événements poussés par des appels HTTP authenticés.
 *
 * <p>Ce que ce test couvre, que {@link TempsReelIntegrationTest} (template
 * mocké) ne peut pas :</p>
 * <ul>
 *   <li>le handshake complet — interceptor JWT, principal posé par
 *       {@code JwtHandshakeHandler}, upgrade Tomcat ;</li>
 *   <li>la résolution {@code /user/queue/*} contre ce principal (le serveur
 *       doit retrouver l'id de l'utilisateur) ;</li>
 *   <li>le broker simple, la conversion JSON des DTO et la livraison réelle
 *       sur la socket ;</li>
 *   <li>le refus du handshake sans jeton, observé côté client.</li>
 * </ul>
 *
 * <p>Le REST passe aussi par le vrai port (HTTP 1.1) : le scénario est de bout
 * en bout. Profil {@code test} (H2) — aucun contact avec MySQL.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SocketTempsReelIntegrationTest {

    /** Tables nettoyées avant chaque test (copie du socle : même base partagée). */
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

    private static final String MOT_DE_PASSE = "Secret123!";

    /** Délai laissé au broker pour enregistrer les abonnements (pas d'accusé de réception côté broker simple). */
    private static final long DELAI_ABONNEMENT_MS = 500;
    private static final long DELAI_ATTENTE_S = 10;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AgenceRepository agenceRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private CategorieRepository categorieRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private HttpClient http;
    private WebSocketStompClient stomp;
    private final List<StompSession> sessions = new ArrayList<>();

    private Utilisateur admin;
    private Utilisateur employeA;
    private Utilisateur employeB;
    private Categorie categorie;

    private String tokenAdmin;
    private String tokenA;

    @BeforeEach
    void preparer() throws Exception {
        viderLaBase();

        Agence agence = agenceRepository.save(Agence.builder()
                .codeAgence("AGTS").nomAgence("Agence Socket").villeAgence("Antananarivo")
                .actif(true)
                .build());
        Service service = serviceRepository.save(Service.builder()
                .code("SVS").nomService("Service Socket").agence(agence).actif(true).build());
        categorie = categorieRepository.save(Categorie.builder()
                .codeComptable("2183").nomCategorie("Matériel informatique")
                .libelle("Matériel informatique socket").typeImmobilisation("COR")
                .classeCompte(21).tauxAmortissementParDefaut(new BigDecimal("20.00"))
                .dureeUtilisation(5).actif(true).build());

        admin = creerUtilisateur("admin.sock", "admin.sock@auximad.mg", "Admin", "Socket",
                Role.ADMIN, null, null);
        employeA = creerUtilisateur("employe.sock.a", "a.sock@auximad.mg", "Alice", "Socket",
                Role.EMPLOYE, service, agence);
        employeB = creerUtilisateur("employe.sock.b", "b.sock@auximad.mg", "Bob", "Socket",
                Role.EMPLOYE, service, agence);

        http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new MappingJackson2MessageConverter(objectMapper));

        tokenAdmin = tokenDe(admin.getNomUtilisateur());
        tokenA = tokenDe(employeA.getNomUtilisateur());
    }

    @AfterEach
    void fermerLesSockets() {
        for (StompSession session : sessions) {
            try {
                session.disconnect();
            } catch (Exception ignore) {
                // Une session déjà tombée n'a rien à fermer.
            }
        }
        sessions.clear();
    }

    /** Handler de session minimal : aucun callback particulier (frames d'erreur ignorées). */
    private static class HandlerDeSession extends StompSessionHandlerAdapter {
    }

    // ============================================================
    // Scénarios de bout en bout
    // ============================================================

    @Test
    @DisplayName("Dépôt : l'admin reçoit notification + recharge, le demandeur recharge, le témoin rien")
    void depotVisibleSurLesSocketsReelles() throws Exception {
        Client adminClient = connecte(tokenAdmin);
        Client employeClient = connecte(tokenA);
        Client temoin = connecte(tokenDe(employeB.getNomUtilisateur()));

        JsonNode demande = creerDemande(tokenA, "Ordinateur portable pour le temps réel");

        // L'admin : la notification de cloche (avec compteur exact)…
        JsonNode notification = attendre(adminClient.notifications, "notification admin");
        assertEquals("NOUVELLE_DEMANDE", notification.get("notification").get("type").asText());
        assertTrue(notification.get("nonLues").asLong() >= 1,
                "le compteur de non-lues doit être celui du serveur");

        // …et l'événement de recharge de sa liste.
        JsonNode evenement = attendre(adminClient.demandes, "événement admin");
        assertEquals("NOUVELLE_DEMANDE", evenement.get("evenement").asText());
        assertEquals(demande.get("id").asLong(), evenement.get("demandeId").asLong());

        // Le demandeur voit sa demande apparaître sur chacune de ses sessions.
        JsonNode evenementEmploye = attendre(employeClient.demandes, "événement demandeur");
        assertEquals(demande.get("id").asLong(), evenementEmploye.get("demandeId").asLong());

        // Un employé hors demande ni destinataire ne reçoit strictement rien.
        assertNull(temoin.notifications.poll(1, TimeUnit.SECONDS),
                "le témoin ne doit recevoir aucune notification");
        assertNull(temoin.demandes.poll(1, TimeUnit.SECONDS),
                "le témoin ne doit recevoir aucun événement de demande");
    }

    @Test
    @DisplayName("Rejet : seul le demandeur est notifié sur la socket, les deux côtés rechargent")
    void rejetVisibleSurLesSocketsReelles() throws Exception {
        JsonNode demande = creerDemande(tokenA, "Demande à rejeter sur socket");
        long demandeId = demande.get("id").asLong();

        Client adminClient = connecte(tokenAdmin);
        Client employeClient = connecte(tokenA);
        Client temoin = connecte(tokenDe(employeB.getNomUtilisateur()));

        decider(demandeId, tokenAdmin, "REJETTEE", "Budget insuffisant cette année");

        // Le seul notifié est le demandeur (périmètre existant respecté).
        JsonNode notification = attendre(employeClient.notifications, "notification de rejet");
        assertEquals("DEMANDE_REJETEE", notification.get("notification").get("type").asText());

        // Le demandeur recharge son statut (Acceptée/Rejetée) sans F5…
        JsonNode evenement = attendre(employeClient.demandes, "événement du demandeur");
        assertEquals("DEMANDE_DECIDEE", evenement.get("evenement").asText());
        assertEquals(demandeId, evenement.get("demandeId").asLong());

        // …l'admin aussi, mais jamais la notification de sa propre décision.
        JsonNode evenementAdmin = attendre(adminClient.demandes, "événement de l'admin");
        assertEquals("DEMANDE_DECIDEE", evenementAdmin.get("evenement").asText());
        assertNull(adminClient.notifications.poll(1, TimeUnit.SECONDS),
                "l'admin qui décide ne notifie pas lui-même");

        // L'employé B n'a rien à voir dans cette décision.
        assertNull(temoin.notifications.poll(1, TimeUnit.SECONDS), "aucune notification au témoin");
        assertNull(temoin.demandes.poll(1, TimeUnit.SECONDS), "aucun événement au témoin");
    }

    @Test
    @DisplayName("Annulation : événements de liste sur les sockets, sans notification")
    void annulationVisibleSurLesSocketsReelles() throws Exception {
        JsonNode demande = creerDemande(tokenA, "Demande à annuler sur socket");
        long demandeId = demande.get("id").asLong();

        Client adminClient = connecte(tokenAdmin);
        Client employeClient = connecte(tokenA);

        annuler(demandeId, tokenA);

        JsonNode evenementAdmin = attendre(adminClient.demandes, "événement de l'admin");
        assertEquals("DEMANDE_ANNULEE", evenementAdmin.get("evenement").asText());
        assertEquals(demandeId, evenementAdmin.get("demandeId").asLong());

        JsonNode evenementAuteur = attendre(employeClient.demandes, "événement de l'auteur");
        assertEquals("DEMANDE_ANNULEE", evenementAuteur.get("evenement").asText());

        // Une annulation n'est pas une décision : aucune notification poussée.
        assertNull(adminClient.notifications.poll(1, TimeUnit.SECONDS),
                "l'annulation ne notifie personne");
        assertNull(employeClient.notifications.poll(1, TimeUnit.SECONDS),
                "l'auteur n'est pas notifié de sa propre annulation");
    }

    @Test
    @DisplayName("Handshake : sans jeton, le client échoue — aucune session STOMP n'ouvre")
    void handshakeSansJetonRefuseSurSocketReelle() {
        CompletableFuture<StompSession> tentative = stomp.connectAsync(
                "ws://localhost:" + port + "/ws", new HandlerDeSession());

        ExecutionException echec = assertThrows(ExecutionException.class,
                () -> tentative.get(DELAI_ATTENTE_S, TimeUnit.SECONDS),
                "un handshake sans jeton doit être refusé par le serveur");
        assertNotNull(echec.getCause(), "le refus doit provenir du serveur (401), pas d'un timeout");
    }

    @Test
    @DisplayName("Handshake : un jeton valide ouvre la session et les abonnements vivent")
    void handshakeAvecJetonOuvreLaSession() throws Exception {
        Client client = connecte(tokenAdmin);

        assertTrue(client.session.isConnected(), "la session STOMP doit être connectée");

        // Preuve que /user/queue/* est résolu : un événement réel arrive.
        creerDemande(tokenA, "Preuve d'abonnement actif");
        JsonNode notification = attendre(client.notifications, "notification après abonnement");
        assertEquals("NOUVELLE_DEMANDE", notification.get("notification").get("type").asText());
    }

    // ============================================================
    // Client STOMP
    // ============================================================

    /** Un client connecté, avec sa file de messages par canal. */
    private record Client(StompSession session, BlockingQueue<JsonNode> notifications,
                          BlockingQueue<JsonNode> demandes) {
    }

    private Client connecte(String token) throws Exception {
        String url = "ws://localhost:" + port + "/ws?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        BlockingQueue<JsonNode> notifications = new LinkedBlockingQueue<>();
        BlockingQueue<JsonNode> demandes = new LinkedBlockingQueue<>();

        StompSession session = stomp
                .connectAsync(url, new HandlerDeSession())
                .get(DELAI_ATTENTE_S, TimeUnit.SECONDS);
        sessions.add(session);

        session.subscribe("/user/queue/notifications", collecteur(notifications));
        session.subscribe("/user/queue/demandes", collecteur(demandes));

        // Le broker simple n'émet pas d'accusé de réception pour un SUBSCRIBE :
        // un court délai couvre l'enregistrement avant les actions REST.
        Thread.sleep(DELAI_ABONNEMENT_MS);
        return new Client(session, notifications, demandes);
    }

    private StompFrameHandler collecteur(BlockingQueue<JsonNode> file) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof JsonNode node) {
                    file.add(node);
                }
            }
        };
    }

    private JsonNode attendre(BlockingQueue<JsonNode> file, String libelle) throws InterruptedException {
        JsonNode message = file.poll(DELAI_ATTENTE_S, TimeUnit.SECONDS);
        assertNotNull(message, "aucun message temps réel reçu : " + libelle);
        return message;
    }

    // ============================================================
    // REST sur le vrai port
    // ============================================================

    private String tokenDe(String nomUtilisateur) throws Exception {
        Map<String, Object> corps = Map.of(
                "nomUtilisateur", nomUtilisateur,
                "motDePasse", MOT_DE_PASSE);
        return reponse("/api/auth/login", "POST", null, corps, 200).get("token").asText();
    }

    private JsonNode creerDemande(String token, String designation) throws Exception {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("designation", designation);
        corps.put("motif", "Besoin de test pour " + designation);
        corps.put("quantite", 1);
        corps.put("priorite", "NORMALE");
        corps.put("categorieId", categorie.getId());
        return reponse("/api/demandes", "POST", token, corps, 201);
    }

    private void decider(long demandeId, String token, String decision, String commentaire) throws Exception {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("decision", decision);
        corps.put("commentaire", commentaire);
        reponse("/api/demandes/" + demandeId + "/decision", "PATCH", token, corps, 200);
    }

    private void annuler(long demandeId, String token) throws Exception {
        reponse("/api/demandes/" + demandeId + "/annuler", "PATCH", token, Map.of(), 200);
    }

    private JsonNode reponse(String chemin, String methode, String token, Object corps, int statutAttendu)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + chemin))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        String corpsJson = objectMapper.writeValueAsString(corps);
        builder.method(methode, HttpRequest.BodyPublishers.ofString(corpsJson));

        HttpResponse<String> reponse = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(statutAttendu, reponse.statusCode(),
                "statut inattendu sur " + methode + " " + chemin + " : " + reponse.body());
        return objectMapper.readTree(reponse.body());
    }

    // ============================================================
    // Jeu de données (miroir du socle d'intégration)
    // ============================================================

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

    private Utilisateur creerUtilisateur(String nomUtilisateur, String email, String prenom,
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
}
