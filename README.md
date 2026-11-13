# Gestion des Immobilisations — AUXIMAD

Application de gestion du parc d'immobilisations d'AUXIMAD : suivi des biens
(achats, affectations, QR codes), mouvements, inventaires, circuit de demandes
d'immobilisation et **notifications temps réel** — de bout en bout, du dépôt de
demande par un employé jusqu'à la décision de l'administrateur, sans
actualisation de page.

## Fonctionnalités

**Parc & biens**
- Immobilisations : création, modification, détail, photo, amortissements, VNC
- **QR Codes** : génération, impression groupée d'étiquettes, scanner intégré
- Mouvements : entrée, transfert, affectation, cession, rebut (traçabilité complète)
- Inventaires : campagnes, constats, écarts
- Catégories comptables, services, agences, fournisseurs

**Circuit de demandes**
- Dépôt par l'employé → décision administrateur (approbation / rejet motivé)
- Lien à l'immobilisation candidat, livraison, notifications in-app

**Temps réel** (WebSocket STOMP)
- Cloche de notifications mise à jour à la seconde près (nouvelle demande,
  décision) sur **tous les navigateurs** de l'utilisateur
- Listes des demandes et du tableau de bord rechargées automatiquement
- Reconnexion automatique en cas de coupure réseau, filet de sécurité
  d'actualisation périodique conservé
- Le JWT voyage au handshake (`/ws?token=…`) ; chaque utilisateur ne reçoit
  que ses propres files (`/user/queue/*`)

**Administration**
- Rôles **ADMIN** / **EMPLOYE** avec périmètre par service (filtrage serveur)
- Journal d'audit des actions sensibles
- Exports Excel, tableau de bord statistiques

## Pile technique

| | |
|---|---|
| **Backend** | Java 17 · Spring Boot 3.4 (Security + JWT, JPA/Hibernate, WebSocket STOMP) · Maven · MySQL (dev) / H2 (tests) |
| **Frontend** | React 19 · Vite · Tailwind CSS · axios · `@stomp/stompjs` · lucide-react |
| **Tests** | 128 tests backend (JUnit 5, Mockito, MockMvc, tests d'intégration sur **socket réelle**) |

## Structure du dépôt

```
backend/    API REST Spring Boot (port 8080)
frontend/   SPA React (port 5173)
docs/       Diagrammes de classes (drawio) et rapport de tests API
```

## Prérequis

- JDK 17+ et Maven 3.9+
- Node.js 20+ et npm
- MySQL 8 pour le développement (la base `auximad_immo` est créée
  automatiquement au démarrage) — les tests n'utilisent pas MySQL (H2 en mémoire)

## Démarrage

### 1. Backend

```bash
cd backend
mvn spring-boot:run
# API : http://localhost:8080
```

Configuration dans `backend/src/main/resources/application.properties`
(hôte MySQL, secret JWT, expiration du jeton).

### 2. Frontend

```bash
cd frontend
npm install
cp .env.example .env     # VITE_API_URL=http://localhost:8080 en dev
npm run dev
# Application : http://localhost:5173
```

## Tests & qualité

```bash
cd backend && mvn test        # 128 tests — H2, aucun contact avec MySQL
cd frontend && npm run lint   # analyse statique
cd frontend && npm run build  # build de production
```

Les tests d'intégration temps réel (`SocketTempsReelIntegrationTest`)
démarrage un vrai serveur et ouvrent de vraies sockets STOMP pour vérifier le
handshake JWT, le routage par utilisateur et la livraison des messages.

## Sécurité

- Authentification JWT (filtre HTTP + validation au handshake WebSocket)
- Contrôle d'accès par rôle dans `SecurityConfig` ; le filtrage métier
  (périmètre par service) est appliqué côté serveur, jamais fait confiance au client
- Les événements temps réel sont publiés **après commit** transactionnel et
  n'conditionnent jamais la réussite d'une opération métier

## Documentation

- `docs/class.drawio`, `docs/class-admin.drawio` — diagrammes de classes
- `docs/rapport-tests-api.md` — rapport de tests de l'API
