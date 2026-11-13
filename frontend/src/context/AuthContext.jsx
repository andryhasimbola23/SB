import { createContext, useState, useEffect } from 'react';
import authService from '../services/authService';
import { connecterRealtime, deconnecterRealtime } from '../services/realtime';

export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    /**
     * Validation de session au démarrage de l'application.
     *
     * `user` n'est qu'une copie recopiée dans le localStorage : sa présence ne
     * prouve rien, un JWT expiré continuait d'y figurer. On la suppose donc
     * valable (pour éviter un second écran de chargement), puis on la confirme
     * auprès du serveur via `GET /api/auth/me`.
     *
     * Tant que `loading` est vrai, `ProtectedRoute` affiche son écran de
     * chargement : aucun tableau de bord fantôme n'apparaît avant la réponse.
     *
     * Note : si le serveur répond 401, l'intercepteur axios purge déjà le
     * localStorage et force une navigation vers /login. Le nettoyage fait ici
     * est alors redondant mais inoffensif — et nécessaire pour le cas 403,
     * que l'intercepteur ne traite pas.
     */
    useEffect(() => {
        let vivant = true;

        const demarrer = async () => {
            const session = authService.getCurrentUser();

            if (!session || !authService.isAuthenticated()) {
                // Session absente ou incomplète : rien à valider.
                authService.logout();
                if (vivant) setLoading(false);
                return;
            }

            if (vivant) setUser(session);

            try {
                await authService.verifierSession();
                // Session confirmée : le canal temps réel peut s'ouvrir.
                if (vivant) connecterRealtime();
            } catch (err) {
                // 401/403 : le serveur refuse réellement cette session -> purge.
                // Autre (réseau coupé, serveur à l'arrêt, 5xx) : on conserve la
                // session locale — une coupure technique ne doit pas déconnecter.
                // On tente quand même la socket : reconnectDelay réessaiera jusqu'à
                // ce que le serveur soit de nouveau joignable.
                const statut = err?.response?.status;
                if (statut === 401 || statut === 403) {
                    authService.logout();
                    deconnecterRealtime();
                    if (vivant) setUser(null);
                } else if (vivant) {
                    connecterRealtime();
                }
            } finally {
                if (vivant) setLoading(false);
            }
        };

        demarrer();

        return () => {
            vivant = false;
        };
    }, []);

    const login = async (nomUtilisateur, motDePasse) => {
        const data = await authService.login(nomUtilisateur, motDePasse);
        setUser(data);
        connecterRealtime();
        return data;
    };

    const logout = () => {
        authService.logout();
        deconnecterRealtime();
        setUser(null);
    };

    return (
        <AuthContext.Provider value={{ user, login, logout, loading }}>
            {children}
        </AuthContext.Provider>
    );
};
