import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from './useAuth';
import demandeService from '../services/demandeService';

/**
 * Nombre de demandes en attente de traitement, pour le badge de menu.
 *
 * - Restreint à l'ADMIN : le compteur d'une entreprise ne doit rien dire à un
 *   employé (le backend renverrait de toute façon ses seules demandes).
 * - Rechargé à chaque changement de route, puis après chaque mutation déclenchée
 *   depuis la page des demandes (événement `demandes:changed`) : pas de state
 *   global à propager, la Sidebar reste une feuille.
 */
export const useDemandeBadge = () => {
    const { isAdmin } = useAuth();
    const { pathname } = useLocation();
    const [tick, setTick] = useState(0);
    const [enAttente, setEnAttente] = useState(0);
    const admin = isAdmin();

    useEffect(() => {
        const refresh = () => setTick((t) => t + 1);
        window.addEventListener('demandes:changed', refresh);
        return () => window.removeEventListener('demandes:changed', refresh);
    }, []);

    useEffect(() => {
        if (!admin) return undefined;
        let vivant = true;
        demandeService
            .getStats()
            .then((stats) => {
                if (vivant) setEnAttente(stats?.enAttente || 0);
            })
            .catch(() => {
                // Sans compteur plutôt que faux compteur : un échec ne doit pas
                // afficher un volume que l'on n'a pas mesuré.
                if (vivant) setEnAttente(0);
            });
        return () => {
            vivant = false;
        };
    }, [admin, pathname, tick]);

    // Dérivé, pas stocké : un non-admin ne doit jamais conserver le dernier
    // compteur lu en tant qu'administrateur.
    return admin ? enAttente : 0;
};

/** Signale que l'état des demandes a changé (badge de menu à rafraîchir). */
export const signalerDemandeChangee = () => {
    window.dispatchEvent(new Event('demandes:changed'));
};

export default useDemandeBadge;
