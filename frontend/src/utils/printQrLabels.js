/**
 * Impression des étiquettes QR Code AUXIMAD.
 *
 * Réutilise le mécanisme déjà en place dans `QrCodeBlock` : une fenêtre vierge
 * ne contenant QUE les étiquettes, impression automatique une fois les images
 * chargées — l'interface applicative (barre latérale, boutons, modales, fonds)
 * n'est donc jamais imprimée.
 *
 * Deux formats :
 *  - `printQrLabel`      : étiquette unique 80 × 80 mm (fiche immobilisation) ;
 *  - `printQrLabelSheet` : planche A4, 3 colonnes d'étiquettes ≈ 60 × 60 mm,
 *                          séparées par des gouttières et cerclées d'un liseré
 *                          pointillé servant de repère de découpe, sous un
 *                          en-tête portant le logo, la date et le nombre.
 *
 * Chaque étiquette contient exactement trois éléments : le logo officiel
 * AUXIMAD, le QR Code et le code unique — repris tels quels depuis l'objet
 * immobilisation (donc identiques à ceux enregistrés en base).
 *
 * Le logo est importé en data URL (`?inline`) : une URL relative (`/assets/…`)
 * serait invalide dans une fenêtre `about:blank`.
 */
import logoAuximad from '../assets/Logo_auximad_simple.png?inline';
import { API_BASE_URL } from '../config';

/** Origine du backend — configuration centralisée (VITE_API_URL). */
const BASE_BACKEND = API_BASE_URL;

/** URL absolue d'une ressource (photo, QR…) servie par le backend. */
export const getUrlRessource = (url) => {
    if (!url) return null;
    // URL déjà résolue (backend distant, data URL, blob) : pas de préfixation.
    if (/^(https?:|data:|blob:)/.test(url)) return url;
    return `${BASE_BACKEND}${url}`;
};

/** Échappement HTML : `document.write` n'interprète pas les données brutes. */
const echapper = (valeur) =>
    String(valeur ?? '').replace(
        /[&<>"']/g,
        (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])
    );

/** Date d'impression en format français, injectée dans l'en-tête de planche. */
const dateFr = () =>
    new Date().toLocaleDateString('fr-FR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
    });

/* -------------------------------------------------------------------------- */
/*  Gabarit d'étiquette (partagé entre l'unitaire et la planche)               */
/* -------------------------------------------------------------------------- */

/** Uniquement la boîte d'étiquette : le habillage (page, grille) est ailleurs. */
const etiquetteHtml = (immo) => `
    <div class="etiquette">
        <img class="logo" src="${logoAuximad}" alt="AUXIMAD" />
        <img class="qr" src="${getUrlRessource(immo.qrCode)}" alt="QR Code ${echapper(immo.codeUnique)}" />
        <div class="code">${echapper(immo.codeUnique)}</div>
    </div>`;

/**
 * Styles communs à l'étiquette elle-même.
 *
 * Dimensions selon la variante :
 *  - `sheet` : QR 34 mm, logo 24 × 7 mm, code 10 pt — l'étiquette fait
 *    60 mm de côté pour 3 mm de marge intérieure (54 mm utiles).
 *  - `single`: QR 40 mm, logo 30 × 8 mm, code 12 pt — 68 mm de côté pour
 *    6 mm de marge (62 mm utiles).
 *
 * Le liseré est pointillé : c'est le repère de découpe, pas une bordure.
 */
const styleEtiquette = (variant) => {
    const sheet = variant === 'sheet';

    return `
    * { box-sizing: border-box; }
    body {
        margin: 0;
        font-family: Arial, Helvetica, sans-serif;
        color: #0f172a;
        -webkit-print-color-adjust: exact;
        print-color-adjust: exact;
    }
    .etiquette {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        text-align: center;
        background: #fff;
        border: 0.25mm dashed #94a3b8;
        border-radius: 2mm;
        padding: ${sheet ? '3mm' : '3mm'};
        overflow: hidden;
        break-inside: avoid;
        page-break-inside: avoid;
    }
    .logo {
        width: ${sheet ? '24mm' : '30mm'};
        max-height: ${sheet ? '7mm' : '8mm'};
        object-fit: contain;
        margin-bottom: ${sheet ? '2mm' : '2.5mm'};
    }
    .qr {
        width: ${sheet ? '34mm' : '40mm'};
        height: ${sheet ? '34mm' : '40mm'};
        display: block;
        /* Zone silencieuse physique : le PNG n'a qu'une marge d'un module,
           on ajoute du blanc autour pour garantir le scan à l'impression. */
        padding: 1mm;
        background: #fff;
    }
    .code {
        font-family: 'Courier New', Courier, monospace;
        font-weight: bold;
        letter-spacing: 0.02em;
        color: #0f172a;
        margin-top: ${sheet ? '2mm' : '2.5mm'};
        padding: 0.6mm 1.5mm;
        background: #f1f5f9;
        border-radius: 1mm;
        font-size: ${sheet ? '10pt' : '12pt'};
        line-height: 1.15;
        word-break: break-all;
    }`;
};

/**
 * En-tête de planche — visible uniquement en tête de la première page (flux
 * normal, donc non répété sur les suivantes).
 *
 * Logo + raison sociale à gauche, nombre d'étiquettes et date d'impression
 * à droite, séparés du corps par un filet.
 */
const entetePlancheHtml = (nbEtiquettes) => `
    <div class="entete">
        <div class="entete-gauche">
            <img class="entete-logo" src="${logoAuximad}" alt="AUXIMAD" />
            <div>
                <div class="entete-marque">AUXIMAD</div>
                <div class="entete-sous">Étiquettes de patrimoine</div>
            </div>
        </div>
        <div class="entete-droite">
            <div class="entete-meta">
                <span class="entete-cle">Étiquettes</span>${nbEtiquettes}
            </div>
            <div class="entete-meta">
                <span class="entete-cle">Imprimé le</span>${dateFr()}
            </div>
        </div>
    </div>`;

/** Styles de l'en-tête de planche (~14 mm de hauteur totale). */
const styleEntete = `
    .entete {
        display: flex;
        align-items: flex-end;
        justify-content: space-between;
        gap: 4mm;
        margin-bottom: 3mm;
        padding-bottom: 2mm;
        border-bottom: 0.3mm solid #cbd5e1;
        break-inside: avoid;
        page-break-inside: avoid;
    }
    .entete-gauche {
        display: flex;
        align-items: center;
        gap: 2.5mm;
    }
    .entete-logo {
        width: 22mm;
        max-height: 9mm;
        object-fit: contain;
    }
    .entete-marque {
        font-size: 12pt;
        font-weight: bold;
        letter-spacing: 0.06em;
        line-height: 1;
    }
    .entete-sous {
        font-size: 7.5pt;
        letter-spacing: 0.04em;
        color: #64748b;
        margin-top: 0.8mm;
    }
    .entete-droite {
        text-align: right;
        flex-shrink: 0;
    }
    .entete-meta {
        font-size: 8pt;
        color: #475569;
        line-height: 1.4;
    }
    .entete-cle {
        display: inline-block;
        min-width: 17mm;
        font-size: 6.5pt;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        color: #94a3b8;
    }`;

/* -------------------------------------------------------------------------- */
/*  Fenêtre d'impression                                                       */
/* -------------------------------------------------------------------------- */

/**
 * Ouvre une fenêtre, y écrit le document puis lance l'impression.
 * L'impression est déclenchée sur `load` : toutes les images (logo + QR)
 * sont garanties affichées avant d'imprimer.
 *
 * @returns {boolean} false si la fenêtre a été bloquée par le navigateur.
 */
const ouvrirEtImprimer = (titre, corps, style, { fermerApres = false } = {}) => {
    const fenetre = window.open('', '_blank', 'width=980,height=1080');
    if (!fenetre) return false;

    fenetre.document.open();
    fenetre.document.write(`<!DOCTYPE html>
<html lang="fr">
<head>
    <meta charset="utf-8" />
    <title>${titre}</title>
    <style>${style}</style>
</head>
<body>
${corps}
<script>
    window.addEventListener('load', function () {
        setTimeout(function () {
            window.print();
            ${fermerApres ? 'window.close();' : ''}
        }, 400);
    });
</script>
</body>
</html>`);
    fenetre.document.close();
    return true;
};

/* -------------------------------------------------------------------------- */
/*  API publique                                                               */
/* -------------------------------------------------------------------------- */

/**
 * Planche A4 : une seule fenêtre contenant TOUS les biens, disposés en
 * 3 colonnes d'étiquettes de 60 × 60 mm (≈ 12 par page), gouttières de 4 mm.
 * L'en-tête (logo, nombre, date) n'apparaît qu'en tête de première page.
 *
 * @param {Array} immobilisations biens à imprimer — doivent tous posséder `qrCode`
 * @returns {boolean} false si la fenêtre a été bloquée
 */
export const printQrLabelSheet = (immobilisations) => {
    const biens = (immobilisations || []).filter((i) => i && i.qrCode);
    if (biens.length === 0) return false;

    const style = `
@page { size: A4; margin: 8mm; }
${styleEtiquette('sheet')}
${styleEntete}
.planche {
    display: grid;
    /* 3 × 60 mm + 2 × 4 mm de gouttière = 188 mm, centré dans les 194 mm
       utiles d'une A4 en marge 8 mm : étiquettes carrées 60 × 60 mm. */
    grid-template-columns: repeat(3, 60mm);
    justify-content: center;
    gap: 4mm;
}
.planche .etiquette { height: 60mm; }`;

    const corps = `${entetePlancheHtml(biens.length)}
<div class="planche">
${biens.map(etiquetteHtml).join('\n')}
</div>`;

    return ouvrirEtImprimer(
        `Étiquettes QR Code (${biens.length})`,
        corps,
        style
    );
};

/**
 * Étiquette unique au format 80 × 80 mm (format historique conservé).
 *
 * @param {Object} immobilisation bien possédant un `qrCode`
 * @returns {boolean} false si la fenêtre a été bloquée
 */
export const printQrLabel = (immobilisation) => {
    if (!immobilisation || !immobilisation.qrCode) return false;

    const style = `
@page { size: 80mm 80mm; margin: 0; }
${styleEtiquette('single')}
body {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 6mm;
}
.etiquette { width: 68mm; height: 68mm; }`;

    return ouvrirEtImprimer(
        `Étiquette - ${immobilisation.codeUnique}`,
        etiquetteHtml(immobilisation),
        style,
        { fermerApres: true }
    );
};
