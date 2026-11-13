import { formatMontant } from '../../utils/format';

/**
 * Plan d'amortissement linéaire.
 * Tableau sur `sm+`, cartes empilées sous `sm` (5 colonnes illisibles sur mobile).
 */
const AmortissementTable = ({ lignes, vncActuelle }) => {
    if (!lignes || lignes.length === 0) {
        return (
            <div className="empty-state bg-surface-2 dark:bg-slate-800/50 rounded-panel border border-dashed border-line-strong py-10">
                <p className="text-sm text-ink-muted">Aucun plan d'amortissement calculé.</p>
            </div>
        );
    }

    const SECONDAIRES = [
        { key: 'valeurInitiale', label: 'Valeur initiale', render: (l) => formatMontant(l.valeurInitiale), tone: null },
        { key: 'dotation', label: 'Dotation', render: (l) => formatMontant(l.dotationAnnuelle), tone: 'danger' },
        { key: 'cumul', label: 'Cumul', render: (l) => formatMontant(l.amortissementCumule), tone: null },
        { key: 'vnc', label: 'VNC', render: (l) => formatMontant(l.valeurNetteComptable), tone: 'brand' },
    ];

    return (
        <div className="rounded-panel border border-line overflow-hidden bg-surface">
            {/* Tableau : sm et plus */}
            <div className="hidden sm:block overflow-x-auto">
                <table className="w-full border-collapse text-sm">
                    <thead>
                        <tr className="bg-surface-2 border-b border-line">
                            <th className="section-label text-left px-3 py-2.5">Année</th>
                            <th className="section-label text-right px-3 py-2.5">Valeur initiale</th>
                            <th className="section-label text-right px-3 py-2.5">Dotation</th>
                            <th className="section-label text-right px-3 py-2.5">Cumul</th>
                            <th className="section-label text-right px-3 py-2.5">VNC</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-line">
                        {lignes.map((ligne) => (
                            <tr key={ligne.id || ligne.annee} className="hover:bg-surface-2 transition-colors">
                                <td className="px-3 py-2.5 font-semibold text-ink">{ligne.annee}</td>
                                <td className="px-3 py-2.5 text-right text-ink-muted">
                                    {formatMontant(ligne.valeurInitiale)}
                                </td>
                                <td className="px-3 py-2.5 text-right font-medium text-danger-600 dark:text-danger-300">
                                    {formatMontant(ligne.dotationAnnuelle)}
                                </td>
                                <td className="px-3 py-2.5 text-right text-ink-muted">
                                    {formatMontant(ligne.amortissementCumule)}
                                </td>
                                <td className="px-3 py-2.5 text-right font-semibold text-brand-600 dark:text-brand-300">
                                    {formatMontant(ligne.valeurNetteComptable)}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {/* Cartes : sous sm */}
            <ul className="sm:hidden divide-y divide-line">
                {lignes.map((ligne) => (
                    <li key={ligne.id || ligne.annee} className="p-4 space-y-2">
                        <p className="text-sm font-semibold text-ink">
                            Année <span className="text-brand-600 dark:text-brand-300">{ligne.annee}</span>
                        </p>
                        <dl className="grid grid-cols-2 gap-x-4 gap-y-2">
                            {SECONDAIRES.map((row) => (
                                <div key={row.key} className="min-w-0">
                                    <dt className="section-label">{row.label}</dt>
                                    <dd
                                        className={`text-sm mt-0.5 truncate font-medium ${
                                            row.tone === 'brand'
                                                ? 'text-brand-600 dark:text-brand-300'
                                                : row.tone === 'danger'
                                                  ? 'text-danger-600 dark:text-danger-300'
                                                  : 'text-ink-muted'
                                        }`}
                                    >
                                        {row.render(ligne)}
                                    </dd>
                                </div>
                            ))}
                        </dl>
                    </li>
                ))}
            </ul>

            {/* VNC actuelle */}
            {vncActuelle != null && (
                <div className="px-4 py-3 bg-surface-3 border-t border-line flex justify-between items-center gap-3">
                    <span className="text-sm font-medium text-ink">VNC actuelle</span>
                    <span className="text-base font-bold text-brand-600 dark:text-brand-300">
                        {formatMontant(vncActuelle)}
                    </span>
                </div>
            )}
        </div>
    );
};

export default AmortissementTable;
