# Rapport de tests — parcours complet API AUXIMAD

Date : 2 novembre 2026
Périmètre : option 1 du plan (`plan-tests-parcours-complet.md`) — profil de test,
tests d'intégration API et script E2E du parcours complet.

## Résultat

```text
mvn test  →  BUILD SUCCESS
107 tests, 0 échec, 0 erreur, 0 ignoré
```

Les 37 tests unitaires préexistants restent verts. Les 70 nouveaux tests
d'intégration s'ajoutent sans modifier la base MySQL de développement.

## Isolation des tests

- Profil Spring `test` activé par `@ActiveProfiles("test")`.
- Base H2 en mémoire `jdbc:h2:mem:auximad_test;MODE=MySQL`, `ddl-auto=create-drop`.
- Aucun accès à `auximad_immo` : le profil de test remplace la datasource et le
  dossier d'upload (`target/test-uploads`).
- Nettoyage avant chaque test par `SET REFERENTIAL_INTEGRITY FALSE` +
  `TRUNCATE ... RESTART IDENTITY`, puis seed programmatique.
- Ce choix (plutôt qu'une transaction annulée) est nécessaire parce que
  plusieurs scénarios vérifient un refus (400/409) **puis** l'état de la base :
  une transaction marquée rollback-only ferait échouer les appels suivants.

## Couverture ajoutée

| Fichier | Bloc | Points couverts |
|---|---|---|
| `AuthIntegrationTest` | B | login admin/employé, mauvais mot de passe, compte inconnu/désactivé, corps vide, `/me` valide/sans/malformé/expiré, 401 anonyme, 403 employé |
| `UtilisateurIntegrationTest` | A | création, doublons username/email, validations, email/mot de passe invalides, rôle inconnu, service inexistant, 403/401, recherche, mise à jour sans écrasement du mot de passe, suppressions protégées |
| `ImmobilisationIntegrationTest` | D | création, format et séquence du code, validations, 403/401, périmètre, QR et fichier réel, réservation du QR à l'ADMIN, amortissement, mise à jour, suppression |
| `DemandeWorkflowIntegrationTest` | E | dépôt, validation, périmètre, notifications, refus/approbation, TRANSFERT/AFFECTATION, bien déjà livré, bien non livrable, employé sans service, annulation, re-décision, audit, candidats |
| `SecuritePermissionsIntegrationTest` | F | matrice 401 anonyme, 403 employé sur écritures/lectures ADMIN, 404 inter-services, notification d'autrui 404, préflight CORS, absence de mot de passe dans les réponses |

Total : **70 tests d'intégration** (13 + 14 + 15 + 20 + 8).

## Script E2E

`backend/test-parcours-complet.sh` rejoue le parcours par l'API :

1. connexion ADMIN de bootstrap (`Tojo`/`admin123` par défaut, surchargeable par
   `ADMIN_USER`/`ADMIN_PASS`) ;
2. création d'une agence, de deux services, d'une catégorie et d'un employé
   uniques (suffixe horodaté, rejouable sur une base peuplée) ;
3. connexion employé et validation `/me` ;
4. création d'un bien dans le service source, génération du QR ;
5. dépôt d'une demande, notification ADMIN, invisibilité pour un autre employé ;
6. approbation avec livraison réelle (TRANSFERT), code réémis, QR invalidé ;
7. vérification du mouvement, du journal d'audit et des notifications ;
8. tableau de bord et exports Excel/PDF ;
9. campagne d'inventaire : scan, clôture, refus de scan après clôture, export.

Le script se termine sur un bilan `OK / FAIL / SKIP` et un code de sortie non nul
en cas d'échec. Il n'a **pas été exécuté** : il écrit dans la base réelle, ce que
la consigne interdisait. Sa syntaxe a été vérifiée avec `bash -n`.

## Anomalies et points d'attention

| Constat | Statut |
|---|---|
| Les anonymes reçoivent **401** (et non 403) grâce à `HttpStatusEntryPoint` | Comportement voulu ; couvert par les tests. Les anciens scripts `test-demandes.sh` attendent encore 403. |
| Le filtre JWT ne vérifie pas `isEnabled()` : un jeton émis avant désactivation reste valide | **Anomalie ouverte**, non corrigée dans ce lot (le login d'un compte désactivé est bien refusé). |
| `POST /api/immobilisations/{id}/generer-qr` est réservé à l'ADMIN | Comportement réel ; test aligné sur 403 pour un employé. |
| `GET /api/mouvements/immobilisation/{id}` hors périmètre renvoie `200 []` et non 404 | Pas de fuite d'existence ; test aligné sur cette conception. |
| `POST /api/utilisateurs/{id}/change-password` exige l'ancien mot de passe de la cible | Pas de reset admin ; à arbitrer si le besoin existe. |
| Pas d'inscription publique : la création de compte passe par l'ADMIN | Conforme au parcours testé. |
| Dépôt Git sans commit | Toutes les modifications sont non commitées ; à relire avant commit. |
