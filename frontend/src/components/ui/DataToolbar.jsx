import { useState } from 'react';
import { createPortal } from 'react-dom';
import { Search, SlidersHorizontal, X, Plus, RotateCw } from 'lucide-react';
import useAutoSearch from '../../hooks/useAutoSearch';

/**
 * Barre d'outils des listes (recherche + filtres + actions).
 *
 * - Desktop : recherche à gauche, actions à droite, filtres toujours visibles.
 * - Mobile  : recherche pleine largeur, filtres repliés dans une feuille
 *             en bas d'écran, bouton d'action en pleine largeur.
 *
 * La recherche est INSTANTANÉE : les résultats se mettent à jour à la frappe
 * (300 ms de stabilisation). Entrée et le bouton « Rechercher » restent
 * disponibles pour forcer un appel immédiat.
 */
const DataToolbar = ({
    keyword,
    onKeywordChange,
    onSearch,
    placeholder = 'Rechercher...',
    actionLabel,
    onAction,
    actionIcon: ActionIcon = Plus,
    secondaryAction,
    loading = false,
    onRefresh,
    filters = null,
    activeFilterCount = 0,
    className = '',
}) => {
    const [filtersOpen, setFiltersOpen] = useState(false);

    // Recherche instantanée : les résultats s'affichent à la frappe, sans
    // attendre Entrée ni le bouton (voir useAutoSearch).
    useAutoSearch(keyword ?? '', onSearch);

    const submitSearch = (e) => {
        e?.preventDefault?.();
        onSearch?.();
    };

    const hasFilters = Boolean(filters);

    return (
        <>
            <div className={`card p-3 sm:p-4 ${className}`}>
                <div className="toolbar">
                    {/* Recherche */}
                    <form onSubmit={submitSearch} className="search-field flex items-center gap-2">
                        <div className="relative flex-1 min-w-0">
                            <Search
                                size={16}
                                className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-subtle pointer-events-none"
                            />
                            <input
                                type="text"
                                value={keyword ?? ''}
                                onChange={(e) => onKeywordChange?.(e.target.value)}
                                placeholder={placeholder}
                                className="input pl-9 pr-3"
                                aria-label={placeholder}
                            />
                        </div>

                        <button
                            type="button"
                            onClick={onSearch}
                            className="btn-secondary btn-md hidden sm:inline-flex"
                            disabled={loading}
                        >
                            Rechercher
                        </button>

                        {hasFilters && (
                            <button
                                type="button"
                                onClick={() => setFiltersOpen(true)}
                                className="btn-secondary btn-md relative sm:hidden"
                                aria-label="Filtres"
                            >
                                <SlidersHorizontal size={16} />
                                {activeFilterCount > 0 && (
                                    <span className="absolute -top-1.5 -right-1.5 min-w-[18px] h-[18px] px-1 bg-brand-500 text-white text-[10px] font-bold rounded-full flex items-center justify-center">
                                        {activeFilterCount}
                                    </span>
                                )}
                            </button>
                        )}
                    </form>

                    {/* Actions */}
                    <div className="flex flex-col sm:flex-row sm:items-center gap-2 sm:shrink-0">
                        {hasFilters && (
                            <div className="hidden sm:flex items-center gap-2 flex-wrap">
                                {filters}
                            </div>
                        )}

                        {onRefresh && (
                            <button
                                type="button"
                                onClick={onRefresh}
                                className="btn-secondary btn-md sm:px-3"
                                title="Actualiser"
                                aria-label="Actualiser"
                            >
                                <RotateCw size={16} className={loading ? 'animate-spin-slow' : ''} />
                            </button>
                        )}

                        {secondaryAction}

                        {actionLabel && (
                            <button
                                type="button"
                                onClick={onAction}
                                className="btn-primary btn-md w-full sm:w-auto"
                            >
                                <ActionIcon size={16} />
                                {actionLabel}
                            </button>
                        )}
                    </div>
                </div>
            </div>

            {/* Feuille de filtres mobile */}
            {hasFilters &&
                filtersOpen &&
                createPortal(
                    <div
                        className="fixed inset-0 flex items-end sm:items-center justify-center bg-black/60 backdrop-blur-sm z-[99999] animate-fade-in"
                        onClick={() => setFiltersOpen(false)}
                    >
                        <div
                            className="card w-full sm:max-w-md rounded-b-none sm:rounded-card max-h-[80vh] flex flex-col animate-slide-up"
                            onClick={(e) => e.stopPropagation()}
                        >
                            <div className="card-head">
                                <h2 className="card-title">Filtres</h2>
                                <button
                                    onClick={() => setFiltersOpen(false)}
                                    className="btn-ghost btn-icon"
                                    aria-label="Fermer"
                                >
                                    <X size={18} />
                                </button>
                            </div>
                            <div className="p-4 overflow-y-auto space-y-4">{filters}</div>
                            <div className="p-4 border-t border-line">
                                <button
                                    onClick={() => setFiltersOpen(false)}
                                    className="btn-primary btn-md w-full"
                                >
                                    Appliquer
                                </button>
                            </div>
                        </div>
                    </div>,
                    document.body
                )}
        </>
    );
};

/** Sélecteur de filtre standardisé pour DataToolbar. */
export const FilterSelect = ({ label, value, onChange, options = [] }) => (
    <label className="flex items-center gap-2">
        {label && <span className="text-xs text-ink-muted font-medium">{label}</span>}
        <select
            value={value}
            onChange={(e) => onChange(e.target.value)}
            className="input py-1.5 pr-8 text-xs font-medium w-auto min-w-[130px]"
        >
            {options.map((opt) => (
                <option key={opt.value} value={opt.value}>
                    {opt.label}
                </option>
            ))}
        </select>
    </label>
);

/** Compteur de résultats aligné sous l'outil de recherche. */
export const ResultCount = ({ count, noun = 'résultat' }) => (
    <p className="text-xs text-ink-muted">
        <span className="font-semibold text-ink">{count}</span> {noun}
        {count > 1 ? 's' : ''}
    </p>
);

export default DataToolbar;
