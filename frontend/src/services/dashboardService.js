import api from './api';

const dashboardService = {
    getStats: async () => {
        const response = await api.get('/dashboard/stats');
        return response.data;
    },

    getRepartitionCategorie: async () => {
        const response = await api.get('/dashboard/repartition-categorie');
        return response.data;
    },

    getRepartitionService: async () => {
        const response = await api.get('/dashboard/repartition-service');
        return response.data;
    },

    getDerniersMouvements: async () => {
        const response = await api.get('/dashboard/derniers-mouvements');
        return response.data;
    },

    getDernieresAcquisitions: async () => {
        const response = await api.get('/dashboard/dernieres-acquisitions');
        return response.data;
    },
};

export default dashboardService;