import api from './api';

/**
 * Demandes d'immobilisation (employé → administrateur).
 *
 * Les réponses sont déjà filtrées côté API selon l'appelant : un employé ne
 * reçoit que ses propres demandes, un administrateur toutes les demandes.
 * Aucune logique de périmètre ne doit être réimplémentée ici.
 */
const demandeService = {
    /**
     * @param {object}  [filtres]
     * @param {string}  [filtres.keyword] recherche textuelle (objet, motif)
     * @param {string}  [filtres.statut]  EN_ATTENTE | APPROUVEE | REJETTEE | ANNULEE
     */
    getAll: async (filtres = {}) => {
        const params = {};
        if (filtres.keyword) params.keyword = filtres.keyword;
        if (filtres.statut) params.statut = filtres.statut;
        const response = await api.get('/demandes', { params });
        return response.data;
    },

    getById: async (id) => {
        const response = await api.get(`/demandes/${id}`);
        return response.data;
    },

    /** Compteurs adaptés au rôle : totaux entreprise ou totaux personnels. */
    getStats: async () => {
        const response = await api.get('/demandes/stats');
        return response.data;
    },

    create: async (data) => {
        const response = await api.post('/demandes', data);
        return response.data;
    },

    /** Biens livrables sur une demande (ADMIN). Liste non périmétrée. */
    getCandidats: async ({ categorieId, q } = {}) => {
        const params = {};
        if (categorieId) params.categorieId = categorieId;
        if (q) params.q = q;
        const response = await api.get('/demandes/candidats', { params });
        return response.data;
    },

    /**
     * Décision d'un administrateur.
     *
     * L'approbation qui remet un bien ne se contente pas de le pointer : le
     * service change réellement et un mouvement est enregistré. `destinationServiceId`
     * n'est requis que si la demande n'a pas de service (employé non affecté).
     *
     * @param {string} decision APPROUVEE | REJETTEE
     * @param {string} [commentaire] motif du refus (obligatoire si REJETTEE)
     * @param {number} [immobilisationId] bien remis (approbation)
     * @param {number} [destinationServiceId] service de destination du bien
     */
    decider: async (id, decision, commentaire, immobilisationId, destinationServiceId) => {
        const response = await api.patch(`/demandes/${id}/decision`, {
            decision,
            commentaire,
            immobilisationId: immobilisationId || null,
            destinationServiceId: destinationServiceId || null,
        });
        return response.data;
    },

    /** Retrait de sa propre demande, tant qu'elle est encore en attente. */
    annuler: async (id) => {
        const response = await api.patch(`/demandes/${id}/annuler`);
        return response.data;
    },
};

export default demandeService;
