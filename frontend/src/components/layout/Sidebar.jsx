import { useEffect, useRef, useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import {
    LayoutDashboard, Building2, Users, Briefcase, Package, Tags,
    Truck, ArrowRightLeft, ClipboardList, LogOut, X, Inbox, ScrollText,
    PanelLeftClose, PanelLeftOpen,
} from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { useDemandeBadge } from '../../hooks/useDemandeBadge';
import Tooltip from '../ui/Tooltip';
import logo from '../../assets/Logo_auximad_simple.png';

const NAV_Z = 40;

const SECTIONS = {
    admin: [
        { to: '/dashboard', icon: LayoutDashboard, label: 'Tableau de bord' },
        {
            section: 'Gestion',
            items: [
                { to: '/immobilisations', icon: Package, label: 'Immobilisations' },
                { to: '/mouvements', icon: ArrowRightLeft, label: 'Mouvements' },
                { to: '/inventaires', icon: ClipboardList, label: 'Inventaires' },
                // À traiter par l'ADMIN — badge du nombre d'attentes.
                { to: '/demandes', icon: Inbox, label: 'Demandes' },
                // Trace des actions sensibles — réservée à l'ADMIN.
                { to: '/journal', icon: ScrollText, label: "Journal d'audit" },
            ],
        },
        {
            section: 'Paramètres',
            items: [
                { to: '/agences', icon: Building2, label: 'Agences' },
                { to: '/services', icon: Briefcase, label: 'Services' },
                { to: '/categories', icon: Tags, label: 'Catégories' },
                { to: '/fournisseurs', icon: Truck, label: 'Fournisseurs' },
                { to: '/utilisateurs', icon: Users, label: 'Utilisateurs' },
            ],
        },
    ],
    employe: [
        { to: '/dashboard', icon: LayoutDashboard, label: 'Tableau de bord' },
        {
            section: 'Consultation',
            items: [
                { to: '/immobilisations', icon: Package, label: 'Immobilisations' },
                // Les mouvements sont visibles, mais filtrés côté API sur le
                // service de l'utilisateur. L'inventaire est exclusif à l'ADMIN.
                { to: '/mouvements', icon: ArrowRightLeft, label: 'Mouvements' },
                { to: '/demandes', icon: Inbox, label: 'Mes demandes' },
            ],
        },
    ],
};

/**
 * Navigation latérale.
 * - `lg+` : rail collant persistant, toujours visible, réductible en mode
 *   icônes (72px) via un bouton de pied (« Réduire le menu »). Les libellés
 *   masqués remontent en tooltip stylé (survol + focus clavier). La
 *   préférence est mémorisée en localStorage (par appareil, choix visuel).
 * - Sous `lg` : tiroir plein écran animé depuis la gauche, fermé par défaut,
 *   toujours en pleine largeur (pas de mode réduit au doigt).
 *   Fermeture par : clic hors zone, touche Échap, lien cliqué, resize.
 */
const Sidebar = ({ isOpen, onClose }) => {
    const { logout, isAdmin } = useAuth();
    const location = useLocation();
    const asideRef = useRef(null);
    const demandesEnAttente = useDemandeBadge();

    // Repli en mode icônes — initialisation paresseuse depuis la préférence
    // locale ; aucun synchronisation serveur (choix purement visuel).
    const [replie, setReplie] = useState(() => localStorage.getItem('sidebar-replie') === '1');

    const basculerRepli = () => {
        setReplie((prev) => {
            const suivant = !prev;
            localStorage.setItem('sidebar-replie', suivant ? '1' : '0');
            return suivant;
        });
    };

    const menuItems = isAdmin() ? SECTIONS.admin : SECTIONS.employe;

    const isActive = (path) =>
        path === '/dashboard' ? location.pathname === path : location.pathname.startsWith(path);

    // Échap ferme le tiroir
    useEffect(() => {
        if (!isOpen) return;
        const onKey = (e) => e.key === 'Escape' && onClose?.();
        document.addEventListener('keydown', onKey);
        return () => document.removeEventListener('keydown', onKey);
    }, [isOpen, onClose]);

    // Verrouillage du scroll + focus piégé dans le tiroir
    useEffect(() => {
        if (!isOpen) return;
        document.body.style.overflow = 'hidden';
        const first = asideRef.current?.querySelector('a, button');
        first?.focus();
        return () => {
            document.body.style.overflow = 'unset';
        };
    }, [isOpen]);

    const renderLink = (item) => {
        const Icon = item.icon;
        const active = isActive(item.to);
        return (
            <NavLink
                key={item.to}
                to={item.to}
                onClick={onClose}
                aria-label={item.label}
                className={`group relative flex items-center gap-3 px-3 py-2.5 rounded-panel text-sm font-medium transition-colors duration-150 ${
                    replie ? 'lg:justify-center lg:px-0' : ''
                } ${
                    active
                        ? 'bg-brand-500 text-white shadow-brand'
                        : 'text-ink-muted hover:bg-surface-3 hover:text-ink'
                }`}
            >
                <Icon size={17} className="shrink-0" />
                <span className={`truncate ${replie ? 'lg:hidden' : ''}`}>{item.label}</span>

                {/* Demandes en attente : seul endroit où le système signale quelque
                    chose — un compteur métier, pas une notification. En mode repli,
                    la pastille se pose dans le coin de la pilule centrée. */}
                {item.to === '/demandes' && demandesEnAttente > 0 && (replie ? (
                    /* Pas de `title` ici : la bulle stylisée du lien suffit
                       — deux infobulles à la fois seraient redondantes. */
                    <span
                        className={`absolute top-1 right-1 min-w-[16px] h-4 px-1 rounded-full text-[10px] font-bold
                                    flex items-center justify-center tabular-nums ${
                            active
                                ? 'bg-white/25 text-white'
                                : 'bg-amber-50 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300'
                        }`}
                    >
                        {demandesEnAttente > 99 ? '99+' : demandesEnAttente}
                    </span>
                ) : (
                    <span
                        className={`ml-auto min-w-[20px] h-5 px-1.5 rounded-full text-[11px] font-bold
                                    flex items-center justify-center tabular-nums shrink-0 ${
                            active
                                ? 'bg-white/25 text-white'
                                : 'bg-amber-50 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300'
                        }`}
                        title={`${demandesEnAttente} demande(s) en attente de traitement`}
                    >
                        {demandesEnAttente > 99 ? '99+' : demandesEnAttente}
                    </span>
                ))}

                {/* En mode repli, la bulle stylisée remonte le libellé
                    masqué — au survol comme au focus clavier. */}
                {replie && <Tooltip label={item.label} />}

                {active && (
                    <span className="absolute -left-1.5 top-1/2 -translate-y-1/2 w-1 h-5 bg-brand-500 rounded-r-full" />
                )}
            </NavLink>
        );
    };

    return (
        <>
            {/* Overlay mobile */}
            {isOpen && (
                <div
                    className="fixed inset-0 bg-black/50 backdrop-blur-sm lg:hidden animate-fade-in"
                    style={{ zIndex: NAV_Z }}
                    onClick={onClose}
                    aria-hidden="true"
                />
            )}

            {/* Rail desktop */}
            <aside
                ref={asideRef}
                aria-hidden={isOpen ? undefined : undefined}
                className={`fixed lg:sticky top-0 left-0 w-[264px] h-screen
                    bg-surface dark:bg-slate-900 border-r border-line dark:border-slate-800
                    flex flex-col shrink-0
                    transform-gpu transition-[transform,width] duration-300 ease-out
                    ${replie ? 'lg:w-[72px]' : 'lg:w-[264px]'}
                    ${isOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}`}
                style={{ zIndex: NAV_Z + 10 }}
            >
                {/* Logo + marque — en repli, le monogramme seul, centré */}
                <div className="px-4 py-4 border-b border-line dark:border-slate-800 flex items-center justify-between shrink-0">
                    <NavLink
                        to="/dashboard"
                        onClick={onClose}
                        aria-label="AUXIMAD — Tableau de bord"
                        className={`relative group flex items-center gap-3 min-w-0 ${replie ? 'lg:justify-center lg:w-full' : ''}`}
                    >
                        <img
                            src={logo}
                            alt=""
                            className="w-10 h-10 object-contain shrink-0"
                            onError={(e) => {
                                e.currentTarget.style.display = 'none';
                            }}
                        />
                        <div className={`min-w-0 ${replie ? 'lg:hidden' : ''}`}>
                            <p className="text-[15px] font-bold tracking-wide leading-tight text-ink">
                                AUXIMAD
                            </p>
                            <p className="text-[10px] text-ink-subtle uppercase tracking-wider truncate">
                                Gestion des immobilisations
                            </p>
                        </div>
                        {replie && <Tooltip label="AUXIMAD — Tableau de bord" />}
                    </NavLink>

                    <button
                        onClick={onClose}
                        className="lg:hidden btn-ghost btn-icon shrink-0"
                        aria-label="Fermer le menu"
                    >
                        <X size={18} />
                    </button>
                </div>

                {/* Navigation — le défilement est INDISPENSABLE : sans lui,
                    la hauteur minimum du conteneur flex égalerait le contenu
                    du menu et pousserait « Déconnexion » hors du bas de
                    l'écran. Les bulles des tooltips échappent au défilement
                    grâce au portail (position fixed hors conteneur). */}
                <nav className="flex-1 min-h-0 py-4 px-3 overflow-y-auto scrollbar-thin space-y-1">
                    {menuItems.map((item, index) => {
                        if (item.section) {
                            return (
                                <div key={index} className="pt-4 first:pt-0">
                                    <div className={`px-1 pb-2 flex items-center gap-2 ${replie ? 'lg:hidden' : ''}`}>
                                        <span className="section-label">{item.section}</span>
                                        <span className="flex-1 h-px bg-line" />
                                    </div>
                                    {/* En repli, le filet de groupe est conservé :
                                        la structure des sections reste lisible. */}
                                    {replie && (
                                        <div className="hidden lg:block h-px bg-line mx-2 my-2" />
                                    )}
                                    <div className="space-y-0.5">{item.items.map(renderLink)}</div>
                                </div>
                            );
                        }
                        return <div key={index}>{renderLink(item)}</div>;
                    })}
                </nav>

                {/* Actions du rail — réduire le menu puis déconnexion, deux
                    boutons de même grammaire. La bascule n'existe qu'en
                    desktop : le tiroir mobile est toujours plein écran. */}
                <div className="border-t border-line dark:border-slate-800 p-3 shrink-0 space-y-1">
                    <button
                        type="button"
                        onClick={basculerRepli}
                        aria-expanded={!replie}
                        aria-label={replie ? 'Agrandir le menu' : 'Réduire le menu'}
                        className={`hidden lg:flex w-full items-center gap-3 px-3 py-2.5 rounded-panel text-sm font-medium
                                   text-ink-muted hover:bg-surface-3 hover:text-ink transition-colors duration-150 ${
                                       replie ? 'lg:justify-center lg:px-0' : ''
                                   }`}
                    >
                        {replie ? <PanelLeftOpen size={16} /> : <PanelLeftClose size={16} />}
                        <span className={replie ? 'lg:hidden' : ''}>Réduire le menu</span>
                        {replie && <Tooltip label="Agrandir le menu" />}
                    </button>

                    <button
                        onClick={() => {
                            onClose?.();
                            logout();
                        }}
                        aria-label="Déconnexion"
                        className={`group relative w-full flex items-center gap-3 px-3 py-2.5 rounded-panel text-sm font-medium
                                   act-danger-bg duration-150 ${
                                       replie ? 'lg:justify-center lg:px-0' : ''
                                   }`}
                    >
                        <LogOut size={16} className="shrink-0 transition-transform group-hover:-translate-x-0.5" />
                        <span className={replie ? 'lg:hidden' : ''}>Déconnexion</span>
                        {replie && <Tooltip label="Déconnexion" />}
                    </button>
                </div>
            </aside>
        </>
    );
};

export default Sidebar;
