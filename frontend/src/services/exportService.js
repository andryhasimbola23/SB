import api from './api';
import { formatDateHeure } from '../utils/format';

const exportService = {
    /**
     * Télécharge un fichier depuis une réponse blob
     */
    downloadFile: async (url, filename) => {
        try {
            const response = await api.get(url, {
                responseType: 'blob',
            });

            // Créer un lien de téléchargement
            const blob = new Blob([response.data]);
            const link = document.createElement('a');
            link.href = window.URL.createObjectURL(blob);
            link.download = filename;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            window.URL.revokeObjectURL(link.href);
        } catch (error) {
            console.error('Erreur lors du téléchargement', error);
            throw error;
        }
    },

    exportImmobilisationsExcel: async () => {
        const date = new Date().toISOString().substring(0, 10);
        await exportService.downloadFile(
            '/export/immobilisations/excel',
            `immobilisations_${date}.xlsx`
        );
    },

    exportMouvementsExcel: async () => {
        const date = new Date().toISOString().substring(0, 10);
        await exportService.downloadFile(
            '/export/mouvements/excel',
            `mouvements_${date}.xlsx`
        );
    },

    exportDemandesExcel: async () => {
        const date = new Date().toISOString().substring(0, 10);
        await exportService.downloadFile(
            '/export/demandes/excel',
            `demandes_${date}.xlsx`
        );
    },

    exportInventaireExcel: async (campagneId) => {
        const date = new Date().toISOString().substring(0, 10);
        await exportService.downloadFile(
            `/export/inventaires/${campagneId}/excel`,
            `inventaire_${campagneId}_${date}.xlsx`
        );
    },

    exportFicheImmobilisationPdf: async (id) => {
        await exportService.downloadFile(
            `/export/immobilisations/${id}/pdf`,
            `fiche_immobilisation_${id}.pdf`
        );
    },

    exportInventairePdf: async (campagneId) => {
        await exportService.downloadFile(
            `/export/inventaires/${campagneId}/pdf`,
            `inventaire_${campagneId}.pdf`
        );
    },

    /**
     * Export CSV du journal d'audit — généré côté client à partir du JEU
     * DÉJÀ CHARGÉ (la page filtre localement, inutile de repasser par le
     * serveur). Séparateur `;` + BOM UTF-8 : Excel FR ouvre le fichier sans
     * réglage d'encodage ni séparateur.
     */
    exportJournalCsv: (lignes) => {
        const date = new Date().toISOString().substring(0, 10);
        const echapper = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;

        const entetes = ['N°', 'Date & heure', 'Acteur', 'Action', 'Entité', 'Identifiant', 'Détail'];
        const corps = lignes.map((e) => [
            e.id,
            formatDateHeure(e.dateAction),
            e.acteurNom || 'Sans session',
            e.action,
            e.entite,
            e.entiteId ?? '',
            e.description,
        ]);

        const contenu = [entetes, ...corps]
            .map((ligne) => ligne.map(echapper).join(';'))
            .join('\r\n');

        const blob = new Blob([`\uFEFF${contenu}`], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement('a');
        link.href = window.URL.createObjectURL(blob);
        link.download = `journal_audit_${date}.csv`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(link.href);
    },
};

export default exportService;