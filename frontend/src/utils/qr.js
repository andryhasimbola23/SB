/**
 * Utilitaires de scan QR partagés (scanner global + campagnes d'inventaire).
 */

/**
 * Extrait le code unique d'un contenu QR scanné.
 *
 * Le backend encode « CODE | ETAT : X | STATUT : Y » pour les QR régénérés,
 * mais les étiquettes déjà imprimées n'encodent que le code seul. Découper
 * sur le premier « | » accepte les deux formats, sans réimpression forcée.
 *
 * Un code unique ne peut jamais contenir « | » (gabarit
 * [Agence]/[Année]/[Service]/[Code]/[Type]/[N°]) : la découpe est sans ambiguïté.
 */
export const extraireCodeUnique = (contenu) => String(contenu ?? '').split('|')[0].trim();

/**
 * Feedback d'un scan accepté : biper (Web Audio) + vibration si disponible.
 * Silencieux si l'audio n'est pas autorisé par le navigateur — jamais bloquant.
 */
export const bipScan = () => {
    try {
        const AudioCtx = window.AudioContext || window.webkitAudioContext;
        if (AudioCtx) {
            const ctx = new AudioCtx();
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            osc.frequency.value = 1200;
            gain.gain.value = 0.08;
            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.start();
            osc.stop(ctx.currentTime + 0.12);
            osc.onended = () => ctx.close().catch(() => {});
        }
    } catch {
        // Audio indisponible : le scan reste confirmé visuellement.
    }
    try {
        navigator.vibrate?.(120);
    } catch {
        // Vibration indisponible (desktop) : ignoré.
    }
};
