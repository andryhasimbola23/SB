import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

/**
 * Tooltip stylé — bulle sombre à flèche, en remplacement de l'attribut
 * natif `title` lorsque le libellé d'un contrôle est masqué (sidebar
 * repliée en mode icônes).
 *
 * Rendu via PORTAIL dans document.body avec positionnement `fixed` mesuré
 * au survol : la bulle échappe à tout conteneur à défilement (le rail de
 * navigation) sans lequel elle serait rognée — et le défilement du rail
 * peut ainsi rester actif, condition pour que le bloc « Déconnexion »
 * ne soit jamais poussé hors écran.
 *
 * Usage : à déposer comme dernier enfant d'un contrôle portant
 * `relative` + `group` — les écouteurs de survol/focus sont posés sur ce
 * parent. Apparaît au survol et au focus clavier ; se ferme au défilement.
 *
 * @param {string} label - texte de la bulle (vide/null → inactif)
 * @param {'right'|'bottom'} [side] - côté d'affichage
 */
const DECALAGE = 10;

const Tooltip = ({ label, side = 'right' }) => {
    const [position, setPosition] = useState(null);
    const ancreRef = useRef(null);

    // Pose les écouteurs sur le CONTRÔLE (le parent de l'ancre) et mesure
    // sa position de viewport à chaque affichage.
    useEffect(() => {
        const controle = ancreRef.current?.parentElement;
        if (!controle || !label) return undefined;

        const afficher = () => {
            const r = controle.getBoundingClientRect();
            setPosition(
                side === 'bottom'
                    ? { left: r.left + r.width / 2, top: r.bottom + DECALAGE, transform: 'translate(-50%, 0)' }
                    : { left: r.right + DECALAGE, top: r.top + r.height / 2, transform: 'translate(0, -50%)' }
            );
        };
        const masquer = () => setPosition(null);

        controle.addEventListener('mouseenter', afficher);
        controle.addEventListener('mouseleave', masquer);
        controle.addEventListener('focusin', afficher);
        controle.addEventListener('focusout', masquer);
        return () => {
            controle.removeEventListener('mouseenter', afficher);
            controle.removeEventListener('mouseleave', masquer);
            controle.removeEventListener('focusin', afficher);
            controle.removeEventListener('focusout', masquer);
        };
    }, [label, side]);

    // Fermeture au défilement (navigation, page) ou au redimensionnement :
    // la position mesurée ne serait plus juste.
    useEffect(() => {
        if (!position) return undefined;
        const fermer = () => setPosition(null);
        window.addEventListener('scroll', fermer, true);
        window.addEventListener('resize', fermer);
        return () => {
            window.removeEventListener('scroll', fermer, true);
            window.removeEventListener('resize', fermer);
        };
    }, [position]);

    return (
        <>
            {/* Ancre de mesure — sans dimension, purement instrumentale. */}
            <span ref={ancreRef} aria-hidden="true" className="hidden" />
            {label && position && createPortal(
                <span
                    role="tooltip"
                    style={position}
                    className={`fixed z-[99999] pointer-events-none whitespace-nowrap rounded-md
                                bg-slate-900 dark:bg-slate-700 px-2 py-1 text-[11px] font-medium text-white shadow-lg
                                animate-fade-in`}
                >
                    <span
                        className={`absolute w-2 h-2 rotate-45 bg-slate-900 dark:bg-slate-700 ${
                            side === 'bottom'
                                ? 'top-0 left-1/2 -translate-x-1/2 -translate-y-1/2'
                                : 'left-0 top-1/2 -translate-x-1/2 -translate-y-1/2'
                        }`}
                    />
                    {label}
                </span>,
                document.body
            )}
        </>
    );
};

export default Tooltip;
