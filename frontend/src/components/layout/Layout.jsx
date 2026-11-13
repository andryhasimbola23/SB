import { Outlet, useLocation } from 'react-router-dom';
import { useEffect, useState } from 'react';
import Sidebar from './Sidebar';
import Header from './Header';
import PageBackground from './PageBackground';

/** Titres de page centralisés (une seule source de vérité). */
const TITLES = {
    '/dashboard': 'Tableau de bord',
    '/agences': 'Gestion des agences',
    '/services': 'Gestion des services',
    '/categories': 'Gestion des catégories',
    '/fournisseurs': 'Gestion des fournisseurs',
    '/utilisateurs': 'Gestion des utilisateurs',
    '/immobilisations': 'Gestion des immobilisations',
    '/mouvements': 'Mouvements',
    '/qr-codes': 'Génération de QR codes',
    '/inventaires': 'Inventaires',
};

const Layout = () => {
    const location = useLocation();
    const [displayLocation, setDisplayLocation] = useState(location);
    const [transitionStage, setTransitionStage] = useState('fadeIn');
    const [isSidebarOpen, setIsSidebarOpen] = useState(false);

    // Ferme le tiroir à chaque changement de page
    useEffect(() => {
        setIsSidebarOpen(false);
    }, [location.pathname]);

    // Transition entre les pages
    useEffect(() => {
        if (location.pathname !== displayLocation.pathname) {
            setTransitionStage('fadeOut');
            const t = setTimeout(() => {
                setDisplayLocation(location);
                setTransitionStage('fadeIn');
            }, 150);
            return () => clearTimeout(t);
        }
    }, [location, displayLocation]);

    // Réinitialise l'état du tiroir au passage en desktop
    useEffect(() => {
        const mq = window.matchMedia('(min-width: 1024px)');
        const onChange = (e) => {
            if (e.matches) setIsSidebarOpen(false);
        };
        mq.addEventListener('change', onChange);
        return () => mq.removeEventListener('change', onChange);
    }, []);

    const title = TITLES[location.pathname] || 'Gestion des immobilisations';

    return (
        <div className="flex min-h-screen">
            <Sidebar isOpen={isSidebarOpen} onClose={() => setIsSidebarOpen(false)} />

            <div className="flex-1 flex flex-col min-w-0">
                <Header title={title} onMenuClick={() => setIsSidebarOpen(true)} />

                <PageBackground>
                    <main className="flex-1 w-full max-w-[1600px] mx-auto p-4 sm:p-6 lg:p-8">
                        <div
                            key={displayLocation.pathname}
                            className={
                                transitionStage === 'fadeIn'
                                    ? 'animate-fade-in-up'
                                    : 'opacity-0'
                            }
                        >
                            <Outlet />
                        </div>
                    </main>
                </PageBackground>
            </div>
        </div>
    );
};

export default Layout;
