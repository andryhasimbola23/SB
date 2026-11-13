/**
 * Source de vérité pour toutes les couleurs sémantiques de l'application.
 *
 * Objectif : une seule palette (vert/rouge/ambre + neutres) et
 * un seul endroit où les classes sont définies. Aucune page ne doit
 * écrire `bg-green-100 text-green-800` en dur.
 */

// Libellés + variantes pour chaque statut métier.
export const STATUTS = {
    ACTIF: { label: 'Actif', tone: 'brand' },
    EN_SERVICE: { label: 'En service', tone: 'brand' },
    NOUVEAU: { label: 'Nouveau', tone: 'brand' },
    MAUVAIS_ETAT: { label: 'Mauvais état', tone: 'warning' },
    HORS_SERVICE: { label: 'Hors service', tone: 'danger' },
    CEDE: { label: 'Cédé', tone: 'neutral' },
    REBUTE: { label: 'Rebut', tone: 'danger' },
    INACTIF: { label: 'Inactif', tone: 'danger' },
};

export const ROLES = {
    ADMIN: { label: 'Administrateur', tone: 'brand' },
    EMPLOYE: { label: 'Employé', tone: 'neutral' },
};

/**
 * Statuts d'une demande d'immobilisation.
 * Aucune couleur nouvelle : ambre pour ce qui attend, vert pour l'accord,
 * rouge pour le refus, neutre pour ce qui est retiré.
 */
export const STATUTS_DEMANDE = {
    EN_ATTENTE: { label: 'En attente', tone: 'warning' },
    APPROUVEE: { label: 'Approuvée', tone: 'brand' },
    REJETTEE: { label: 'Rejetée', tone: 'danger' },
    ANNULEE: { label: 'Annulée', tone: 'neutral' },
};

/** Priorités d'une demande : l'urgence sort du rang, le reste reste neutre. */
export const PRIORITES_DEMANDE = {
    URGENTE: { label: 'Urgente', tone: 'danger' },
    NORMALE: { label: 'Normale', tone: 'quiet' },
};

export const TYPES_MATERIEL = {
    INFORMATIQUE: { label: 'Informatique', tone: 'neutral' },
    BUREAUTIQUE: { label: 'Bureautique', tone: 'neutral' },
    MOBILIER: { label: 'Mobilier', tone: 'neutral' },
    VEHICULE: { label: 'Véhicule', tone: 'neutral' },
    OUTILLAGE: { label: 'Outillage', tone: 'neutral' },
    AUTRE: { label: 'Autre', tone: 'quiet' },
};

/**
 * Types de mouvement.
 * Les couleurs restent distinctes car l'utilisateur doit distinguer
 * visuellement 4 types dans un historique — mais elles restent
 * dans la palette approuvée (vert / ambre / rouge / neutre).
 */
export const TYPES_MOUVEMENT = {
    ENTREE: {
        label: 'Entrée',
        tone: 'brand',
        icon: 'Package',
    },
    TRANSFERT: {
        label: 'Transfert',
        tone: 'neutral',
        icon: 'ArrowRightLeft',
    },
    /**
     * Livraison d'un bien déjà dans le service de destination — issue d'une
     * demande approuvée. Rien ne bouge sur le bien (pas de code réémis, pas de
     * QR invalidés) : seule la remise est tracée. Vert comme une entrée, puisque
     * le sens est le même — un bien arrive à quelqu'un — et l'icône sépare les
     * deux : une affectation vise une personne, une entrée un stock.
     * Non proposé dans la modale de mouvement manuelle : elle n'a de sens que
     * comme conséquence d'une demande.
     */
    AFFECTATION: {
        label: 'Affectation',
        tone: 'brand',
        icon: 'UserPlus',
    },
    CESSION: {
        label: 'Cession',
        tone: 'warning',
        icon: 'Send',
    },
    REBUT: {
        label: 'Rebut',
        tone: 'danger',
        icon: 'Trash2',
    },
};

/**
 * Classes Tailwind par "tone".
 * Complète les utilitaires `.badge-*` définis dans index.css
 * pour les contextes où une simple couleur de fond/texte suffit.
 */
export const TONE_CLASSES = {
    brand: {
        text: 'text-brand-600 dark:text-brand-300',
        bg: 'bg-brand-50 dark:bg-brand-500/15',
        border: 'border-brand-200 dark:border-brand-500/30',
        solid: 'bg-brand-500 hover:bg-brand-600 text-white',
        icon: 'bg-brand-50 dark:bg-brand-500/15 text-brand-600 dark:text-brand-300',
        badge: 'badge-brand',
    },
    danger: {
        text: 'text-danger-600 dark:text-danger-300',
        bg: 'bg-danger-50 dark:bg-danger-500/15',
        border: 'border-danger-200 dark:border-danger-500/30',
        solid: 'bg-danger-500 hover:bg-danger-600 text-white',
        icon: 'bg-danger-50 dark:bg-danger-500/15 text-danger-600 dark:text-danger-300',
        badge: 'badge-danger',
    },
    warning: {
        text: 'text-amber-600 dark:text-amber-300',
        bg: 'bg-amber-50 dark:bg-amber-500/15',
        border: 'border-amber-200 dark:border-amber-500/30',
        solid: 'bg-amber-500 hover:bg-amber-600 text-white',
        icon: 'bg-amber-50 dark:bg-amber-500/15 text-amber-600 dark:text-amber-300',
        badge: 'badge-warning',
    },
    neutral: {
        text: 'text-slate-700 dark:text-slate-300',
        bg: 'bg-slate-100 dark:bg-slate-700/60',
        border: 'border-slate-200 dark:border-slate-600',
        solid: 'bg-slate-800 hover:bg-slate-700 text-white',
        icon: 'bg-slate-100 dark:bg-slate-700/60 text-slate-600 dark:text-slate-300',
        badge: 'badge-neutral',
    },
    quiet: {
        text: 'text-slate-500 dark:text-slate-400',
        bg: 'bg-slate-50 dark:bg-slate-800',
        border: 'border-line',
        solid: 'bg-slate-500 hover:bg-slate-600 text-white',
        icon: 'bg-slate-50 dark:bg-slate-800 text-slate-400 dark:text-slate-500',
        badge: 'badge-quiet',
    },
};

/** Résout la configuration d'un statut métier. */
export const statutConfig = (statut) =>
    STATUTS[statut] || { label: statut || '-', tone: 'neutral' };

/** Résout la configuration d'un type de mouvement. */
export const mouvementConfig = (type) =>
    TYPES_MOUVEMENT[type] || { label: type || '-', tone: 'neutral', icon: 'Package' };

/** Résout la configuration d'un statut de demande. */
export const statutDemandeConfig = (statut) =>
    STATUTS_DEMANDE[statut] || { label: statut || '-', tone: 'neutral' };

/** Résout la configuration d'une priorité de demande. */
export const prioriteDemandeConfig = (priorite) =>
    PRIORITES_DEMANDE[priorite] || PRIORITES_DEMANDE.NORMALE;

/** Retourne les classes d'un tone donné. */
export const tone = (name) => TONE_CLASSES[name] || TONE_CLASSES.neutral;
