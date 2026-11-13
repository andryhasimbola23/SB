import api from './api';

const categorieService = {
    getAll: async () => {
        const response = await api.get('/categories');
        return response.data;
    },

    getById: async (id) => {
        const response = await api.get(`/categories/${id}`);
        return response.data;
    },

    create: async (categorie) => {
        const response = await api.post('/categories', categorie);
        return response.data;
    },

    update: async (id, categorie) => {
        const response = await api.put(`/categories/${id}`, categorie);
        return response.data;
    },

    delete: async (id) => {
        await api.delete(`/categories/${id}`);
    },

    search: async (keyword) => {
        const response = await api.get('/categories/search', { params: { keyword } });
        return response.data;
    },
};

export default categorieService;