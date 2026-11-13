import { tone as toneOf, STATUTS, TYPES_MOUVEMENT, statutDemandeConfig, prioriteDemandeConfig } from '../../theme/toneStyles';

/**
 * Badge sémantique — la seule façon d'afficher un statut.
 *
 * @param {string} tone  - brand | danger | warning | neutral | quiet
 * @param {string} label - texte affiché
 * @param {boolean} dot  - affiche le point coloré (par défaut oui)
 */
const Badge = ({ tone = 'neutral', label, dot = true, className = '' }) => {
    const t = toneOf(tone);

    return (
        <span className={`badge ${dot ? '' : 'badge-plain'} ${t.badge} ${className}`}>
            {label}
        </span>
    );
};

/** Badge dérivé d'une clé de STATUTS (ACTIF, REBUTE, ...). */
export const StatutBadge = ({ statut, label }) => {
    const entry = STATUTS[statut];
    return (
        <Badge
            tone={entry?.tone || 'neutral'}
            label={label || entry?.label || statut || '-'}
        />
    );
};

/** Badge pour un type de mouvement (ENTREE, TRANSFERT, ...). */
export const MouvementBadge = ({ type, label }) => {
    const entry = TYPES_MOUVEMENT[type];
    return (
        <Badge
            tone={entry?.tone || 'neutral'}
            label={label || entry?.label || type || '-'}
        />
    );
};

/** Badge pour un statut de demande (EN_ATTENTE, APPROUVEE, ...). */
export const DemandeBadge = ({ statut, label }) => {
    const entry = statutDemandeConfig(statut);
    return <Badge tone={entry.tone} label={label || entry.label || statut || '-'} />;
};

/** Badge pour une priorité de demande (NORMALE, URGENTE). */
export const PrioriteBadge = ({ priorite, label }) => {
    const entry = prioriteDemandeConfig(priorite);
    return <Badge tone={entry.tone} label={label || entry.label || priorite || '-'} />;
};

export default Badge;
