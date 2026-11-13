import api from './api';

const fournisseurService = {
    getAll: async () => {
        const response = await api.get('/fournisseurs');
        return response.data;
    },

    getById: async (id) => {
        const response = await api.get(`/fournisseurs/${id}`);
        return response.data;
    },

    create: async (fournisseur) => {
        const response = await api.post('/fournisseurs', fournisseur);
        return response.data;
    },

    update: async (id, fournisseur) => {
        const response = await api.put(`/fournisseurs/${id}`, fournisseur);
        return response.data;
    },

    delete: async (id) => {
        await api.delete(`/fournisseurs/${id}`);
    },

    search: async (keyword) => {
        const response = await api.get('/fournisseurs/search', { params: { keyword } });
        return response.data;
    },
};

export default fournisseurService;