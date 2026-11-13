import api from './api';

const utilisateurService = {
    getAll: async () => {
        const response = await api.get('/utilisateurs');
        return response.data;
    },

    getById: async (id) => {
        const response = await api.get(`/utilisateurs/${id}`);
        return response.data;
    },

    create: async (utilisateur) => {
        const response = await api.post('/utilisateurs', utilisateur);
        return response.data;
    },

    update: async (id, utilisateur) => {
        const response = await api.put(`/utilisateurs/${id}`, utilisateur);
        return response.data;
    },

    delete: async (id) => {
        await api.delete(`/utilisateurs/${id}`);
    },

    changePassword: async (id, data) => {
        await api.post(`/utilisateurs/${id}/change-password`, data);
    },

    search: async (keyword) => {
        const response = await api.get('/utilisateurs/search', { params: { keyword } });
        return response.data;
    },
};

export default utilisateurService;