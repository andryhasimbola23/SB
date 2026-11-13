import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Menu, Sun, Moon, KeyRound, ScanLine } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { useTheme } from '../../hooks/useTheme';
import ProfilModal from '../ui/ProfilModal';
import NotificationBell from './NotificationBell';

const HEADER_HEIGHT = '56px';

/**
 * En-tête de page.
 * - Barre d'accent vert/rouge en haut (signature de la marque).
 * - Sticky, hauteur constante.
 * - Cloche de notifications in-app (badge des non-lues).
 * - Bouton « Scanner » : accès permanent au scanner global (deux rôles).
 * - Le bloc utilisateur ouvre la modale « Mon profil » : fiche complète
 *   (photo, coordonnées éditables), changement de mot de passe et
 *   déconnexion — accessible aux deux rôles.
 */
const Header = ({ title, onMenuClick }) => {
    const { user } = useAuth();
    const { isDark, toggleTheme } = useTheme();
    const navigate = useNavigate();
    const [isProfilOpen, setIsProfilOpen] = useState(false);

    return (
        <header
            className="sticky top-0 bg-surface/95 backdrop-blur border-b border-line"
            style={{ zIndex: 30 }}
        >
            {/* Signature de marque */}
            <div className="h-1 w-full flex">
                <span className="flex-1 bg-brand-500" />
                <span className="flex-1 bg-danger-500" />
            </div>

            <div
                className="px-3 sm:px-5 lg:px-6 flex items-center justify-between gap-3"
                style={{ minHeight: HEADER_HEIGHT }}
            >
                {/* Gauche */}
                <div className="flex items-center gap-2.5 min-w-0 flex-1">
                    <button
                        onClick={onMenuClick}
                        className="lg:hidden btn-ghost btn-icon shrink-0"
                        aria-label="Ouvrir le menu"
                    >
                        <Menu size={20} />
                    </button>
                    <h1 className="text-base sm:text-lg font-semibold text-ink truncate">{title}</h1>
                </div>

                {/* Droite */}
                <div className="flex items-center gap-1 shrink-0">
                    {/* Notifications in-app — badge des non-lues */}
                    <NotificationBell />

                    {/* Scanner global — deux rôles, périmètre filtré côté backend */}
                    <button
                        onClick={() => navigate('/scanner')}
                        className="btn-ghost btn-icon group"
                        title="Scanner un QR Code"
                        aria-label="Ouvrir le scanner"
                    >
                        <ScanLine
                            size={19}
                            className="text-brand-500 group-hover:scale-110 transition-transform"
                        />
                    </button>

                    <button
                        onClick={toggleTheme}
                        className="btn-ghost btn-icon group"
                        title={isDark ? 'Passer en mode clair' : 'Passer en mode sombre'}
                        aria-label={isDark ? 'Passer en mode clair' : 'Passer en mode sombre'}
                    >
                        {isDark ? (
                            <Sun size={19} className="text-amber-500 group-hover:rotate-90 transition-transform duration-500" />
                        ) : (
                            <Moon size={19} className="group-hover:-rotate-12 transition-transform duration-500" />
                        )}
                    </button>

                    <span className="hidden sm:block w-px h-7 bg-line mx-1" />

                    {/* Utilisateur — clic : fiche, mot de passe, déconnexion */}
                    <button
                        type="button"
                        onClick={() => setIsProfilOpen(true)}
                        className="flex items-center gap-2 pl-1 pr-1 sm:pr-2 rounded-panel hover:bg-surface-2 dark:hover:bg-slate-800/60 transition-colors cursor-pointer"
                        title="Mon profil"
                        aria-label="Ouvrir mon profil"
                    >
                        <div className="w-8 h-8 rounded-full bg-brand-500 text-white flex items-center justify-center text-sm font-semibold shrink-0">
                            {user?.nomComplet?.charAt(0)?.toUpperCase() || 'U'}
                        </div>
                        <div className="hidden md:block text-left leading-tight">
                            <p className="text-sm font-medium text-ink truncate max-w-[140px]">
                                {user?.nomComplet}
                            </p>
                            <p className="text-[11px] text-ink-subtle flex items-center gap-1">
                                {user?.role === 'ADMIN'
                                    ? 'Administrateur'
                                    : (user?.serviceNom || 'Sans service rattaché')}
                                <KeyRound size={11} className="opacity-60" />
                            </p>
                        </div>
                    </button>
                </div>
            </div>

            <ProfilModal open={isProfilOpen} onClose={() => setIsProfilOpen(false)} />
        </header>
    );
};

export default Header;
