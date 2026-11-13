/**
 * Helpers de formatage partagés par toute l'application.
 *
 * Ces fonctions étaient recopiées (avec de légères variations) dans une
 * dizaine de fichiers : une seule définition vit ici, les variantes de format
 * de date sont nommées explicitement pour rester lisibles.
 */
import { API_BASE_URL } from '../config';

/** Montant en ariary : « 1 250 000 Ar » (fallback si null/undefined). */
export const formatMontant = (montant, fallback = '-') =>
    montant == null ? fallback : Number(montant).toLocaleString('fr-FR') + ' Ar';

/** Date courte : « 12 mars 2026 ». */
export const formatDate = (dateStr, fallback = '-') => {
    if (!dateStr) return fallback;
    return new Date(dateStr).toLocaleDateString('fr-FR', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
    });
};

/** Date + heure (mois en toutes lettres) : « 12 mars 2026 14:30 ». */
export const formatDateHeure = (dateStr, fallback = '-') => {
    if (!dateStr) return fallback;
    return new Date(dateStr).toLocaleDateString('fr-FR', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    });
};

/** Date + heure (chiffres) : « 12/03/2026 14:30 ». */
export const formatDateTime = (dateStr, fallback = '-') => {
    if (!dateStr) return fallback;
    return new Date(dateStr).toLocaleDateString('fr-FR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    });
};

/**
 * URL absolue d'une ressource (photo, QR…) servie par le backend :
 * les URLs stockées en base sont relatives (`/uploads/...`), l'origine
 * provient de la configuration centralisée.
 */
export const getPhotoUrl = (url) => {
    if (!url) return null;
    if (url.startsWith('http')) return url;
    return `${API_BASE_URL}${url}`;
};
