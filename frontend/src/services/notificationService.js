import api from './api';

/** Notifications in-app du connecté — scopées au porteur du jeton côté backend. */
const notificationService = {
    getAll: async () => {
        const response = await api.get('/notifications');
        return response.data;
    },
    getNonLuesCount: async () => {
        const response = await api.get('/notifications/non-lues');
        return response.data.count;
    },
    marquerLue: async (id) => {
        const response = await api.put(`/notifications/${id}/lue`);
        return response.data;
    },
    toutMarquerLue: async () => {
        const response = await api.put('/notifications/toutes-lues');
        return response.data;
    },
};

export default notificationService;
