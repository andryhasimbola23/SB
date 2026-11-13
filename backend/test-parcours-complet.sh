#!/usr/bin/env bash
# ============================================================
# Parcours complet AUXIMAD — test E2E de bout en bout par l'API
# ============================================================
#
# Ce script rejoue le parcours réel d'un utilisateur, sans interface :
#
#   1. connexion ADMIN (bootstrap)
#   2. création d'un référentiel unique (agence, services, catégorie)
#   3. création d'un compte employé, connexion, validation de session
#   4. création d'un bien dans un AUTRE service, génération du QR
#   5. dépôt d'une demande par l'employé, notification de l'ADMIN
#   6. approbation avec livraison réelle (TRANSFERT)
#   7. traçabilité : mouvement, bien réaffecté, QR invalidé, journal d'audit
#   8. tableau de bord et exports (Excel/PDF)
#   9. campagne d'inventaire : scan, clôture, export
#
# Prérequis : backend démarré sur http://localhost:8080 et un compte ADMIN
# existant. Par défaut : Tojo / admin123.
#
#   ADMIN_USER=mon.admin ADMIN_PASS=secret ./test-parcours-complet.sh
#   API=http://localhost:8080/api ./test-parcours-complet.sh
#
# IMPORTANT encodage : sous Git Bash/Windows, les accents passés directement
# dans la ligne de commande sont transmis dans le codepage OEM et corrompent
# le JSON. Les payloads de ce script sont donc écrits sans accent.
#
# Les données créées portent un suffixe unique : le script peut être rejoué
# sur une base déjà peuplée. Les données restent en place à la fin, pour
# pouvoir inspecter le parcours dans l'interface.
set -u

API=${API:-http://localhost:8080/api}
ADMIN_USER=${ADMIN_USER:-Tojo}
ADMIN_PASS=${ADMIN_PASS:-admin123}
MOT_DE_PASSE_EMPLOYE=${MOT_DE_PASSE_EMPLOYE:-Test123!}

# Git Bash décode la sortie de curl selon la console (cp1252) : forcer UTF-8
# côté Python, sinon les accents lus depuis le JSON sont mojibakés.
export PYTHONIOENCODING=utf-8
export PYTHONUTF8=1

PASS=0; FAIL=0; SKIP=0
check() { if [ "$1" = "$2" ]; then PASS=$((PASS+1)); echo "  OK   $3  ($1)";
           else FAIL=$((FAIL+1)); echo "  FAIL $3  (obtenu «$1», attendu «$2»)"; fi; }
check_ne() { if [ "$1" != "$2" ]; then PASS=$((PASS+1)); echo "  OK   $3  ($1 ≠ $2)";
              else FAIL=$((FAIL+1)); echo "  FAIL $3  (identique «$1»)"; fi; }
check_ge() { if [ "$1" -ge "$2" ] 2>/dev/null; then PASS=$((PASS+1)); echo "  OK   $3  ($1 >= $2)";
              else FAIL=$((FAIL+1)); echo "  FAIL $3  (obtenu «$1», attendu >= «$2»)"; fi; }
skip() { SKIP=$((SKIP+1)); echo "  SKIP $1"; }

J()  { curl -s "$@"; }
A()  { curl -s -o /dev/null -w "%{http_code}" "$@"; }
val(){ python -c "import sys,json;v=json.load(sys.stdin).get('$1','');print('' if v is None else v)" 2>/dev/null; }
cnt(){ python -c "import sys,json;d=json.load(sys.stdin);print(len(d) if isinstance(d,list) else -1)" 2>/dev/null; }
has(){ python -c "
import sys,json
d=json.load(sys.stdin)
print('YES' if any(str(x.get('$1'))=='$2' for x in d) else 'NO')" 2>/dev/null; }

# Télécharge une réponse binaire et renvoie le code HTTP.
dl() { curl -s -o "$2" -w "%{http_code}" -H "Authorization: Bearer $TOKEN_ADMIN" "$1"; }
taille() { if [ -f "$1" ]; then wc -c < "$1" | tr -d ' '; else echo 0; fi; }

SUF="$(date +%H%M%S)$((RANDOM % 10))"
CODE_AGENCE="AE$SUF"
CODE_SERVICE_DEST="D$SUF"
CODE_SERVICE_SRC="S$SUF"
CODE_CATEGORIE="E$SUF"
NOM_EMPLOYE="e2e$SUF"
EMAIL_EMPLOYE="e2e$SUF@auximad.mg"
DATE_JOUR=$(date +%Y-%m-%d)

echo "=== Parcours complet AUXIMAD (suffixe $SUF) ==="
echo "  API : $API"

# ============================================================
# 0. Bootstrap : connexion de l'ADMIN existant
# ============================================================
echo
echo "=== 0. Connexion ADMIN ==="
REP=$(J -X POST "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"nomUtilisateur\":\"$ADMIN_USER\",\"motDePasse\":\"$ADMIN_PASS\"}")
TOKEN_ADMIN=$(echo "$REP" | val token)
if [ -z "$TOKEN_ADMIN" ]; then
  echo "  ÉCHEC : impossible de se connecter avec $ADMIN_USER."
  echo "  Vérifiez que le backend tourne et que ADMIN_USER/ADMIN_PASS sont corrects."
  exit 1
fi
echo "  admin connecté : $(echo "$REP" | val nomComplet) ($(echo "$REP" | val role))"
check "$(echo "$REP" | val role)" "ADMIN" "le compte de bootstrap est ADMIN"

AH=(-H "Authorization: Bearer $TOKEN_ADMIN" -H 'Content-Type: application/json')

# ============================================================
# 1. Référentiel unique
# ============================================================
echo
echo "=== 1. Référentiel (agence, services, catégorie) ==="
R=$(J -X POST "$API/agences" "${AH[@]}" \
  -d "{\"codeAgence\":\"$CODE_AGENCE\",\"nomAgence\":\"Agence E2E $SUF\",\"villeAgence\":\"Antananarivo\"}")
ID_AGENCE=$(echo "$R" | val id)
check_ne "$ID_AGENCE" "" "agence créée ($CODE_AGENCE)"

R=$(J -X POST "$API/services" "${AH[@]}" \
  -d "{\"code\":\"$CODE_SERVICE_DEST\",\"nomService\":\"Service destination $SUF\",\"agenceId\":$ID_AGENCE}")
ID_SERVICE_DEST=$(echo "$R" | val id)
check_ne "$ID_SERVICE_DEST" "" "service de destination créé ($CODE_SERVICE_DEST)"

R=$(J -X POST "$API/services" "${AH[@]}" \
  -d "{\"code\":\"$CODE_SERVICE_SRC\",\"nomService\":\"Service source $SUF\",\"agenceId\":$ID_AGENCE}")
ID_SERVICE_SRC=$(echo "$R" | val id)
check_ne "$ID_SERVICE_SRC" "" "service source créé ($CODE_SERVICE_SRC)"

R=$(J -X POST "$API/categories" "${AH[@]}" \
  -d "{\"codeComptable\":\"$CODE_CATEGORIE\",\"nomCategorie\":\"Materiel E2E $SUF\",\"typeImmobilisation\":\"COR\",\"classeCompte\":21,\"tauxAmortissementParDefaut\":20,\"dureeUtilisation\":5}")
ID_CATEGORIE=$(echo "$R" | val id)
check_ne "$ID_CATEGORIE" "" "catégorie créée ($CODE_CATEGORIE)"

# ============================================================
# 2. Compte employé, connexion, session
# ============================================================
echo
echo "=== 2. Compte employé et session ==="
R=$(J -X POST "$API/utilisateurs" "${AH[@]}" \
  -d "{\"nomUtilisateur\":\"$NOM_EMPLOYE\",\"motDePasse\":\"$MOT_DE_PASSE_EMPLOYE\",\"email\":\"$EMAIL_EMPLOYE\",\"nom\":\"Test\",\"prenom\":\"E2E\",\"role\":\"EMPLOYE\",\"actif\":true,\"serviceId\":$ID_SERVICE_DEST,\"agenceId\":$ID_AGENCE}")
ID_EMPLOYE=$(echo "$R" | val id)
check_ne "$ID_EMPLOYE" "" "compte employé créé ($NOM_EMPLOYE)"
check "$(echo "$R" | val motDePasse)" "" "aucun mot de passe renvoyé à la création"

REP=$(J -X POST "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"nomUtilisateur\":\"$NOM_EMPLOYE\",\"motDePasse\":\"$MOT_DE_PASSE_EMPLOYE\"}")
TOKEN_EMP=$(echo "$REP" | val token)
EH=(-H "Authorization: Bearer $TOKEN_EMP" -H 'Content-Type: application/json')
check_ne "$TOKEN_EMP" "" "connexion de l'employé"
check "$(echo "$REP" | val role)" "EMPLOYE" "rôle EMPLOYE"
check "$(echo "$REP" | val serviceId)" "$ID_SERVICE_DEST" "périmètre = service de destination"

ME=$(J "${EH[@]}" "$API/auth/me")
check "$(echo "$ME" | val nomUtilisateur)" "$NOM_EMPLOYE" "/me confirme le porteur du jeton"
check "$(echo "$ME" | val token)" "$TOKEN_EMP" "/me renvoie le jeton présenté"

check "$(A "${EH[@]}" "$API/utilisateurs")" "403" "un EMPLOYE ne liste pas les comptes"
check "$(A "$API/immobilisations")" "401" "sans jeton : 401 (et non 403)"

# ============================================================
# 3. Bien dans le service source + QR
# ============================================================
echo
echo "=== 3. Création du bien et QR ==="
R=$(J -X POST "$API/immobilisations" "${AH[@]}" \
  -d "{\"designation\":\"Ordinateur portable E2E $SUF\",\"dateAcquisition\":\"2024-01-15T00:00:00\",\"dateMiseService\":\"2024-02-01T00:00:00\",\"valeurAchat\":1500000,\"etatPhysique\":\"BON\",\"marque\":\"Dell\",\"tauxAmortissement\":20,\"typeMateriel\":\"UCB\",\"serviceId\":$ID_SERVICE_SRC,\"agenceId\":$ID_AGENCE,\"categorieId\":$ID_CATEGORIE}")
ID_BIEN=$(echo "$R" | val id)
CODE_BIEN=$(echo "$R" | val codeUnique)
check_ne "$ID_BIEN" "" "bien créé"
check "$(echo "$R" | val statut)" "ACTIF" "statut initial ACTIF"
check "$(echo "$R" | val serviceId)" "$ID_SERVICE_SRC" "bien rattaché au service source"
python -c "
import re,sys
print('YES' if re.match(r'^[A-Z0-9]+/[0-9]{2}/[A-Z0-9]+/[A-Z0-9]+/[A-Z0-9_]+/[0-9]{3}$', '$CODE_BIEN') else 'NO')" > /tmp/_code_e2e
check "$(cat /tmp/_code_e2e)" "YES" "code unique au format agence/annee/service/categorie/type/numero ($CODE_BIEN)"

R=$(J -X POST "$API/immobilisations/$ID_BIEN/generer-qr" "${AH[@]}")
QR_AVANT=$(echo "$R" | val qrCode)
check_ne "$QR_AVANT" "" "QR code généré"
check "$(A "${EH[@]}" -X POST "$API/immobilisations/$ID_BIEN/generer-qr")" "403" "génération de QR réservée à l'ADMIN"

# Le bien est hors du périmètre de l'employé tant qu'il n'est pas livré.
check "$(J "${EH[@]}" "$API/immobilisations/$ID_BIEN" | val codeUnique)" "" "bien hors périmètre invisible avant livraison"

# ============================================================
# 4. Dépôt de la demande
# ============================================================
echo
echo "=== 4. Dépôt de la demande ==="
NOTIF_AVANT=$(J "${AH[@]}" "$API/notifications" | cnt)
R=$(J -X POST "$API/demandes" "${EH[@]}" \
  -d "{\"designation\":\"Ordinateur portable pour E2E\",\"motif\":\"Besoin de service pour le parcours complet\",\"quantite\":1,\"priorite\":\"URGENTE\",\"categorieId\":$ID_CATEGORIE}")
ID_DEMANDE=$(echo "$R" | val id)
REF_DEMANDE=$(echo "$R" | val reference)
check_ne "$ID_DEMANDE" "" "demande déposée"
check "$(echo "$R" | val statut)" "EN_ATTENTE" "statut initial EN_ATTENTE"
check "$(echo "$R" | val serviceId)" "$ID_SERVICE_DEST" "service figé au service du demandeur"
check "$(echo "$R" | val priorite)" "URGENTE" "priorité conservée"
python -c "
import re
print('YES' if re.match(r'^DEM-[0-9]{4}-[0-9]{4,}$', '$REF_DEMANDE') else 'NO')" > /tmp/_ref_e2e
check "$(cat /tmp/_ref_e2e)" "YES" "référence de suivi calculée ($REF_DEMANDE)"

check "$(A "${EH[@]}" -X PATCH "$API/demandes/$ID_DEMANDE/decision" -d '{"decision":"APPROUVEE"}')" "403" "un EMPLOYE ne décide pas"
NOTIF_APRES=$(J "${AH[@]}" "$API/notifications" | cnt)
check_ge "$NOTIF_APRES" "$((NOTIF_AVANT + 1))" "l'ADMIN est notifié du dépôt"

# La demande reste privée : un autre employé ne la voit pas. On réutilise ici
# le compte admin (qui voit tout) pour vérifier l'existence, puis on crée un
# second employé dans un autre service.
NOM_AUTRE="e2eautre$SUF"
J -X POST "$API/utilisateurs" "${AH[@]}" \
  -d "{\"nomUtilisateur\":\"$NOM_AUTRE\",\"motDePasse\":\"$MOT_DE_PASSE_EMPLOYE\",\"email\":\"$NOM_AUTRE@auximad.mg\",\"nom\":\"Autre\",\"prenom\":\"E2E\",\"role\":\"EMPLOYE\",\"actif\":true,\"serviceId\":$ID_SERVICE_SRC,\"agenceId\":$ID_AGENCE}" > /dev/null
TOKEN_AUTRE=$(J -X POST "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"nomUtilisateur\":\"$NOM_AUTRE\",\"motDePasse\":\"$MOT_DE_PASSE_EMPLOYE\"}" | val token)
AUTRE=(-H "Authorization: Bearer $TOKEN_AUTRE" -H 'Content-Type: application/json')
check "$(A "${AUTRE[@]}" "$API/demandes/$ID_DEMANDE")" "404" "la demande d'autrui est invisible"

# ============================================================
# 5. Décision et livraison réelle
# ============================================================
echo
echo "=== 5. Approbation et livraison ==="
MOUV_AVANT=$(J "${AH[@]}" "$API/mouvements" | cnt)
R=$(J -X PATCH "$API/demandes/$ID_DEMANDE/decision" "${AH[@]}" \
  -d "{\"decision\":\"APPROUVEE\",\"immobilisationId\":$ID_BIEN}")
ID_MOUVEMENT=$(echo "$R" | val mouvementId)
CODE_APRES=$(echo "$R" | val immobilisationCode)
check "$(echo "$R" | val statut)" "APPROUVEE" "demande approuvée"
check "$(echo "$R" | val immobilisationId)" "$ID_BIEN" "bien rattaché à la demande"
check "$(echo "$R" | val mouvementType)" "TRANSFERT" "livraison inter-services = TRANSFERT"
check_ne "$ID_MOUVEMENT" "" "un mouvement est créé"
check_ne "$CODE_APRES" "$CODE_BIEN" "le code unique est réémis après transfert"

MOUV_APRES=$(J "${AH[@]}" "$API/mouvements" | cnt)
check "$((MOUV_APRES - MOUV_AVANT))" "1" "exactement un mouvement ajouté"

BIEN=$(J "${AH[@]}" "$API/immobilisations/$ID_BIEN")
check "$(echo "$BIEN" | val serviceId)" "$ID_SERVICE_DEST" "le bien a changé de service"
check "$(echo "$BIEN" | val agenceId)" "$ID_AGENCE" "l'agence du bien suit le service"
check "$(echo "$BIEN" | val codeUnique)" "$CODE_APRES" "la fiche porte le nouveau code"
check "$(echo "$BIEN" | val qrCode)" "" "le QR de l'ancien code est invalidé"

# Le bien est désormais dans le périmètre de l'employé, scannable par code.
check "$(J "${EH[@]}" "$API/immobilisations/$ID_BIEN" | val codeUnique)" "$CODE_APRES" "bien désormais visible dans le périmètre"
check "$(J "${EH[@]}" "$API/immobilisations/code?code=$CODE_APRES" | val id)" "$ID_BIEN" "recherche par code dans le périmètre"
check "$(J "${EH[@]}" "$API/immobilisations/code?code=$CODE_BIEN" | val id)" "" "l'ancien code ne résout plus rien"

check "$(J "${EH[@]}" "$API/notifications" | has type DEMANDE_APPROUVEE)" "YES" "l'employé est notifié de l'approbation"
check "$(J "${AH[@]}" "$API/notifications" | has type NOUVELLE_DEMANDE)" "YES" "l'ADMIN a une notification de nouvelle demande"

# ============================================================
# 6. Traçabilité
# ============================================================
echo
echo "=== 6. Traçabilité ==="
if [ -n "$ID_MOUVEMENT" ]; then
  MV=$(J "${AH[@]}" "$API/mouvements/$ID_MOUVEMENT")
  check "$(echo "$MV" | val typeMouvement)" "TRANSFERT" "mouvement relu : TRANSFERT"
  check "$(echo "$MV" | val immobilisationId)" "$ID_BIEN" "mouvement pointe le bien"
  check "$(echo "$MV" | val demandeReference)" "$REF_DEMANDE" "mouvement pointe la demande"
  check_ne "$(echo "$MV" | val ancienneLocalisation)" "$(echo "$MV" | val nouvelleLocalisation)" "localisation avant ≠ après"
else
  skip "mouvement non identifié"
fi

AUDIT=$(J "${AH[@]}" "$API/journal-audit?action=DECISION_DEMANDE")
check_ge "$(echo "$AUDIT" | cnt)" "1" "la décision est tracée"
check "$(echo "$AUDIT" | python -c "
import sys,json
d=json.load(sys.stdin)
print('YES' if d and '$REF_DEMANDE' in d[0].get('description','') else 'NO')")" "YES" "la trace cite la référence de la demande"
check "$(J "${AH[@]}" "$API/journal-audit?action=CREATION&entite=Immobilisation" | has entite Immobilisation)" "YES" "la création du bien est tracée"
check "$(A "${EH[@]}" "$API/journal-audit")" "403" "le journal d'audit est réservé à l'ADMIN"

# ============================================================
# 7. Tableau de bord et exports
# ============================================================
echo
echo "=== 7. Tableau de bord et exports ==="
STATS=$(J "${AH[@]}" "$API/dashboard/stats")
TOTAL=$(echo "$STATS" | val totalImmobilisations)
check_ge "$TOTAL" "1" "le tableau de bord compte le parc"
check_ge "$(echo "$STATS" | val campagnesEnCours)" "0" "compteur de campagnes cohérent"
check "$(A "${EH[@]}" "$API/dashboard/stats")" "200" "l'employé voit un tableau de bord filtré"

TMP="${TMPDIR:-/tmp}/auximad-e2e-$$"
mkdir -p "$TMP"
check "$(dl "$API/export/immobilisations/excel" "$TMP/immos.xlsx")" "200" "export Excel des immobilisations"
check_ge "$(taille "$TMP/immos.xlsx")" "1000" "fichier Excel des immobilisations non vide"
check "$(dl "$API/export/mouvements/excel" "$TMP/mouvements.xlsx")" "200" "export Excel des mouvements"
check "$(dl "$API/export/demandes/excel" "$TMP/demandes.xlsx")" "200" "export Excel des demandes"
check "$(dl "$API/export/immobilisations/$ID_BIEN/pdf" "$TMP/fiche.pdf")" "200" "export PDF de la fiche du bien"
check_ge "$(taille "$TMP/fiche.pdf")" "1000" "fichier PDF non vide"

# ============================================================
# 8. Campagne d'inventaire
# ============================================================
echo
echo "=== 8. Campagne d'inventaire ==="
R=$(J -X POST "$API/inventaires/campagnes" "${AH[@]}" \
  -d "{\"libelle\":\"Campagne E2E $SUF\",\"dateDebut\":\"$DATE_JOUR\",\"agenceId\":$ID_AGENCE}")
ID_CAMPAGNE=$(echo "$R" | val id)
check_ne "$ID_CAMPAGNE" "" "campagne créée"
check "$(echo "$R" | val statut)" "EN_COURS" "campagne EN_COURS"
check "$(echo "$R" | val agenceId)" "$ID_AGENCE" "campagne rattachée à l'agence"
check "$(A "${EH[@]}" "$API/inventaires/campagnes")" "403" "inventaire réservé à l'ADMIN"

R=$(J -X POST "$API/inventaires/lignes/scan" "${AH[@]}" \
  -d "{\"campagneId\":$ID_CAMPAGNE,\"immobilisationId\":$ID_BIEN,\"etatConstate\":\"PRESENT\",\"localisationConstatee\":\"Bureau E2E\"}")
check "$(echo "$R" | val etatConstate)" "PRESENT" "scan enregistré PRESENT"
check "$(echo "$R" | val immobilisationCode)" "$CODE_APRES" "le scan pointe le code courant"
check "$(J "${AH[@]}" "$API/inventaires/campagnes/$ID_CAMPAGNE/lignes" | cnt)" "1" "une ligne d'inventaire"

R=$(J -X POST "$API/inventaires/campagnes/$ID_CAMPAGNE/cloturer" "${AH[@]}")
check "$(echo "$R" | val statut)" "CLOTURE" "campagne clôturée"
check "$(A "${AH[@]}" -X POST "$API/inventaires/lignes/scan" -d "{\"campagneId\":$ID_CAMPAGNE,\"immobilisationId\":$ID_BIEN}")" "400" "scan refusé après clôture"
check_ge "$(J "${AH[@]}" "$API/journal-audit?action=CLOTURE_CAMPAGNE" | cnt)" "1" "la clôture est tracée"
check "$(dl "$API/export/inventaires/$ID_CAMPAGNE/excel" "$TMP/inventaire.xlsx")" "200" "export Excel de la campagne"
check_ge "$(taille "$TMP/inventaire.xlsx")" "1000" "fichier d'inventaire non vide"

# ============================================================
# 9. Bilan
# ============================================================
echo
echo "=== Bilan ==="
echo "  Agence      : $CODE_AGENCE ($ID_AGENCE)"
echo "  Services    : $ID_SERVICE_DEST (destinataire) / $ID_SERVICE_SRC (source)"
echo "  Employé     : $NOM_EMPLOYE ($ID_EMPLOYE)"
echo "  Bien        : $CODE_BIEN -> $CODE_APRES ($ID_BIEN)"
echo "  Demande     : $REF_DEMANDE ($ID_DEMANDE)"
echo "  Campagne    : $ID_CAMPAGNE"
echo
echo "  $PASS OK / $FAIL FAIL / $SKIP SKIP"
if [ "$FAIL" = "0" ]; then
  echo "  PARCOURS COMPLET : VERT"
else
  echo "  PARCOURS COMPLET : ÉCHEC"
fi
exit "$FAIL"
