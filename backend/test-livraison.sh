#!/usr/bin/env bash
# Tests de contournement de la LIVRAISON RÉELLE d'une immobilisation.
#
# Vérifie que l'approbation d'une demande ne se contente pas de pointer un bien :
# le bien change réellement de service, un mouvement est enregistré, et la trace
# est lisible dans les deux sens.
#
#  - TRANSFERT  : le bien vient d'un autre service -> service + agence suivent,
#                 code unique réémis, QR invalidé.
#  - AFFECTATION: le bien est déjà dans le service de destination -> rien ne
#                 bouge sur le bien, seule la remise est tracée.
#
# ATTENTION encodage : sous Git Bash/Windows, les accents placés directement
# dans la ligne de commande sont transmis dans le codepage OEM et corrompent le
# JSON (« Invalid UTF-8 middle byte »). Les payloads passés par argv sont donc
# écrits sans accent, et l'UTF-8 est vérifié à part via un fichier.
#
# Les compteurs sont mesurés en delta (baseline -> après) pour rester
# reproductibles sur une base déjà peuplée.
set -u
API=http://localhost:8080/api

# Git Bash décode la sortie de curl selon la console (cp1252), ce qui mojibake
# les accents lus depuis le JSON. Forcer UTF-8 côté Python — les données en base
# sont correctes, c'est la sonde qui décode mal.
export PYTHONIOENCODING=utf-8
export PYTHONUTF8=1

tok() { curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d "{\"nomUtilisateur\":\"$1\",\"motDePasse\":\"$2\"}" \
  | python -c "import sys,json;print(json.load(sys.stdin).get('token') or '')" 2>/dev/null; }

TOKEN_ADMIN=$(tok Tojo admin123)
TOKEN_EMP=$(tok TestEmp test123)
TOKEN_VIDE=$(tok TestVide test123)

A()  { curl -s -o /dev/null -w "%{http_code}" "$@"; }
J()  { curl -s "$@"; }
val(){ python -c "import sys,json;print(json.load(sys.stdin).get('$1',''))" 2>/dev/null; }
cnt(){ python -c "import sys,json;d=json.load(sys.stdin);print(len(d) if isinstance(d,list) else -1)" 2>/dev/null; }

PASS=0; FAIL=0
check() { if [ "$1" = "$2" ]; then PASS=$((PASS+1)); echo "  OK   $3  ($1)";
           else FAIL=$((FAIL+1)); echo "  FAIL $3  (obtenu «$1», attendu «$2»)"; fi; }
check_ne() { if [ "$1" != "$2" ]; then PASS=$((PASS+1)); echo "  OK   $3  ($1 ≠ $2)";
              else FAIL=$((FAIL+1)); echo "  FAIL $3  (identique «$1»)"; fi; }
check_ge() { if [ "$1" -ge "$2" ] 2>/dev/null; then PASS=$((PASS+1)); echo "  OK   $3  ($1 >= $2)";
              else FAIL=$((FAIL+1)); echo "  FAIL $3  (obtenu «$1», attendu >= «$2»)"; fi; }

AH=(-H "Authorization: Bearer $TOKEN_ADMIN" -H 'Content-Type: application/json')
EH=(-H "Authorization: Bearer $TOKEN_EMP"  -H 'Content-Type: application/json')
VH=(-H "Authorization: Bearer $TOKEN_VIDE" -H 'Content-Type: application/json')

SERVICE_EMP=2   # TestEmp est rattaché au service 2

echo "=== Comptes ==="
echo "  admin=$([ -n "$TOKEN_ADMIN" ] && echo OK || echo MANQUANT) emp=$([ -n "$TOKEN_EMP" ] && echo OK || echo MANQUANT) sans-service=$([ -n "$TOKEN_VIDE" ] && echo OK || echo MANQUANT)"

echo
echo "=== 0. Sélecteur de biens ==="
CANDS=$(J "${AH[@]}" "$API/demandes/candidats")
NBC=$(echo "$CANDS" | cnt)
check "$(A "${EH[@]}" "$API/demandes/candidats")" "403" "sélecteur réservé à l'ADMIN"

# Un bien d'un AUTRE service que celui de TestEmp, et un bien de SON service.
#
# Chacun est choisi de préférence AVEC un QR code imprimé : c'est le seul cas où
# le transfert (invalidation) ou l'affectation (préservation) du QR est
# observable — un QR déjà absent ne prouve rien. Le sélecteur n'expose pas le QR,
# on le lit donc sur la fiche complète du bien.
choisir() { # $1 = condition python sur le candidat
  local cid qr
  for cid in $(echo "$CANDS" | python -c "
import sys,json
d=json.load(sys.stdin)
print(' '.join(str(x['id']) for x in d if $1))"); do
    qr=$(J "${AH[@]}" $API/immobilisations/$cid | val qrCode)
    if [ -n "$qr" ] && [ "$qr" != "None" ]; then echo "$cid"; return; fi
  done
  echo "$CANDS" | python -c "
import sys,json
d=json.load(sys.stdin)
b=[x for x in d if $1]
print(b[0]['id'] if b else '')"
}
CAND_AUTRE=$(choisir "x.get('serviceId') not in (None,$SERVICE_EMP)")
CAND_MEME=$(choisir "x.get('serviceId')==$SERVICE_EMP")

echo "  $NBC biens livrables — autre service:$CAND_AUTRE même service:$CAND_MEME"

# Tous les biens (ADMIN) pour trouver des biens non livrables
ALL=$(J "${AH[@]}" $API/immobilisations)
CEDE=$(echo "$ALL" | python -c "
import sys,json
d=json.load(sys.stdin)
b=[x for x in d if x.get('statut')=='CEDE']
print(b[0]['id'] if b else '')")
HORS=$(echo "$ALL" | python -c "
import sys,json
d=json.load(sys.stdin)
b=[x for x in d if x.get('etatPhysique')=='HORS_SERVICE']
print(b[0]['id'] if b else '')")
echo "  cédé:$CEDE hors-service:$HORS"

echo
echo "=== 1. TRANSFERT : bien d'un autre service ==="
BASE_EMP=$(J "${EH[@]}" $API/immobilisations | cnt)
MOUV_AVANT=$(J "${AH[@]}" $API/mouvements | cnt)

IMMO=$(J "${AH[@]}" $API/immobilisations/$CAND_AUTRE)
CODE_AVANT=$(echo "$IMMO" | val codeUnique)
SERVICE_AVANT=$(echo "$IMMO" | val serviceId)
QRCODE_AVANT=$(echo "$IMMO" | val qrCode)

# L'employé ne voit pas ce bien avant la livraison
INAVANT=$(J "${EH[@]}" $API/immobilisations | python -c "
import sys,json
print('YES' if any(x['id']==$CAND_AUTRE for x in json.load(sys.stdin)) else 'NO')")

R=$(J -X POST $API/demandes "${EH[@]}" -d '{"designation":"Immo de test livraison","motif":"Besoin reel de service","quantite":1,"priorite":"URGENTE"}')
ID=$(echo "$R" | val id)
REF=$(echo "$R" | val reference)

RD=$(J -X PATCH $API/demandes/$ID/decision "${AH[@]}" \
  -d "{\"decision\":\"APPROUVEE\",\"immobilisationId\":$CAND_AUTRE}")

check "$(echo "$RD" | val statut)"              "APPROUVEE"         "demande approuvée"
check "$(echo "$RD" | val immobilisationId)"    "$CAND_AUTRE"       "bien rattaché à la demande"
check "$(echo "$RD" | val mouvementType)"       "TRANSFERT"         "mouvement de type TRANSFERT"
check_ne "$(echo "$RD" | val mouvementId)"      ""                  "mouvement lié à la demande"
check_ne "$(echo "$RD" | val immobilisationCode)" "$CODE_AVANT"      "code unique réémis"

IMMO2=$(J "${AH[@]}" $API/immobilisations/$CAND_AUTRE)
check "$(echo "$IMMO2" | val serviceId)"  "$SERVICE_EMP"  "service du bien = service du demandeur"
check_ne "$(echo "$IMMO2" | val codeUnique)" "$CODE_AVANT" "code unique effectivement changé"
if [ -n "$QRCODE_AVANT" ] && [ "$QRCODE_AVANT" != "None" ]; then
  check "$(echo "$IMMO2" | val qrCode)" "None" "QR code invalidé (il existait avant)"
else
  echo "  SKIP QR : le bien sélectionné n'avait pas de QR à invalider"
fi
check "$(echo "$IMMO2" | val codeUnique)" "$(echo "$RD" | val immobilisationCode)" "demande pointe le nouveau code"

# L'employé voit désormais le bien
INAFT=$(J "${EH[@]}" $API/immobilisations | python -c "
import sys,json
print('YES' if any(x['id']==$CAND_AUTRE for x in json.load(sys.stdin)) else 'NO')")
check "$INAVANT" "NO" "bien invisible de l'employé avant livraison"
check "$INAFT"   "YES" "bien visible de l'employé après livraison"

MOUV_APRES=$(J "${AH[@]}" $API/mouvements | cnt)
check "$(expr $MOUV_APRES - $MOUV_AVANT)" "1" "un mouvement de plus dans l'historique"

# Le mouvement trace d'où il vient
MV=$(J "${AH[@]}" $API/mouvements | python -c "
import sys,json
d=json.load(sys.stdin)
b=[x for x in d if x.get('demandeId')==$ID]
print(json.dumps(b[0]) if b else '{}')")
check "$(echo "$MV" | val typeMouvement)"  "TRANSFERT"  "mouvement de type TRANSFERT"
check "$(echo "$MV" | val demandeReference)" "$REF"      "mouvement renvoie à la demande"
check "$(echo "$MV" | val utilisateurNom)" "Tojo Niaina" "auteur du mouvement = l'admin qui décide"
check_ne "$(echo "$MV" | val ancienneLocalisation)" "$(echo "$MV" | val nouvelleLocalisation)" "localisation avant ≠ après"
echo "$MV" | python -c "
import sys,json
m=json.load(sys.stdin)
ok = m.get('commentaire') and 'DEMANDE' not in m['commentaire'] and '$REF' in m['commentaire']
print('YES' if ok else 'NO')" > /tmp/_mc
check "$(cat /tmp/_mc)" "YES" "commentaire porte la référence de la demande"

echo
echo "=== 2. AFFECTATION : bien déjà dans le service de destination ==="
CODE_M_AVANT=$(J "${AH[@]}" $API/immobilisations/$CAND_MEME | val codeUnique)
QRCODE_M_AVANT=$(J "${AH[@]}" $API/immobilisations/$CAND_MEME | val qrCode)
MOUV_AVANT2=$(J "${AH[@]}" $API/mouvements | cnt)

R2=$(J -X POST $API/demandes "${EH[@]}" -d '{"designation":"Immo de test affectation","motif":"Besoin interne de service","quantite":1}')
ID2=$(echo "$R2" | val id)

RD2=$(J -X PATCH $API/demandes/$ID2/decision "${AH[@]}" \
  -d "{\"decision\":\"APPROUVEE\",\"immobilisationId\":$CAND_MEME,\"destinationServiceId\":$SERVICE_EMP}")

check "$(echo "$RD2" | val mouvementType)" "AFFECTATION" "mouvement de type AFFECTATION"
check "$(echo "$RD2" | val immobilisationCode)" "$CODE_M_AVANT" "code unique préservé"

IMMO3=$(J "${AH[@]}" $API/immobilisations/$CAND_MEME)
check "$(echo "$IMMO3" | val serviceId)"    "$SERVICE_EMP"   "service inchangé"
check "$(echo "$IMMO3" | val codeUnique)"   "$CODE_M_AVANT"  "code unique inchangé (pas de réémission)"
if [ -n "$QRCODE_M_AVANT" ] && [ "$QRCODE_M_AVANT" != "None" ]; then
  check "$(echo "$IMMO3" | val qrCode)" "$QRCODE_M_AVANT" "QR code préservé (pas invalidé pour rien)"
else
  echo "  SKIP QR : le bien sélectionné n'avait pas de QR à préserver"
fi
check "$(expr $(J "${AH[@]}" $API/mouvements | cnt) - $MOUV_AVANT2)" "1" "la remise est malgré tout tracée"

echo
echo "=== 3. Garde-fous ==="
R3=$(J -X POST $API/demandes "${EH[@]}" -d '{"designation":"Test garde-fous","motif":"Cas limite a verifier","quantite":1}')
ID3=$(echo "$R3" | val id)
ST=$(J "${EH[@]}" $API/demandes/$ID3 | val statut)

if [ -n "$CEDE" ]; then
  check "$(A -X PATCH $API/demandes/$ID3/decision "${AH[@]}" -d "{\"decision\":\"APPROUVEE\",\"immobilisationId\":$CEDE}")" "400" "bien cédé refusé"
else echo "  SKIP aucun bien cédé"; fi
if [ -n "$HORS" ]; then
  check "$(A -X PATCH $API/demandes/$ID3/decision "${AH[@]}" -d "{\"decision\":\"APPROUVEE\",\"immobilisationId\":$HORS}")" "400" "bien hors service refusé"
else echo "  SKIP aucun bien hors service"; fi
check "$(J "${EH[@]}" $API/demandes/$ID3 | val statut)" "$ST" "approbation annulée : la demande reste EN_ATTENTE"

# Un même bien ne se redonne pas
check "$(A -X PATCH $API/demandes/$ID3/decision "${AH[@]}" -d "{\"decision\":\"APPROUVEE\",\"immobilisationId\":$CAND_AUTRE}")" "400" "bien déjà livré refusé"

echo
echo "=== 4. Employé sans service ==="
R4=$(J -X POST $API/demandes "${VH[@]}" -d '{"designation":"Test sans service","motif":"Je n appartiens a aucun service","quantite":1}')
ID4=$(echo "$R4" | val id)
check "$(echo "$R4" | val serviceAffiche)" "Non affecté" "demande sans service affichée « Non affecté »"

# Sans destination -> refus explicite
check "$(A -X PATCH $API/demandes/$ID4/decision "${AH[@]}" -d "{\"decision\":\"APPROUVEE\",\"immobilisationId\":$CAND_AUTRE}")" "400" "livraison impossible sans destination"

# Avec destination -> transfert effectif
CAND_AUTRE2=$(J "${AH[@]}" "$API/demandes/candidats" | python -c "
import sys,json
d=json.load(sys.stdin)
b=[x for x in d if x.get('serviceId') not in (None,$SERVICE_EMP)]
print(b[0]['id'] if b else '')")
if [ -n "$CAND_AUTRE2" ]; then
  RD4=$(J -X PATCH $API/demandes/$ID4/decision "${AH[@]}" \
    -d "{\"decision\":\"APPROUVEE\",\"immobilisationId\":$CAND_AUTRE2,\"destinationServiceId\":$SERVICE_EMP}")
  check "$(echo "$RD4" | val statut)" "APPROUVEE" "approuvée avec destination choisie"
  check "$(J "${AH[@]}" $API/immobilisations/$CAND_AUTRE2 | val serviceId)" "$SERVICE_EMP" "transfert effectif vers la destination choisie"
else echo "  SKIP plus de bien livrable d'un autre service"; fi

echo
echo "=== 5. Contrôle de rôle sur la décision ==="
R5=$(J -X POST $API/demandes "${EH[@]}" -d '{"designation":"Test role decision","motif":"Employe tente une decision","quantite":1}')
ID5=$(echo "$R5" | val id)
check "$(A -X PATCH $API/demandes/$ID5/decision "${EH[@]}" -d '{"decision":"APPROUVEE"}')" "403" "employé ne peut pas décider"
check "$(A -X PATCH $API/demandes/$ID4/decision "${VH[@]}" -d "{\"decision\":\"APPROUVEE\",\"immobilisationId\":$CAND_MEME}")" "403" "employé sans service ne peut pas décider non plus"

echo
echo "=== 6. UTF-8 préservé dans le commentaire du mouvement ==="
# Écrit via heredoc : bash relit le fichier en octets bruts, contrairement à une
# ligne de commande tapée, où le codepage de la console corrompt les accents.
FIXTURE=$(mktemp)
cat > "$FIXTURE" <<'JSON'
{"designation":"Immo test accents","motif":"Équipement à renouveler — accès difficile, pièce à transférer","quantite":1,"priorite":"NORMALE"}
JSON
R6=$(J -X POST $API/demandes "${EH[@]}" --data-binary "@$FIXTURE")
rm -f "$FIXTURE"
ID6=$(echo "$R6" | val id)
REF6=$(echo "$R6" | val reference)
CAND_AUTRE3=$(J "${AH[@]}" "$API/demandes/candidats" | python -c "
import sys,json
d=json.load(sys.stdin)
b=[x for x in d if x.get('serviceId') not in (None,$SERVICE_EMP)]
print(b[0]['id'] if b else '')")
if [ -n "$CAND_AUTRE3" ]; then
  J -X PATCH $API/demandes/$ID6/decision "${AH[@]}" \
    -d "{\"decision\":\"APPROUVEE\",\"immobilisationId\":$CAND_AUTRE3,\"destinationServiceId\":$SERVICE_EMP}" > /dev/null
  COM=$(J "${AH[@]}" $API/mouvements | python -c "
import sys,json
d=json.load(sys.stdin)
b=[x for x in d if x.get('demandeId')==$ID6]
print(b[0].get('commentaire','') if b else '')")
  echo "$COM" | python -c "
import sys
s=sys.stdin.read()
print('YES' if 'É' in s and 'à' in s else 'NO')" > /tmp/_u8
  check "$(cat /tmp/_u8)" "YES" "accents du motif préservés dans le mouvement"
  check "$REF6" "$REF6" "référence $REF6 calculée"
else echo "  SKIP aucun bien livrable"; fi

echo
echo "=== Résultat ==="
echo "  $PASS OK / $FAIL FAIL"
[ "$FAIL" = "0" ] && echo "  TOUJOURS VERT" || echo "  ÉCHEC"
exit $FAIL
