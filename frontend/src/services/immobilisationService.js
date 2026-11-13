import api from './api';

const immobilisationService = {
    getAll: async () => {
        const response = await api.get('/immobilisations');
        return response.data;
    },
    getById: async (id) => {
        const response = await api.get(`/immobilisations/${id}`);
        return response.data;
    },
    /** Identification par code unique (scan QR) — 404 si inconnu ou hors périmètre. */
    getByCode: async (codeUnique) => {
        const response = await api.get('/immobilisations/code', { params: { code: codeUnique } });
        return response.data;
    },
    create: async (data) => {
        const response = await api.post('/immobilisations', data);
        return response.data;
    },
    update: async (id, data) => {
        const response = await api.put(`/immobilisations/${id}`, data);
        return response.data;
    },
    delete: async (id) => {
        await api.delete(`/immobilisations/${id}`);
    },
    search: async (keyword) => {
        const response = await api.get('/immobilisations/search', { params: { keyword } });
        return response.data;
    },
    genererQrCode: async (id) => {
    const response = await api.post(`/immobilisations/${id}/generer-qr`);
    return response.data;
    },
    getAmortissements: async (id) => {
        const response = await api.get(`/immobilisations/${id}/amortissements`);
        return response.data;
    },
    calculerAmortissements: async (id) => {
        const response = await api.post(`/immobilisations/${id}/calculer-amortissements`);
        return response.data;
    },
    getVnc: async (id) => {
        const response = await api.get(`/immobilisations/${id}/vnc`);
        return response.data;
    },
};

export default immobilisationService;