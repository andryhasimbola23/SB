#!/usr/bin/env bash
# Tests de contournement du module « demande d'immobilisation ».
#
# Vérifie : le périmètre par identité (employé = ses demandes, jamais celles du
# service), la figuration du service au dépôt, les transitions de statut, le
# motif de refus obligatoire et les refus de rôle.
#
# ATTENTION encodage : sous Git Bash/Windows, les accents placés directement
# dans la ligne de commande sont transmis dans le codepage OEM et corrompent le
# JSON (« Invalid UTF-8 middle byte »). Les payloads passés par argv sont donc
# écrits sans accent, et l'UTF-8 est vérifié à part via un fichier (--data-binary).
#
# Les compteurs sont mesurés en delta (baseline → après) pour rester
# reproductibles sur une base déjà peuplée.
set -u
API=http://localhost:8080/api

tok() { curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d "{\"nomUtilisateur\":\"$1\",\"motDePasse\":\"$2\"}" \
  | python -c "import sys,json;print(json.load(sys.stdin).get('token') or '')" 2>/dev/null; }

TOKEN_ADMIN=$(tok Tojo admin123)
TOKEN_EMP=$(tok TestEmp test123)
TOKEN_VIDE=$(tok TestVide test123)

A()  { curl -s -o /dev/null -w "%{http_code}" "$@"; }
J()  { curl -s "$@"; }
cnt(){ python -c "import sys,json;d=json.load(sys.stdin);print(len(d) if isinstance(d,list) else -1)" 2>/dev/null; }
val(){ python -c "import sys,json;print(json.load(sys.stdin).get('$1',''))" 2>/dev/null; }

PASS=0; FAIL=0
check() { if [ "$1" = "$2" ]; then PASS=$((PASS+1)); echo "  OK   $3  ($1)";
           else FAIL=$((FAIL+1)); echo "  FAIL $3  (obtenu «$1», attendu «$2»)"; fi; }
# >= : pour les répartitions par statut, qui incluent les restes des runs précédents
check_ge() { if [ "$1" -ge "$2" ] 2>/dev/null; then PASS=$((PASS+1)); echo "  OK   $3  ($1 >= $2)";
           else FAIL=$((FAIL+1)); echo "  FAIL $3  (obtenu «$1», attendu >= «$2»)"; fi; }
# Présence d'un identifiant dans une liste JSON
ids_of(){ python -c "import sys,json;print(' '.join(str(x['id']) for x in json.load(sys.stdin)))" 2>/dev/null; }
contains(){ case " $1 " in *" $2 "*) echo YES;; *) echo NO;; esac; }
# Champ JSON explicitement nul (« None » en Python) vs absent/vide
isnull(){ python -c "import sys,json;print('NULL' if json.load(sys.stdin).get('$1') is None else 'RENSIGNE')" 2>/dev/null; }

AH=(-H "Authorization: Bearer $TOKEN_ADMIN" -H 'Content-Type: application/json')
EH=(-H "Authorization: Bearer $TOKEN_EMP"  -H 'Content-Type: application/json')
VH=(-H "Authorization: Bearer $TOKEN_VIDE" -H 'Content-Type: application/json')

echo "=== Comptes ==="
echo "  admin=$([ -n "$TOKEN_ADMIN" ] && echo OK || echo MANQUANT) emp=$([ -n "$TOKEN_EMP" ] && echo OK || echo MANQUANT) vide=$([ -n "$TOKEN_VIDE" ] && echo OK || echo MANQUANT)"

BA=$(J "${AH[@]}" $API/demandes | cnt); BE=$(J "${EH[@]}" $API/demandes | cnt); BV=$(J "${VH[@]}" $API/demandes | cnt)
echo "  baseline admin=$BA employé=$BE sans-service=$BV"

echo
echo "=== 1. Dépôt (employé rattaché) ==="
R=$(J -X POST $API/demandes "${EH[@]}" -d '{"designation":"Chaise ergonomique","motif":"Chaise actuelle hors service","quantite":2,"priorite":"URGENTE","besoinLe":"2026-11-01"}')
ID1=$(echo "$R" | val id)
check "$(echo "$R" | val statut)"     "EN_ATTENTE"           "statut initial EN_ATTENTE"
check "$(echo "$R" | val serviceNom)" "Service Informatique" "service figé au dépôt"
check "$(echo "$R" | val priorite)"   "URGENTE"              "priorité reprise"
check "$(echo "$R" | val quantite)"   "2"                    "quantité reprise"
check "$(echo "$R" | val reference)"  "DEM-2026-$(printf '%04d' "${ID1:-0}")" "référence dérivée de l'id et de l'année"
echo "  -> id=$ID1"

echo
echo "=== 2. Dépôt (employé SANS service — service null, « Non affecté ») ==="
R=$(J -X POST $API/demandes "${VH[@]}" -d '{"designation":"Tableau blanc","motif":"Salle de reunion vide","quantite":1,"priorite":"NORMALE"}')
ID2=$(echo "$R" | val id)
check "$(echo "$R" | val serviceAffiche)" "Non affecté"  "service null affiché « Non affecté »"
check "$(echo "$R" | isnull serviceId)"   "NULL"         "serviceId réellement nul (et non 0)"
echo "  -> id=$ID2"

echo
echo "=== 3. Périmètre de lecture (par identifiant, insensible aux restes des runs) ==="
LE=$(J "${EH[@]}" $API/demandes | ids_of); LV=$(J "${VH[@]}" $API/demandes | ids_of); LA=$(J "${AH[@]}" $API/demandes | ids_of)
check "$(contains "$LE" "$ID1")" "YES" "employé voit sa propre demande"
check "$(contains "$LE" "$ID2")" "NO"  "employé ne voit PAS celle d'un collègue"
check "$(contains "$LV" "$ID2")" "YES" "employé sans service voit sa propre demande"
check "$(contains "$LV" "$ID1")" "NO"  "idem, sans voir celle des autres"
check "$(contains "$LA" "$ID1")$(contains "$LA" "$ID2")" "YESYES" "admin voit les demandes des deux"
check "$(J "${EH[@]}" $API/demandes | cnt)" "$((BE+1))" "employé : total = baseline + 1"
check "$(J "${VH[@]}" $API/demandes | cnt)" "$((BV+1))" "employé sans service : total = baseline + 1"
check "$(J "${AH[@]}" $API/demandes | cnt)" "$((BA+2))" "admin : total = baseline + 2"

echo
echo "=== 4. Accès unitaire hors périmètre (404, jamais 403) ==="
check "$(A "${EH[@]}" $API/demandes/$ID2)" "404" "demande d'un collègue -> 404"
check "$(A "${VH[@]}" $API/demandes/$ID1)" "404" "demande d'autrui -> 404"
check "$(A "${AH[@]}" $API/demandes/999999)" "404" "id inexistant -> 404"

echo
echo "=== 5. Rôles ==="
check "$(A "${EH[@]}" -X PATCH $API/demandes/$ID1/decision -H 'Content-Type: application/json' -d '{"decision":"APPROUVEE"}')" "403" "employé ne peut pas décider"
check "$(A "${AH[@]}" -X PATCH $API/demandes/$ID2/annuler)" "404" "admin ne peut pas retirer la demande d'autrui (retrait = auteur seul)"
check "$(A "${VH[@]}" -X PATCH $API/demandes/$ID2/annuler)" "200" "auteur peut retirer sa propre demande"

echo
echo "=== 6. Refus sans motif (obligatoire) ==="
check "$(A "${AH[@]}" -X PATCH $API/demandes/$ID1/decision -H 'Content-Type: application/json' -d '{"decision":"REJETTEE","commentaire":"   "}')" "400" "refus sans motif -> 400"
check "$(A "${AH[@]}" -X PATCH $API/demandes/$ID1/decision -H 'Content-Type: application/json' -d '{"decision":"BOF"}')" "400" "décision inconnue -> 400"

echo
echo "=== 7. Approbation puis verrou de transition ==="
TMP="$TEMP/rep.json"
CODE=$(curl -s -o "$TMP" -w "%{http_code}" -X PATCH $API/demandes/$ID1/decision "${AH[@]}" \
        -H 'Content-Type: application/json' -d '{"decision":"APPROUVEE","commentaire":"Budget valide"}')
check "$CODE" "200" "approbation -> 200"
check "$(cat "$TMP" | val statut)" "APPROUVEE" "statut APPROUVEE"
check "$([ -n "$(cat "$TMP" | val traiteeParNom)" ] && echo RENSEIGNE || echo VIDE)" "RENSEIGNE" "décideur tracé"
rm -f "$TMP"
check "$(A "${EH[@]}" -X PATCH $API/demandes/$ID1/decision -H 'Content-Type: application/json' -d '{"decision":"REJETTEE","commentaire":"x"}')" "403" "employé ne redécide pas (403)"
check "$(A "${AH[@]}" -X PATCH $API/demandes/$ID1/decision -H 'Content-Type: application/json' -d '{"decision":"REJETTEE","commentaire":"x"}')" "400" "re-décision par l'admin d'une demande close -> 400"
check "$(A "${EH[@]}" -X PATCH $API/demandes/$ID1/annuler)" "400" "retrait par l'auteur d'une demande close -> 400"
check "$(A "${AH[@]}" -X PATCH $API/demandes/$ID1/annuler)" "404" "retrait par un tiers (admin) -> 404"

echo
echo "=== 8. UTF-8 (payload via fichier, accents préservés) ==="
PAY="$TEMP/payload-utf8.json"; BODY="$TEMP/payload-utf8.out"
cat > "$PAY" <<'JSON'
{"designation":"Clavier mécanique","motif":"Le clavier actuel est en panne — touches collantes","quantite":1,"priorite":"URGENTE"}
JSON
CODE=$(curl -s -o "$BODY" -w "%{http_code}" -X POST $API/demandes "${EH[@]}" \
        -H 'Content-Type: application/json' --data-binary @"$PAY")
RU=$(cat "$BODY"); IDU=$(echo "$RU" | val id); rm -f "$PAY" "$BODY"
check "$CODE" "201" "dépôt UTF-8 -> 201"
check "$(echo "$RU" | val designation)" "Clavier mécanique" "accents préservés (désignation)"
check "$(echo "$RU" | val motif)" "Le clavier actuel est en panne — touches collantes" "tiret cadratin préservé (motif)"

echo
echo "=== 9. Validation des champs ==="
check "$(A "${EH[@]}" -X POST $API/demandes -H 'Content-Type: application/json' -d '{"designation":"","motif":"x"}')" "400" "désignation vide -> 400"
check "$(A "${EH[@]}" -X POST $API/demandes -H 'Content-Type: application/json' -d '{"designation":"Ordinateur","motif":""}')" "400" "motif vide -> 400"
check "$(A "${EH[@]}" -X POST $API/demandes -H 'Content-Type: application/json' -d '{"designation":"Ordinateur","motif":"x","quantite":0}')" "400" "quantité 0 -> 400"
check "$(A "${EH[@]}" -X POST $API/demandes -H 'Content-Type: application/json' -d '{"designation":"Ordinateur","motif":"x","categorieId":99999}')" "404" "catégorie inexistante -> 404"

echo
echo "=== 10. Filtres et compteurs (périmètre) ==="
LEA=$(J "${EH[@]}" "$API/demandes?statut=EN_ATTENTE" | ids_of)
check "$(contains "$LEA" "$IDU")" "YES" "filtre « en attente » inclut la demande ouverte"
check "$(contains "$LEA" "$ID1")" "NO"  "…et exclut celle déjà approuvée"
LC=$(J "${EH[@]}" "$API/demandes?keyword=Chaise" | ids_of)
check "$(contains "$LC" "$ID1")" "YES" "recherche employé trouve sa demande"
check "$(contains "$LC" "$ID2")" "NO"  "…sans sortir de son périmètre"
check "$(J "${AH[@]}" "$API/demandes?keyword=zzzzinexistant" | cnt)" "0" "recherche sans résultat"
JE=$(J "${EH[@]}" $API/demandes/stats); JA=$(J "${AH[@]}" $API/demandes/stats)
check "$(echo "$JA" | val total)" "$((BA+3))" "compteurs admin : total = baseline + 3"
check "$(echo "$JE" | val total)" "$((BE+2))" "compteurs employé : total = baseline + 2 (personnels)"
check_ge "$(echo "$JA" | val enAttente)"  "1" "compteurs admin : au moins la demande ouverte"
check_ge "$(echo "$JA" | val approuvees)" "1" "compteurs admin : au moins l'approbation"
check_ge "$(echo "$JA" | val annulees)"   "1" "compteurs admin : au moins le retrait"
check_ge "$(echo "$JE" | val enAttente)"  "1" "compteurs employé : au moins sa demande ouverte"
check_ge "$(echo "$JE" | val approuvees)" "1" "compteurs employé : au moins son approbation"

echo
echo "=== 11. Export ==="
check "$(A "${AH[@]}" $API/export/demandes/excel)" "200" "export admin -> 200"
check "$(A "${EH[@]}" $API/export/demandes/excel)" "200" "export employé -> 200"

echo
echo "=== 12. Anonymes ==="
check "$(A $API/demandes)" "403" "lecture anonyme -> 403"
check "$(A -X POST $API/demandes -H 'Content-Type: application/json' -d '{"designation":"x","motif":"y"}')" "403" "dépôt anonyme -> 403"
check "$(A -X PATCH $API/demandes/$ID1/decision -H 'Content-Type: application/json' -d '{"decision":"APPROUVEE"}')" "403" "décision anonyme -> 403"

echo
echo "=============================="
echo "  $PASS réussi(s), $FAIL échec(s)"
echo "=============================="
[ "$FAIL" = "0" ]
