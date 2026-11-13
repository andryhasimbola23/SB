import api from './api';

const uploadService = {
    /**
     * Upload une photo de profil utilisateur
     * @param {File} file - Le fichier image
     * @param {number} userId - ID de l'utilisateur (optionnel)
     * @returns {Promise<{url: string}>}
     */
    uploadPhoto: async (file, userId = null) => {
        const formData = new FormData();
        formData.append('file', file);
        if (userId) {
            formData.append('userId', userId);
        }

        const response = await api.post('/upload/photo', formData, {
            headers: {
                'Content-Type': 'multipart/form-data',
            },
        });
        return response.data;
    },

    immobilisationPhoto: async (file) => {
        const formData = new FormData();
        formData.append('file', file);
        const response = await api.post('/upload/immobilisation-photo', formData, {
            headers: { 'Content-Type': 'multipart/form-data' },
        });
        return response.data;
    },
};

export default uploadService;