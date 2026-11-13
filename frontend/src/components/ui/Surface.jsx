/**
 * Enveloppe "carte" avec en-tête normalisé.
 * Remplace les ~12 en-têtes de carte écrits à la main dans les pages.
 */
const Surface = ({ title, subtitle, actions, children, className = '', bodyClassName = '', padded = true }) => (
    <section className={`card overflow-hidden ${className}`}>
        {(title || actions) && (
            <header className="card-head">
                <div className="min-w-0">
                    {title && <h3 className="card-title">{title}</h3>}
                    {subtitle && <p className="card-subtitle">{subtitle}</p>}
                </div>
                {actions && <div className="flex items-center gap-2 shrink-0">{actions}</div>}
            </header>
        )}
        <div className={padded ? `p-5 ${bodyClassName}` : bodyClassName}>{children}</div>
    </section>
);

/** Lien "Voir tout" stylisé pour les en-têtes de carte. */
export const SurfaceLink = ({ children, onClick }) => (
    <button
        type="button"
        onClick={onClick}
        className="inline-flex items-center gap-1 text-xs font-semibold text-brand-600 dark:text-brand-300 hover:text-brand-700 dark:hover:text-brand-200 transition-colors"
    >
        {children}
    </button>
);

/** État vide standardisé (icône + message + sous-message). */
export const EmptyState = ({ icon: Icon, title, message, action }) => (
    <div className="empty-state">
        {Icon && (
            <div className="empty-state-icon">
                <Icon size={24} />
            </div>
        )}
        {title && <p className="text-sm font-semibold text-ink">{title}</p>}
        {message && <p className="text-sm text-ink-muted max-w-sm">{message}</p>}
        {action && <div className="mt-3">{action}</div>}
    </div>
);

export default Surface;
