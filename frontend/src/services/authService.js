import api from './api';

const authService = {
    login: async (nomUtilisateur, motDePasse) => {
        const response = await api.post('/auth/login', {
            nomUtilisateur,
            motDePasse,
        });
        if (response.data.token) {
            localStorage.setItem('token', response.data.token);
            localStorage.setItem('user', JSON.stringify(response.data));
        }
        return response.data;
    },

    logout: () => {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
    },

    getCurrentUser: () => {
        const user = localStorage.getItem('user');
        if (!user) return null;
        // Une entrée corrompue ne doit pas faire planter l'application au
        // démarrage : on la traite comme une session absente.
        try {
            return JSON.parse(user);
        } catch {
            localStorage.removeItem('user');
            return null;
        }
    },

    isAuthenticated: () => {
        return !!localStorage.getItem('token');
    },

    /**
     * Confirme auprès du serveur que le jeton stocké est encore valable.
     *
     * Le client ne peut pas le savoir tout seul : `user` n'est qu'une copie
     * locale, et un JWT expiré renvoyait jusqu'ici un 500 que l'intercepteur
     * ignorait — d'où un tableau de bord affiché mais totalement non fonctionnel.
     *
     * @throws {Error} rejeté si le serveur refuse (401/403) ou est injoignable.
     *                  L'appelant décide de purger ou non selon `err.response.status`.
     */
    verifierSession: async () => {
        const response = await api.get('/auth/me');
        return response.data;
    },

    /**
     * Change le mot de passe de l'utilisateur CONNECTÉ (porteur du jeton).
     * L'ancien mot de passe est exigé par le serveur.
     */
    changeMyPassword: async (ancienMotDePasse, nouveauMotDePasse) => {
        await api.post('/auth/change-password', { ancienMotDePasse, nouveauMotDePasse });
    },

    /**
     * Fiche complète du compte connecté (email, téléphone, photo, service,
     * agence, date de création) — /utilisateurs/{id} étant réservé à l'ADMIN.
     */
    getProfil: async () => {
        const response = await api.get('/auth/profil');
        return response.data;
    },

    /**
     * Mise à jour des propres coordonnées — email, téléphone et photo
     * uniquement ; le reste (nom, rôle, service) relève de l'ADMIN.
     *
     * @param patch { email?, telephone?, photo? } — champs seuls à modifier.
     */
    updateProfil: async (patch) => {
        const response = await api.patch('/auth/profil', patch);
        return response.data;
    },
};

export default authService;