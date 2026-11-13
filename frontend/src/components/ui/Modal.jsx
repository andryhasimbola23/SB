import { useEffect, useId } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';

const SIZES = {
    sm: 'sm:max-w-md',
    md: 'sm:max-w-2xl',
    lg: 'sm:max-w-4xl',
    xl: 'sm:max-w-6xl',
};

/**
 * Modale adaptative.
 * - Mobile  : feuille pleine largeur, ancrée en bas, coins carrés.
 * - `sm+`   : fenêtre centrée selon la taille demandée.
 *
 * L'en-tête accepte une tuile d'icône neutre (même grammaire que les fiches
 * de détail) et le pied est rendu HORS de la zone de défilement : l'action
 * principale reste toujours visible, même sur un formulaire long.
 */
const Modal = ({
    isOpen,
    onClose,
    title,
    subtitle,
    icon: Icon,
    children,
    size = 'md',
    footer = null,
    bodyClassName = '',
    footerClassName = '',
    hideHeader = false,
}) => {
    const titleId = useId();

    useEffect(() => {
        if (!isOpen) return undefined;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        return () => {
            document.body.style.overflow = previousOverflow;
        };
    }, [isOpen]);

    useEffect(() => {
        if (!isOpen) return undefined;
        const handleEscape = (e) => {
            if (e.key === 'Escape') onClose?.();
        };
        document.addEventListener('keydown', handleEscape);
        return () => document.removeEventListener('keydown', handleEscape);
    }, [isOpen, onClose]);

    if (!isOpen) return null;

    return createPortal(
        <div
            className="fixed inset-0 z-[99999] flex items-end justify-center bg-slate-950/60 backdrop-blur-sm sm:items-center sm:p-4 animate-fade-in"
            onClick={onClose}
            role="dialog"
            aria-modal="true"
            aria-labelledby={hideHeader ? undefined : titleId}
            aria-label={hideHeader ? title : undefined}
        >
            <div
                className={`bg-surface w-full ${SIZES[size] || SIZES.md} max-h-[94vh] sm:max-h-[88vh] flex flex-col overflow-hidden rounded-t-2xl shadow-panel sm:rounded-card animate-slide-up`}
                onClick={(e) => e.stopPropagation()}
            >
                {/* Poignée mobile */}
                <div className="sm:hidden pt-2.5 pb-1 flex justify-center shrink-0">
                    <span className="w-10 h-1 rounded-full bg-line-strong" />
                </div>

                {!hideHeader && (
                    <header className="card-head shrink-0 bg-surface px-5 py-4 sm:px-6">
                        <div className="flex min-w-0 flex-1 items-center gap-3">
                            {Icon && (
                                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-panel bg-surface-3 text-ink-muted">
                                    <Icon size={18} />
                                </div>
                            )}
                            <div className="min-w-0">
                                <h2 id={titleId} className="truncate text-base font-semibold text-ink sm:text-lg">
                                    {title}
                                </h2>
                                {subtitle && (
                                    <p className="mt-0.5 truncate text-xs text-ink-muted">{subtitle}</p>
                                )}
                            </div>
                        </div>
                        <button
                            onClick={onClose}
                            className="btn-ghost btn-icon -mr-1 shrink-0"
                            aria-label="Fermer"
                        >
                            <X size={20} />
                        </button>
                    </header>
                )}

                <div
                    className={`scrollbar-thin flex-1 overflow-y-auto overscroll-contain p-5 sm:p-6 ${bodyClassName}`}
                >
                    {children}
                </div>

                {footer && (
                    <footer
                        className={`shrink-0 border-t border-line bg-surface-2 px-5 py-4 sm:px-6 ${footerClassName}`}
                    >
                        {footer}
                    </footer>
                )}
            </div>
        </div>,
        document.body
    );
};

export default Modal;
