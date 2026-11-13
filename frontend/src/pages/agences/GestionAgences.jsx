import { useState, useEffect, useRef } from 'react';
import { Building2, Hash, Mail, MapPin, Phone, Plus, Save } from 'lucide-react';
import agenceService from '../../services/agenceService';
import Modal from '../../components/ui/Modal';
import ConfirmDialog from '../../components/ui/ConfirmDialog';
import Pagination from '../../components/ui/Pagination';
import Field, { FieldGroup, CheckboxField, FormFooter } from '../../components/ui/Field';
import Section from '../../components/ui/Section';
import DataToolbar from '../../components/ui/DataToolbar';
import ResponsiveTable, { RowActions, TableCard } from '../../components/ui/ResponsiveTable';
import Badge from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/Surface';
import { TableSkeleton } from '../../components/ui/Skeleton';
import { usePagination } from '../../hooks/usePagination';

const EMPTY_FORM = {
    codeAgence: '',
    nomAgence: '',
    villeAgence: '',
    emailAgence: '',
    telephoneAgence: '',
    actif: true,
};

const GestionAgences = () => {
    const [agences, setAgences] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [keyword, setKeyword] = useState('');

    // Liste complète en cache : elle restaure les résultats instantanément
    // quand le champ de recherche est vidé, sans rechargement ni squelette.
    const fullListRef = useRef([]);
    // Compteur de requête : seules les réponses les plus récentes s'affichent.
    const searchSeqRef = useRef(0);

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editing, setEditing] = useState(null);
    const [formData, setFormData] = useState(EMPTY_FORM);
    const [formErrors, setFormErrors] = useState({});
    const [deleteId, setDeleteId] = useState(null);

    const {
        currentItems, currentPage, totalPages, totalItems, itemsPerPage, handlePageChange,
    } = usePagination(agences, 6);

    useEffect(() => {
        loadAgences();
    }, []);

    const flash = (setter, message, delay) => {
        setter(message);
        setTimeout(() => setter(''), delay);
    };

    const loadAgences = async () => {
        try {
            setLoading(true);
            const agencesData = await agenceService.getAll();
            // Invalide d'éventuelles recherches en vol avant d'écrire la liste.
            searchSeqRef.current += 1;
            fullListRef.current = agencesData;
            setAgences(agencesData);
            // Une recherche en cours reste appliquée sur les données rechargées.
            if (keyword.trim()) await handleSearch(keyword);
        } catch (err) {
            setError('Erreur lors du chargement des agences');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    /**
     * Recherche — appelée automatiquement à la frappe (via DataToolbar),
     * par Entrée ou par le bouton « Rechercher ».
     *
     * @param {string} terme terme recherché ; défaut = état courant du champ
     */
    const handleSearch = async (terme = keyword) => {
        const k = (terme ?? '').trim();
        handlePageChange(1);

        if (!k) {
            // Champ vidé : restauration immédiate depuis le cache.
            searchSeqRef.current += 1;
            setAgences(fullListRef.current);
            return;
        }

        const seq = ++searchSeqRef.current;
        try {
            const resultats = await agenceService.search(k);
            // Réponse obsolète (une frappe plus récente a pris le relais).
            if (seq === searchSeqRef.current) setAgences(resultats);
        } catch {
            if (seq === searchSeqRef.current) setError('Erreur lors de la recherche');
        }
    };

    const openCreate = () => {
        setEditing(null);
        setFormData(EMPTY_FORM);
        setFormErrors({});
        setIsModalOpen(true);
    };

    const openEdit = (agence) => {
        setEditing(agence);
        setFormData({
            codeAgence: agence.codeAgence || '',
            nomAgence: agence.nomAgence || '',
            villeAgence: agence.villeAgence || '',
            emailAgence: agence.emailAgence || '',
            telephoneAgence: agence.telephoneAgence || '',
            actif: agence.actif ?? true,
        });
        setFormErrors({});
        setIsModalOpen(true);
    };

    const handleChange = (e) => {
        const { name, value, type, checked } = e.target;
        setFormData((p) => ({ ...p, [name]: type === 'checkbox' ? checked : value }));
        if (formErrors[name]) setFormErrors((p) => ({ ...p, [name]: '' }));
    };

    const validate = () => {
        const errors = {};
        if (!formData.codeAgence.trim()) errors.codeAgence = 'Le code est obligatoire';
        if (!formData.nomAgence.trim()) errors.nomAgence = 'Le nom est obligatoire';
        if (formData.emailAgence && !/\S+@\S+\.\S+/.test(formData.emailAgence)) {
            errors.emailAgence = 'Email invalide';
        }
        setFormErrors(errors);
        return Object.keys(errors).length === 0;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!validate()) return;
        try {
            setError('');
            if (editing) {
                await agenceService.update(editing.id, formData);
                flash(setSuccess, 'Agence modifiée avec succès', 3000);
            } else {
                await agenceService.create(formData);
                flash(setSuccess, 'Agence créée avec succès', 3000);
            }
            setIsModalOpen(false);
            loadAgences();
        } catch (err) {
            flash(setError, err.response?.data?.message || 'Erreur lors de l’enregistrement', 5000);
        }
    };

    const handleDelete = async () => {
        try {
            await agenceService.delete(deleteId);
            flash(setSuccess, 'Agence supprimée avec succès', 3000);
        } catch (err) {
            flash(setError, err.response?.data?.message || 'Erreur lors de la suppression', 5000);
        } finally {
            setDeleteId(null);
            loadAgences();
        }
    };

    const COLUMNS = [
        {
            key: 'codeAgence',
            label: 'Code',
            primary: true,
            render: (a) => <span className="font-mono font-semibold text-ink">{a.codeAgence}</span>,
        },
        { key: 'nomAgence', label: 'Nom', primary: true, render: (a) => <span className="font-medium text-ink">{a.nomAgence}</span> },
        { key: 'villeAgence', label: 'Ville', hideBelow: 'md', render: (a) => a.villeAgence || '—' },
        { key: 'emailAgence', label: 'Email', hideBelow: 'lg', render: (a) => a.emailAgence || '—' },
        { key: 'telephoneAgence', label: 'Téléphone', hideBelow: 'lg', render: (a) => a.telephoneAgence || '—' },
        {
            key: 'actif',
            label: 'Statut',
            render: (a) => <Badge tone={a.actif ? 'brand' : 'danger'} label={a.actif ? 'Actif' : 'Inactif'} />,
        },
        {
            key: 'actions',
            label: 'Actions',
            align: 'right',
            actions: true,
            render: (a) => <RowActions onEdit={() => openEdit(a)} onDelete={() => setDeleteId(a.id)} />,
        },
    ];

    return (
        <div className="space-y-5">
            {error && <div className="alert-danger alert">{error}</div>}
            {success && <div className="alert-brand alert">{success}</div>}

            <DataToolbar
                keyword={keyword}
                onKeywordChange={setKeyword}
                onSearch={handleSearch}
                placeholder="Rechercher une agence…"
                actionLabel="Nouvelle agence"
                onAction={openCreate}
                loading={loading}
                onRefresh={loadAgences}
            />

            <TableCard>
                {loading ? (
                    <TableSkeleton rows={6} cols={7} />
                ) : (
                    <>
                        <ResponsiveTable
                            columns={COLUMNS}
                            data={currentItems}
                            rowKey={(a) => a.id}
                            empty={
                                <EmptyState
                                    icon={Building2}
                                    title="Aucune agence trouvée"
                                    message={
                                        keyword
                                            ? 'Aucun résultat pour cette recherche.'
                                            : 'Créez votre première agence pour commencer.'
                                    }
                                />
                            }
                        />
                        <Pagination
                            currentPage={currentPage}
                            totalPages={totalPages}
                            onPageChange={handlePageChange}
                            totalItems={totalItems}
                            itemsPerPage={itemsPerPage}
                        />
                    </>
                )}
            </TableCard>

            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title={editing ? 'Modifier l’agence' : 'Nouvelle agence'}
                subtitle={
                    editing
                        ? 'Mettez à jour les informations de cette agence.'
                        : 'Renseignez les informations de la nouvelle agence.'
                }
                icon={Building2}
                footer={
                    <FormFooter
                        embedded
                        form="agence-form"
                        onCancel={() => setIsModalOpen(false)}
                        submitLabel={editing ? 'Enregistrer' : 'Créer l’agence'}
                        submitIcon={editing ? Save : Plus}
                        hint="Les champs marqués * sont obligatoires."
                    />
                }
            >
                <form id="agence-form" onSubmit={handleSubmit} className="space-y-6">
                    <Section title="Identification">
                        <FieldGroup>
                            <Field
                                icon={Hash}
                                label="Code agence"
                                required
                                name="codeAgence"
                                value={formData.codeAgence}
                                onChange={handleChange}
                                maxLength={10}
                                placeholder="ex : AG01"
                                hint="Identifiant court utilisé dans les listes."
                                error={formErrors.codeAgence}
                            />
                            <Field
                                icon={Building2}
                                label="Nom de l’agence"
                                required
                                name="nomAgence"
                                value={formData.nomAgence}
                                onChange={handleChange}
                                placeholder="Nom de l’agence"
                                error={formErrors.nomAgence}
                            />
                        </FieldGroup>
                    </Section>

                    <Section
                        title="Coordonnées"
                        description="Ces informations facilitent le rattachement des services et des utilisateurs."
                    >
                        <FieldGroup>
                            <Field
                                icon={MapPin}
                                label="Ville"
                                name="villeAgence"
                                value={formData.villeAgence}
                                onChange={handleChange}
                                placeholder="Ville"
                            />
                            <Field
                                icon={Phone}
                                label="Téléphone"
                                name="telephoneAgence"
                                value={formData.telephoneAgence}
                                onChange={handleChange}
                                placeholder="Téléphone"
                            />
                        </FieldGroup>

                        <div className="mt-4">
                            <Field
                                icon={Mail}
                                label="Email"
                                type="email"
                                name="emailAgence"
                                value={formData.emailAgence}
                                onChange={handleChange}
                                placeholder="agence@auximad.mg"
                                error={formErrors.emailAgence}
                            />
                        </div>
                    </Section>

                    <CheckboxField
                        id="actif"
                        name="actif"
                        label="Agence active"
                        description="Une agence inactive reste conservée dans l’historique mais ne peut plus être sélectionnée."
                        checked={formData.actif}
                        onChange={handleChange}
                    />
                </form>
            </Modal>

            <ConfirmDialog
                isOpen={deleteId !== null}
                onClose={() => setDeleteId(null)}
                onConfirm={handleDelete}
                title="Confirmer la suppression"
                message="Voulez-vous vraiment supprimer cette agence ? Cette action est irréversible."
                confirmLabel="Supprimer"
            />
        </div>
    );
};

export default GestionAgences;
