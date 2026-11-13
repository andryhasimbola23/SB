import { cloneElement, isValidElement, useId } from 'react';

/**
 * Champ de formulaire unifié (label + contrôle + message d'erreur).
 *
 * Supporte input, select et textarea via la prop `as`, ou un contrôle
 * personnalisé passé en `children`. Dans ce dernier cas, le champ hérite
 * automatiquement de l'état d'erreur et de l'identifiant du label : plus
 * besoin de recopier `className={`input ${erreur ? 'input-error' : ''}`}`
 * dans chaque page.
 *
 * Le wrap `FieldGroup` gère la grille 1, 2 ou 3 colonnes.
 */

const Field = ({
    label,
    htmlFor,
    error,
    required = false,
    hint,
    as = 'input',
    className = '',
    icon: Icon,
    children,
    options,
    ...props
}) => {
    const autoId = useId();
    const childId = isValidElement(children) ? children.props.id : undefined;
    const controlId = htmlFor || childId || autoId;
    const messageId = `${controlId}-message`;
    const controlClass = `input ${error ? 'input-error' : ''} ${className}`;

    const controlProps = {
        id: controlId,
        'aria-invalid': error ? true : undefined,
        'aria-describedby': error || hint ? messageId : undefined,
        ...props,
    };

    const renderControl = () => {
        if (children) {
            if (!isValidElement(children)) return children;

            // Conserve le style propre au contrôle, ajoute l'erreur une
            // seule fois et garantit le lien label ↔ champ.
            const childClassName = children.props.className || 'input';
            const hasErrorClass = childClassName.includes('input-error');

            return cloneElement(children, {
                id: children.props.id || controlId,
                'aria-invalid': error ? true : children.props['aria-invalid'],
                'aria-describedby':
                    error || hint ? messageId : children.props['aria-describedby'],
                className: error && !hasErrorClass ? `${childClassName} input-error` : childClassName,
            });
        }

        if (as === 'select') {
            return (
                <select className={controlClass} {...controlProps}>
                    {options?.map((opt) => (
                        <option key={opt.value} value={opt.value}>
                            {opt.label}
                        </option>
                    ))}
                </select>
            );
        }

        if (as === 'textarea') {
            return <textarea className={controlClass} rows={4} {...controlProps} />;
        }

        return <input className={controlClass} {...controlProps} />;
    };

    const contenu = (
        <>
            {label && (
                <label htmlFor={controlId} className="field-label">
                    {label}
                    {required && (
                        <span className="ml-0.5 text-danger-500" aria-hidden="true">
                            *
                        </span>
                    )}
                </label>
            )}
            {renderControl()}
            {error ? (
                <p id={messageId} className="field-error" role="alert">
                    {error}
                </p>
            ) : (
                hint && (
                    <p id={messageId} className="mt-1.5 text-xs text-ink-muted">
                        {hint}
                    </p>
                )
            )}
        </>
    );

    // Variante « ligne de fiche » : la tuile d'icône neutre des modales de
    // détail, pour que les formulaires parlent la même langue visuelle que
    // les fiches. Sans `icon`, le rendu reste strictement inchangé.
    if (Icon) {
        return (
            <div className="flex items-start gap-2.5">
                <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-control bg-surface-3">
                    <Icon size={14} className="text-ink-muted" />
                </div>
                <div className="min-w-0 flex-1">{contenu}</div>
            </div>
        );
    }

    return <div>{contenu}</div>;
};

/**
 * Grille responsive pour enchaîner les champs.
 * `cols={2}` → 1 colonne sous `sm`, 2 à partir de `sm`.
 */
export const FieldGroup = ({ cols = 2, className = '', children }) => (
    <div
        className={`grid grid-cols-1 ${
            cols === 2 ? 'sm:grid-cols-2' : cols === 3 ? 'sm:grid-cols-2 lg:grid-cols-3' : ''
        } gap-4 ${className}`}
    >
        {children}
    </div>
);

/**
 * Case à cocher stylée.
 * Avec `description` (ou `boxed`), elle devient une tuile cliquable :
 * cible plus large, hiérarchie titre / explication, survol neutre.
 */
export const CheckboxField = ({ id, label, description, boxed = false, checked, onChange, name }) => {
    const carte = boxed || Boolean(description);

    if (!carte) {
        return (
            <label htmlFor={id} className="group flex cursor-pointer select-none items-center gap-2.5">
                <input
                    type="checkbox"
                    id={id}
                    name={name}
                    checked={checked}
                    onChange={onChange}
                    className="h-4 w-4 cursor-pointer rounded border-line-strong text-brand-500 focus:ring-brand-500/30"
                />
                <span className="text-sm text-ink transition-colors group-hover:text-ink">{label}</span>
            </label>
        );
    }

    return (
        <label
            htmlFor={id}
            className="group flex cursor-pointer items-start gap-3 rounded-panel border border-line bg-surface-2 p-3.5 transition-colors hover:border-line-strong hover:bg-surface-3 dark:bg-slate-800/60 dark:hover:bg-slate-800"
        >
            <input
                type="checkbox"
                id={id}
                name={name}
                checked={checked}
                onChange={onChange}
                className="mt-0.5 h-4 w-4 shrink-0 cursor-pointer rounded border-line-strong text-brand-500 focus:ring-brand-500/30"
            />
            <span className="min-w-0">
                <span className="block text-sm font-medium text-ink">{label}</span>
                {description && (
                    <span className="mt-0.5 block text-xs leading-relaxed text-ink-muted">
                        {description}
                    </span>
                )}
            </span>
        </label>
    );
};

/**
 * Pied de formulaire (Annuler / Valider).
 *
 * - `embedded` : à placer dans le `footer` de la modale (pas de filet ni de
 *   marge, le pied est déjà séparé par la modale).
 * - `form` : associe le bouton de validation à un `<form id="…">` rendu
 *   ailleurs — indispensable quand le pied est hors de la zone défilante.
 * - `hint` : rappel discret à gauche (champs obligatoires, portée de
 *   l'action…).
 */
export const FormFooter = ({
    onCancel,
    onCancelLabel = 'Annuler',
    submitLabel = 'Enregistrer',
    submitting = false,
    danger = false,
    form,
    hint,
    submitIcon: SubmitIcon,
    embedded = false,
    className = '',
}) => (
    <div
        className={`flex flex-col gap-3 sm:flex-row sm:items-center ${
            hint ? 'sm:justify-between' : 'sm:justify-end'
        } ${embedded ? '' : 'mt-5 border-t border-line pt-5'} ${className}`}
    >
        {hint && (
            <p className="text-xs leading-relaxed text-ink-muted">{hint}</p>
        )}
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
            <button type="button" onClick={onCancel} className="btn-secondary btn-md">
                {onCancelLabel}
            </button>
            <button
                type="submit"
                form={form}
                disabled={submitting}
                className={`btn btn-md ${danger ? 'btn-danger' : 'btn-primary'} disabled:cursor-not-allowed disabled:opacity-50`}
            >
                {submitting ? (
                    <>
                        <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white" />
                        Enregistrement…
                    </>
                ) : (
                    <>
                        {SubmitIcon && <SubmitIcon size={15} />}
                        {submitLabel}
                    </>
                )}
            </button>
        </div>
    </div>
);

export default Field;
