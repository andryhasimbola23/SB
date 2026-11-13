import { useState, useEffect, useRef } from 'react';
import { AlignLeft, Briefcase, Building2, Hash, Plus, Save } from 'lucide-react';
import serviceService from '../../services/serviceService';
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

const EMPTY_FORM = { code: '', nomService: '', description: '', actif: true, agenceId: '' };

const GestionServices = () => {
    const [services, setServices] = useState([]);
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
    const [editingService, setEditingService] = useState(null);
    const [formData, setFormData] = useState(EMPTY_FORM);
    const [formErrors, setFormErrors] = useState({});
    const [deleteId, setDeleteId] = useState(null);

    const {
        currentItems, currentPage, totalPages, totalItems, itemsPerPage, handlePageChange,
    } = usePagination(services, 6);

    useEffect(() => {
        loadAll();
    }, []);

    const flash = (setter, msg, delay) => {
        setter(msg);
        setTimeout(() => setter(''), delay);
    };

    const loadAll = async () => {
        try {
            setLoading(true);
            const [servicesData, agencesData] = await Promise.all([
                serviceService.getAll(),
                agenceService.getAll(),
            ]);
            // Invalide d'éventuelles recherches en vol avant d'écrire la liste.
            searchSeqRef.current += 1;
            fullListRef.current = servicesData;
            setServices(servicesData);
            setAgences(agencesData.filter((a) => a.actif));
            // Une recherche en cours reste appliquée sur les données rechargées
            // (création, suppression, bouton Actualiser).
            if (keyword.trim()) await handleSearch(keyword);
        } catch (err) {
            setError('Erreur lors du chargement');
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
            setServices(fullListRef.current);
            return;
        }

        const seq = ++searchSeqRef.current;
        try {
            const resultats = await serviceService.search(k);
            // Réponse obsolète (une frappe plus récente a pris le relais).
            if (seq === searchSeqRef.current) setServices(resultats);
        } catch {
            if (seq === searchSeqRef.current) setError('Erreur lors de la recherche');
        }
    };

    const openCreate = () => {
        setEditingService(null);
        setFormData({ ...EMPTY_FORM, agenceId: agences[0]?.id ?? '' });
        setFormErrors({});
        setIsModalOpen(true);
    };

    const openEdit = (service) => {
        setEditingService(service);
        setFormData({
            code: service.code || '',
            nomService: service.nomService || '',
            description: service.description || '',
            actif: service.actif ?? true,
            agenceId: service.agenceId || '',
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
        if (!formData.code.trim()) errors.code = 'Le code est obligatoire';
        if (!formData.nomService.trim()) errors.nomService = 'Le nom est obligatoire';
        if (!formData.agenceId) errors.agenceId = 'L’agence est obligatoire';
        setFormErrors(errors);
        return Object.keys(errors).length === 0;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!validate()) return;
        try {
            setError('');
            const payload = { ...formData, agenceId: Number(formData.agenceId) };
            if (editingService) {
                await serviceService.update(editingService.id, payload);
                flash(setSuccess, 'Service modifié avec succès', 3000);
            } else {
                await serviceService.create(payload);
                flash(setSuccess, 'Service créé avec succès', 3000);
            }
            setIsModalOpen(false);
            loadAll();
        } catch (err) {
            flash(setError, err.response?.data?.message || 'Erreur lors de l’enregistrement', 5000);
        }
    };

    const handleDelete = async () => {
        try {
            await serviceService.delete(deleteId);
            flash(setSuccess, 'Service supprimé avec succès', 3000);
        } catch (err) {
            flash(setError, err.response?.data?.message || 'Erreur lors de la suppression', 5000);
        } finally {
            setDeleteId(null);
            loadAll();
        }
    };

    const COLUMNS = [
        {
            key: 'code',
            label: 'Code',
            primary: true,
            render: (s) => <span className="font-mono font-semibold text-ink">{s.code}</span>,
        },
        {
            key: 'nomService',
            label: 'Nom',
            primary: true,
            render: (s) => <span className="font-medium text-ink">{s.nomService}</span>,
        },
        {
            key: 'agence',
            label: 'Agence',
            hideBelow: 'md',
            render: (s) => (
                <span className="inline-flex items-center px-2 py-0.5 rounded-control bg-surface-2 dark:bg-slate-800 text-ink-muted text-xs border border-line">
                    {s.agenceCode} — {s.agenceNom}
                </span>
            ),
        },
        {
            key: 'description',
            label: 'Description',
            hideBelow: 'lg',
            render: (s) => <span className="truncate max-w-[240px] inline-block">{s.description || '—'}</span>,
        },
        {
            key: 'actif',
            label: 'Statut',
            render: (s) => <Badge tone={s.actif ? 'brand' : 'danger'} label={s.actif ? 'Actif' : 'Inactif'} />,
        },
        {
            key: 'actions',
            label: 'Actions',
            align: 'right',
            actions: true,
            render: (s) => <RowActions onEdit={() => openEdit(s)} onDelete={() => setDeleteId(s.id)} />,
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
                placeholder="Rechercher un service…"
                actionLabel="Nouveau service"
                onAction={openCreate}
                loading={loading}
                onRefresh={loadAll}
            />

            <TableCard>
                {loading ? (
                    <TableSkeleton rows={6} cols={6} />
                ) : (
                    <>
                        <ResponsiveTable
                            columns={COLUMNS}
                            data={currentItems}
                            rowKey={(s) => s.id}
                            empty={
                                <EmptyState
                                    icon={Briefcase}
                                    title="Aucun service trouvé"
                                    message={
                                        keyword
                                            ? 'Aucun résultat pour cette recherche.'
                                            : 'Créez votre premier service pour commencer.'
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
                title={editingService ? 'Modifier le service' : 'Nouveau service'}
                subtitle={
                    editingService
                        ? 'Mettez à jour le rattachement et la présentation du service.'
                        : 'Rattachez un nouveau service à une agence active.'
                }
                icon={Briefcase}
                footer={
                    <FormFooter
                        embedded
                        form="service-form"
                        onCancel={() => setIsModalOpen(false)}
                        submitLabel={editingService ? 'Enregistrer' : 'Créer le service'}
                        submitIcon={editingService ? Save : Plus}
                        hint="Les champs marqués * sont obligatoires."
                    />
                }
            >
                <form id="service-form" onSubmit={handleSubmit} className="space-y-6">
                    <Section title="Identification">
                        <FieldGroup>
                            <Field
                                icon={Hash}
                                label="Code"
                                required
                                name="code"
                                value={formData.code}
                                onChange={handleChange}
                                maxLength={20}
                                placeholder="ex : SAG, SIT, SRH"
                                hint="Abréviation utilisée dans les codes d’immobilisation."
                                error={formErrors.code}
                            />
                            <Field icon={Building2} label="Agence" required error={formErrors.agenceId}>
                                <select
                                    id="agenceId"
                                    name="agenceId"
                                    value={formData.agenceId}
                                    onChange={handleChange}
                                    className="input"
                                >
                                    <option value="">-- Sélectionner --</option>
                                    {agences.map((ag) => (
                                        <option key={ag.id} value={ag.id}>
                                            {ag.codeAgence} — {ag.nomAgence}
                                        </option>
                                    ))}
                                </select>
                            </Field>
                        </FieldGroup>

                        <div className="mt-4">
                            <Field
                                icon={Briefcase}
                                label="Nom du service"
                                required
                                name="nomService"
                                value={formData.nomService}
                                onChange={handleChange}
                                placeholder="ex : Service Administratif"
                                error={formErrors.nomService}
                            />
                        </div>
                    </Section>

                    <Section
                        title="Présentation"
                        description="Facultatif — précisez les missions ou le périmètre du service."
                    >
                        <Field
                            icon={AlignLeft}
                            label="Description"
                            as="textarea"
                            name="description"
                            value={formData.description}
                            onChange={handleChange}
                            rows={3}
                            placeholder="Description du service…"
                            className="resize-none"
                        />
                    </Section>

                    <CheckboxField
                        id="actif"
                        name="actif"
                        label="Service actif"
                        description="Un service inactif n’apparaît plus dans les nouveaux rattachements."
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
                message="Voulez-vous vraiment supprimer ce service ? Cette action est irréversible."
                confirmLabel="Supprimer"
            />
        </div>
    );
};

export default GestionServices;
