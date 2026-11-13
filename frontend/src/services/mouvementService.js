import api from './api';

const mouvementService = {
    getAll: async () => {
        const response = await api.get('/mouvements');
        return response.data;
    },

    getById: async (id) => {
        const response = await api.get(`/mouvements/${id}`);
        return response.data;
    },

    getByImmobilisation: async (immobilisationId) => {
        const response = await api.get(`/mouvements/immobilisation/${immobilisationId}`);
        return response.data;
    },

    create: async (data) => {
        const response = await api.post('/mouvements', data);
        return response.data;
    },

    delete: async (id) => {
        await api.delete(`/mouvements/${id}`);
    },
};

export default mouvementService;