import api from './api';

const serviceService = {
    getAll: async () => {
        const response = await api.get('/services');
        return response.data;
    },

    getById: async (id) => {
        const response = await api.get(`/services/${id}`);
        return response.data;
    },

    create: async (service) => {
        const response = await api.post('/services', service);
        return response.data;
    },

    update: async (id, service) => {
        const response = await api.put(`/services/${id}`, service);
        return response.data;
    },

    delete: async (id) => {
        await api.delete(`/services/${id}`);
    },

    search: async (keyword) => {
        const response = await api.get('/services/search', { params: { keyword } });
        return response.data;
    },

    getByAgence: async (agenceId) => {
        const response = await api.get(`/services/agence/${agenceId}`);
        return response.data;
    },
};

export default serviceService;