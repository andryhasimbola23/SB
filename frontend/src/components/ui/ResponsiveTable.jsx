import { Edit, Trash2 } from 'lucide-react';

/**
 * Tableau adaptatif : tableau sur `md+`, cartes empilées en dessous.
 *
 * Déclare les colonnes une seule fois, la disposition change selon la largeur.
 *
 * @param {Array} columns
 *   { key, label, render(item), align = 'left'|'right', hideBelow = 'md'|'lg'|'xl',
 *     primary = false (colonne de titre des cartes),
 *     compact = false (non affichée dans les cartes), actions = false }
 * @param {Array}  data     - lignes
 * @param {string|Function} rowKey
 * @param {React.ReactNode} empty - état vide (optionnel)
 * @param {boolean} dense - padding réduit
 */
const ResponsiveTable = ({
    columns,
    data,
    rowKey = (row, i) => row?.id ?? i,
    empty,
    dense = false,
    onRowClick,
    className = '',
}) => {
    if (!data || data.length === 0) {
        if (empty) return empty;
        return (
            <div className="empty-state">
                <div className="empty-state-icon">—</div>
                <p className="text-sm text-ink-muted">Aucune donnée à afficher</p>
            </div>
        );
    }

    const pad = dense ? 'px-4 py-2.5' : 'px-4 sm:px-5 py-4';
    const primaryCols = columns.filter((c) => c.primary);
    const secondaryCols = columns.filter((c) => !c.primary && !c.actions);
    const actionCols = columns.filter((c) => c.actions);

    /* ---------- Tableau (md et plus) ---------- */
    const TableView = () => (
        <div className="hidden md:block overflow-x-auto">
            <table className="w-full border-collapse">
                <thead>
                    <tr className="bg-surface-2 border-b border-line">
                        {columns.map((col) => (
                            <th
                                key={col.key}
                                className={`section-label text-left ${pad} ${
                                    col.align === 'right' ? 'text-right' : 'text-left'
                                } ${col.hideBelow ? `${col.hideBelow}:table-cell hidden` : ''}`}
                            >
                                {col.label}
                            </th>
                        ))}
                    </tr>
                </thead>
                <tbody className="divide-y divide-line">
                    {data.map((row, index) => (
                        <tr
                            key={rowKey(row, index)}
                            onClick={onRowClick ? () => onRowClick(row) : undefined}
                            className={`transition-colors hover:bg-surface-2 ${
                                onRowClick ? 'cursor-pointer' : ''
                            }`}
                        >
                            {columns.map((col) => (
                                <td
                                    key={col.key}
                                    className={`text-sm text-ink-muted ${pad} ${
                                        col.align === 'right' ? 'text-right' : ''
                                    } ${col.hideBelow ? `${col.hideBelow}:table-cell hidden` : ''}`}
                                >
                                    {col.render ? col.render(row, index) : row[col.key]}
                                </td>
                            ))}
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );

    /* ---------- Cartes empilées (sous md) ---------- */
    const CardView = () => (
        <ul className="md:hidden divide-y divide-line">
            {data.map((row, index) => (
                <li key={rowKey(row, index)} className="p-4 space-y-3">
                    {/* Titre = colonnes primaires */}
                    <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0 space-y-1">
                            {(primaryCols.length ? primaryCols : columns.slice(0, 1)).map((col) => (
                                <div key={col.key} className="min-w-0">
                                    {col.render ? col.render(row, index) : row[col.key]}
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Détails = colonnes secondaires en label/valeur */}
                    {secondaryCols.length > 0 && (
                        <dl className="grid grid-cols-2 gap-x-4 gap-y-2">
                            {secondaryCols.map((col) => (
                                <div key={col.key} className="min-w-0">
                                    <dt className="section-label">{col.label}</dt>
                                    <dd className="text-sm text-ink-muted mt-0.5 truncate">
                                        {col.render ? col.render(row, index) : row[col.key]}
                                    </dd>
                                </div>
                            ))}
                        </dl>
                    )}

                    {/* Actions = dernière ligne, pleine largeur */}
                    {actionCols.length > 0 && (
                        <div className="flex justify-end gap-2 pt-2 border-t border-line/60">
                            {/* Clé obligatoire : col.render renvoie déjà des
                                éléments, rendus dans un tableau par React. */}
                            {actionCols.map((col) => (
                                <div key={col.key} className="inline-flex">
                                    {col.render(row, index)}
                                </div>
                            ))}
                        </div>
                    )}
                </li>
            ))}
        </ul>
    );

    return (
        <div className={`overflow-hidden ${className}`}>
            <TableView />
            <CardView />
        </div>
    );
};

/**
 * Bouton d'action d'icône standardisé pour les tableaux.
 * `variant` : edit | delete | neutral | brand
 * Les couleurs de survol vivent dans `index.css` (.act-*).
 */
export const RowAction = ({ icon: Icon, label, onClick, variant = 'edit' }) => {
    const variants = {
        edit: 'act-quiet-bg',
        delete: 'act-danger-bg',
        neutral: 'act-quiet-bg',
        brand: 'act-brand',
    };

    return (
        <button
            type="button"
            onClick={onClick}
            title={label}
            aria-label={label}
            className={`p-2 rounded-panel transition-all duration-150 active:scale-90 ${variants[variant] || variants.edit}`}
        >
            <Icon size={16} />
        </button>
    );
};

/** Paire d'actions modifier / supprimer, prête à l'emploi. */
export const RowActions = ({ onEdit, onDelete }) => (
    <div className="inline-flex items-center gap-1 justify-end">
        {onEdit && <RowAction icon={Edit} label="Modifier" onClick={onEdit} variant="edit" />}
        {onDelete && <RowAction icon={Trash2} label="Supprimer" onClick={onDelete} variant="delete" />}
    </div>
);

/** Enveloppe "carte" d'un tableau. */
export const TableCard = ({ children, className = '' }) => (
    <div className={`card overflow-hidden ${className}`}>{children}</div>
);

export default ResponsiveTable;
