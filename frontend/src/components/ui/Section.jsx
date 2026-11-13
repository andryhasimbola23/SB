/**
 * Titre de section repère — grammaire unique des formulaires longs
 * (fiches et modales de création / modification) : filet vert de marque
 * devant un titre compact, contenu en dessous.
 *
 * Les enfants ne sont pas espacés automatiquement : dans un formulaire,
 * empiler les champs avec `<div className="mt-4">` ou `FieldGroup`.
 *
 * @param {object} props
 * @param {string} props.title       titre affiché
 * @param {string} [props.description] phrase d'aide sous le titre
 * @param {Node}   props.children    contenu de la section
 */
const Section = ({ title, description, children, className = '' }) => (
    <section className={className}>
        <div className="mb-3">
            <h3 className="flex items-center gap-2 text-sm font-semibold text-ink">
                <span className="h-4 w-1 rounded bg-brand-500" aria-hidden="true" />
                {title}
            </h3>
            {description && (
                <p className="mt-1 pl-3 text-xs leading-relaxed text-ink-muted">{description}</p>
            )}
        </div>
        {children}
    </section>
);

export default Section;
