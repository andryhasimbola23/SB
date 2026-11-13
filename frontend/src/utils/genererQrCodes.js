/**
 * Génération en lot des QR Codes manquants, en vue d'une impression groupée.
 *
 * L'impression groupée (`printQrLabelSheet`) exige que chaque bien sélectionné
 * possède déjà un QR Code. Plutôt que d'écarter les biens concernés, on les
 * fait générer à la volée via `POST /api/immobilisations/{id}/generer-qr` —
 * route déjà existante, déjà utilisée par le bouton « Régénérer » de la fiche.
 *
 * Deux propriétés importantes pour l'appelant :
 *  - **un échec n'interrompt jamais le lot** : chaque bien est traité de façon
 *    isolée et consigné dans `echecs` ;
 *  - **le nom de fichier produit est dérivé du code unique** (`/` → `_`), donc
 *    identique à celui déjà en base : régénérer est idempotent, aucune URL
 *    existante n'est cassée.
 *
 * La concurrence est bornée (5 par défaut) pour ne pas saturer le serveur quand
 * « Tout sélectionner » porte sur plusieurs dizaines de biens.
 */

/**
 * Génère les QR Codes des biens qui n'en ont pas encore.
 *
 * @param {Array} biens biens sélectionnés ; ceux possédant déjà `qrCode` sont ignorés
 * @param {Object} [options]
 * @param {Function} [options.genererUn] appel API `(id) => Promise<ImmobilisationDto>`
 * @param {number} [options.concurrence] nombre d'appels simultanés (défaut 5, minimum 1)
 * @param {Function} [options.onProgress] `(faits, total)` appelé après chaque bien traité
 * @returns {Promise<{regeneres: Array, echecs: Array}>} les DTO mis à jour (dans
 *          l'ordre d'entrée) et les biens en échec `{ id, codeUnique, message }`
 */
export const genererQrCodesEnLot = async (
    biens,
    { genererUn, concurrence = 5, onProgress } = {}
) => {
    const aGenerer = (biens || []).filter((bien) => bien && !bien.qrCode);
    const total = aGenerer.length;

    // Résultats indexés : on restitue l'ordre de l'entrée, pas l'ordre de
    // résolution des promesses, pour que le rendu soit déterministe.
    const majParIndex = new Array(total).fill(null);
    const echecParIndex = new Array(total).fill(null);

    const signalerEchec = (index, bien, message) => {
        echecParIndex[index] = {
            id: bien.id,
            codeUnique: bien.codeUnique,
            message,
        };
    };

    if (total === 0) return { regeneres: [], echecs: [] };

    // Sans moyen de générer, on le dit plutôt que de faire semblant.
    if (typeof genererUn !== 'function') {
        aGenerer.forEach((bien, index) =>
            signalerEchec(index, bien, 'Aucun moyen de générer le QR Code')
        );
        return { regeneres: [], echecs: echecParIndex.filter(Boolean) };
    }

    // Repli sur 5 si la concurrence demandée est absente, non numérique ou
    // strictement négative : mieux vaut une valeur sûre qu'une valeur folle.
    const demande = Math.floor(Number(concurrence));
    const limite = Number.isFinite(demande) && demande >= 1 ? demande : 5;
    let curseur = 0;
    let faits = 0;

    const ouvrier = async () => {
        for (;;) {
            const index = curseur++;
            if (index >= total) return;

            const bien = aGenerer[index];
            try {
                const maj = await genererUn(bien.id);
                if (maj && maj.qrCode) majParIndex[index] = maj;
                else signalerEchec(index, bien, 'QR Code non renvoyé par le serveur');
            } catch (err) {
                signalerEchec(
                    index,
                    bien,
                    err?.response?.data?.message ||
                        err?.message ||
                        'Erreur inconnue lors de la génération'
                );
            } finally {
                faits += 1;
                if (typeof onProgress === 'function') onProgress(faits, total);
            }
        }
    };

    await Promise.all(
        Array.from({ length: Math.min(limite, total) }, () => ouvrier())
    );

    return {
        regeneres: majParIndex.filter(Boolean),
        echecs: echecParIndex.filter(Boolean),
    };
};

export default genererQrCodesEnLot;
