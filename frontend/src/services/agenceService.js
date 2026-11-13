import api from './api';

const agenceService = {
    // Récupérer toutes les agences
    getAll: async () => {
        const response = await api.get('/agences');
        return response.data;
    },

    // Récupérer une agence par ID
    getById: async (id) => {
        const response = await api.get(`/agences/${id}`);
        return response.data;
    },

    // Créer une nouvelle agence
    create: async (agence) => {
        const response = await api.post('/agences', agence);
        return response.data;
    },

    // Mettre à jour une agence
    update: async (id, agence) => {
        const response = await api.put(`/agences/${id}`, agence);
        return response.data;
    },

    // Supprimer une agence
    delete: async (id) => {
        await api.delete(`/agences/${id}`);
    },

    // Rechercher des agences
    search: async (keyword) => {
        const response = await api.get('/agences/search', { params: { keyword } });
        return response.data;
    },
};

export default agenceService;