/**
 * Fond de page partagé par toutes les vues authentifiées.
 * Dégradé discret aux couleurs de marque + trame de points.
 * `background-attachment: local` pour éviter le coût de repaint sur mobile.
 */
const PageBackground = ({ children }) => (
    <div className="page-surface page-surface-dots relative min-h-screen">
        <div className="relative z-[1] min-h-screen">{children}</div>
    </div>
);

export default PageBackground;
