/**
 * Ligne de fiche — grammaire unique des modales de détail
 * (immobilisation, mouvement, demande, entrée de journal).
 *
 * - Icône en aplat neutre sans bordure : la couleur reste réservée aux
 *   statuts et aux actions (règle de palette).
 * - Libellé en capitales fines (`section-label`), valeur forte à gauche.
 * - Valeur absente → « — » pâle au lieu d'un « - » dur.
 * - `children` pour un contenu riche (badge, lien, valeur colorée) :
 *   le repli « — » est alors neutralisé et le tronquage retiré.
 * - `sub` pour une seconde ligne discrète (ex. service du demandeur).
 *
 * @param {object}    props
 * @param {Function}  props.icon    icône lucide-react (obligatoire)
 * @param {string}    props.label   libellé de champ
 * @param {string}    [props.value] valeur textuelle simple
 * @param {string}    [props.sub]   seconde ligne, discrète
 * @param {Node}      [props.children] contenu riche en lieu place de `value`
 */
const DetailItem = ({ icon: Icon, label, value, sub, children }) => {
    const riche = children !== undefined && children !== null;
    const vide = value == null || value === '' || value === '-';

    return (
        <div className="flex items-start gap-2.5">
            <div className="w-8 h-8 rounded-control bg-surface-3 flex items-center justify-center shrink-0 mt-0.5">
                <Icon size={14} className="text-ink-muted" />
            </div>
            <div className="min-w-0">
                <p className="section-label">{label}</p>
                <p
                    className={`text-sm font-medium ${
                        riche ? 'text-ink' : `truncate ${vide ? 'text-ink-subtle' : 'text-ink'}`
                    }`}
                >
                    {riche ? children : (vide ? '—' : value)}
                </p>
                {sub && <p className="text-xs text-ink-muted truncate mt-0.5">{sub}</p>}
            </div>
        </div>
    );
};

export default DetailItem;
