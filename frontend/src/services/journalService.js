import api from './api';

/** Journal d'audit — consultation ADMIN, lecture seule. */
const journalService = {
    /**
     * @param filtres { action?, entite?, acteurId? } — tous optionnels.
     */
    rechercher: async (filtres = {}) => {
        const response = await api.get('/journal-audit', { params: filtres });
        return response.data;
    },

    /**
     * Trace un export CSV du journal — le journal audite sa propre
     * extraction. Le serveur compose la description à partir de ces
     * critères structurés ; l'export est ensuite rechargé pour que la
     * nouvelle trace apparaisse en tête de liste.
     *
     * @param resume { nombre, action?, entite?, acteurNom?, du?, au?, recherche?, bien? }
     */
    tracerExport: async (resume) => {
        await api.post('/journal-audit/export', resume);
    },
};

export default journalService;
