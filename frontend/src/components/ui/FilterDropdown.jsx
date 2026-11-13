import { useState, useRef, useEffect, useMemo } from 'react';
import { createPortal } from 'react-dom';
import { Check, ChevronDown, Search, X } from 'lucide-react';

/**
 * Sélecteur de filtre déroulant professionnel.
 *
 * - Déclencheur : bouton `btn btn-secondary` — même gabarit que les autres
 *   boutons de la barre d'outils (Export, Nouveau). Icône + valeur active.
 *   `block` rend ce déclencheur pleine largeur : utile dans une barre d'outils
 *   mobile où le filtre occupe la place restante d'une ligne à débordement.
 * - Desktop : menu ancré sous le déclencheur (champ de recherche optionnel).
 * - Mobile  : feuille en bas d'écran via portal, cohérent avec Modal/DataToolbar.
 * - Accessible : `aria-expanded`, `aria-haspopup`, fermeture par Échap,
 *   clic extérieur et navigation clavier.
 *
 * Note : les listes/options sont rendues par des fonctions (et non des
 * sous-composants) pour éviter qu'React ne remonte le DOM à chaque rendu —
 * sinon le champ de recherche perdrait le focus à chaque frappe.
 */
const FilterDropdown = ({
    icon: Icon = null,
    label = 'Filtrer',
    value = '',
    onChange,
    options = [],
    placeholder = 'Tous',
    searchable = false,
    size = 'md',
    align = 'left',
    width = 'w-64',
    className = '',
    block = false,
}) => {
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState('');
    const triggerRef = useRef(null);
    // Deux refs distinctes : la feuille mobile (portail) et le menu desktop
    // coexistent dans le DOM quand le menu est ouvert. Une ref unique serait
    // réaffectée au dernier monté et casserait la détection du clic extérieur
    // sur desktop.
    const desktopMenuRef = useRef(null);
    const mobileMenuRef = useRef(null);

    const selected = options.find((o) => String(o.value) === String(value));
    const isActive = Boolean(selected) && String(selected.value) !== '';

    const filtered = useMemo(() => {
        const q = query.trim().toLowerCase();
        if (!q) return options;
        return options.filter((o) => String(o.label).toLowerCase().includes(q));
    }, [options, query]);

    /* Fermeture : la recherche se réinitialise à chaque fermeture
       (plutôt qu'au montage d'un effet, pour éviter un setState dans un effet) */
    const close = () => {
        setOpen(false);
        setQuery('');
    };

    const sizes = { sm: 'btn-sm', md: 'btn-md', lg: 'btn-lg' };

    const select = (opt) => {
        onChange?.(opt.value);
        close();
    };

    /* Fermeture : Échap + clic extérieur */
    useEffect(() => {
        if (!open) return;

        const onKey = (e) => {
            if (e.key === 'Escape') close();
        };
        const onPointerDown = (e) => {
            const inDesktopMenu = desktopMenuRef.current?.contains(e.target);
            const inMobileMenu = mobileMenuRef.current?.contains(e.target);
            const inTrigger = triggerRef.current?.contains(e.target);
            if (!inDesktopMenu && !inMobileMenu && !inTrigger) close();
        };

        document.addEventListener('keydown', onKey);
        document.addEventListener('mousedown', onPointerDown);
        return () => {
            document.removeEventListener('keydown', onKey);
            document.removeEventListener('mousedown', onPointerDown);
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open]);

    /* Rendu d'une option */
    const renderOption = (opt) => {
        const isSel = String(opt.value) === String(value);
        return (
            <button
                key={String(opt.value)}
                type="button"
                role="option"
                aria-selected={isSel}
                onClick={() => select(opt)}
                className={`w-full flex items-center justify-between gap-3 px-3 py-2.5 text-sm text-left transition-colors ${
                    isSel
                        ? 'bg-brand-50 dark:bg-brand-500/15 text-brand-700 dark:text-brand-300 font-semibold'
                        : 'text-ink hover:bg-surface-2 dark:hover:bg-slate-800'
                }`}
            >
                <span className="truncate">{opt.label}</span>
                {isSel && <Check size={15} className="shrink-0" />}
            </button>
        );
    };

    /* Contenu de la liste (options + état vide) */
    const renderList = () => (
        <>
            {filtered.map(renderOption)}
            {filtered.length === 0 && (
                <p className="px-3 py-4 text-xs text-ink-muted text-center">Aucun résultat</p>
            )}
        </>
    );

    /* Champ de recherche contrôlé — rendu à chaque ouverture, jamais remonté */
    const renderSearch = (autoFocus) => (
        <div className="relative">
            <Search
                size={14}
                className="absolute left-2.5 top-1/2 -translate-y-1/2 text-ink-subtle pointer-events-none"
            />
            <input
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Rechercher…"
                autoFocus={autoFocus}
                className="input py-1.5 pl-8 pr-2 text-xs"
                aria-label={`Rechercher dans ${label}`}
            />
        </div>
    );

    return (
        <div className={`relative ${className}`}>
            {/* --- Déclencheur : même style que les autres boutons de la barre --- */}
            <button
                ref={triggerRef}
                type="button"
                onClick={() => setOpen((o) => !o)}
                aria-haspopup="listbox"
                aria-expanded={open}
                title={label}
                className={`btn btn-secondary ${sizes[size]} ${block ? 'w-full' : ''} ${
                    isActive
                        ? 'border-brand-500 text-brand-700 dark:text-brand-300 dark:border-brand-500/40'
                        : ''
                }`}
            >
                {Icon && (
                    <Icon size={14} className={isActive ? 'text-brand-600 dark:text-brand-300' : ''} />
                )}
                <span className="max-w-[9rem] sm:max-w-[11rem] truncate">
                    {selected ? selected.label : placeholder}
                </span>
                <ChevronDown
                    size={14}
                    className={`text-ink-subtle transition-transform duration-150 ${open ? 'rotate-180' : ''}`}
                />
            </button>

            {/* --- Menu desktop --- */}
            {open && (
                <>
                    <div
                        ref={desktopMenuRef}
                        role="listbox"
                        aria-label={label}
                        className={`hidden sm:block absolute z-20 mt-2 ${width} ${
                            align === 'right' ? 'right-0' : 'left-0'
                        } card rounded-panel shadow-panel border border-line overflow-hidden animate-slide-down`}
                    >
                        {searchable && <div className="p-2 border-b border-line">{renderSearch(true)}</div>}
                        <div className="max-h-64 overflow-y-auto py-1">{renderList()}</div>
                    </div>

                    {/* --- Feuille mobile --- */}
                    {createPortal(
                        <div
                            className="sm:hidden fixed inset-0 flex items-end bg-black/60 backdrop-blur-sm z-[99999] animate-fade-in"
                            onClick={close}
                        >
                            <div
                                ref={mobileMenuRef}
                                role="listbox"
                                aria-label={label}
                                className="card w-full rounded-b-none max-h-[80vh] flex flex-col animate-slide-up"
                                onClick={(e) => e.stopPropagation()}
                            >
                                <div className="card-head">
                                    <h2 className="card-title">{label}</h2>
                                    <button
                                        type="button"
                                        onClick={close}
                                        className="btn-ghost btn-icon"
                                        aria-label="Fermer"
                                    >
                                        <X size={18} />
                                    </button>
                                </div>
                                {searchable && <div className="px-4 pb-2">{renderSearch(true)}</div>}
                                <div className="overflow-y-auto py-1">{renderList()}</div>
                            </div>
                        </div>,
                        document.body
                    )}
                </>
            )}
        </div>
    );
};

export default FilterDropdown;
