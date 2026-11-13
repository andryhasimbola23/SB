import api from './api';

const inventaireService = {
    // Campagnes
    getAllCampagnes: async () => {
        const response = await api.get('/inventaires/campagnes');
        return response.data;
    },

    getCampagneById: async (id) => {
        const response = await api.get(`/inventaires/campagnes/${id}`);
        return response.data;
    },

    createCampagne: async (data) => {
        const response = await api.post('/inventaires/campagnes', data);
        return response.data;
    },

    updateCampagne: async (id, data) => {
        const response = await api.put(`/inventaires/campagnes/${id}`, data);
        return response.data;
    },

    cloturerCampagne: async (id) => {
        const response = await api.post(`/inventaires/campagnes/${id}/cloturer`);
        return response.data;
    },

    deleteCampagne: async (id) => {
        await api.delete(`/inventaires/campagnes/${id}`);
    },

    // Lignes
    getLignesByCampagne: async (campagneId) => {
        const response = await api.get(`/inventaires/campagnes/${campagneId}/lignes`);
        return response.data;
    },

    scanner: async (data) => {
        const response = await api.post('/inventaires/lignes/scan', data);
        return response.data;
    },

    deleteLigne: async (id) => {
        await api.delete(`/inventaires/lignes/${id}`);
    },
};

export default inventaireService;