import { useState, useEffect, useMemo } from 'react';
import { Plus, ClipboardList, FileText, Building2, Save, Eye, Lock, Calendar, Pencil, Trash2, SearchX } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import inventaireService from '../../services/inventaireService';
import agenceService from '../../services/agenceService';
import Modal from '../../components/ui/Modal';
import ConfirmDialog from '../../components/ui/ConfirmDialog';
import Pagination from '../../components/ui/Pagination';
import Field, { FieldGroup, FormFooter } from '../../components/ui/Field';
import Section from '../../components/ui/Section';
import DataToolbar from '../../components/ui/DataToolbar';
import Badge from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/Surface';
import { usePagination } from '../../hooks/usePagination';
import { ListSkeleton } from '../../components/ui/Skeleton';

const EMPTY_FORM = {
    libelle: '',
    dateDebut: new Date().toISOString().substring(0, 10),
    dateFin: '',
    agenceId: '',
};

const GestionInventaires = () => {
    const navigate = useNavigate();
    const [campagnes, setCampagnes] = useState([]);
    const [agences, setAgences] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [keyword, setKeyword] = useState('');

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingCampagne, setEditingCampagne] = useState(null);
    const [formData, setFormData] = useState(EMPTY_FORM);
    const [formErrors, setFormErrors] = useState({});
    const [deleteId, setDeleteId] = useState(null);
    const [cloturerId, setCloturerId] = useState(null);

    /**
     * Filtrage client par libellé, agence (nom ou code) et statut :
     * le champ de recherche était affiché mais restait sans effet.
     */
    const campagnesFiltrees = useMemo(() => {
        const k = keyword.trim().toLowerCase();
        if (!k) return campagnes;
        return campagnes.filter((c) => {
            const statutTexte = c.statut === 'CLOTURE' ? 'clôturée cloturee' : 'en cours';
            return (
                (c.libelle || '').toLowerCase().includes(k) ||
                (c.agenceNom || '').toLowerCase().includes(k) ||
                (c.agenceCode || '').toLowerCase().includes(k) ||
                statutTexte.includes(k)
            );
        });
    }, [campagnes, keyword]);

    const {
        currentItems: currentCampagnes,
        currentPage,
        totalPages,
        totalItems,
        itemsPerPage,
        handlePageChange,
    } = usePagination(campagnesFiltrees, 6);

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
            const [campData, agData] = await Promise.all([
                inventaireService.getAllCampagnes(),
                agenceService.getAll(),
            ]);
            setCampagnes(campData);
            setAgences(agData.filter((a) => a.actif));
        } catch (err) {
            setError('Erreur lors du chargement');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const handleOpenCreate = () => {
        setEditingCampagne(null);
        setFormData({
            ...EMPTY_FORM,
            libelle: `Inventaire ${new Date().getFullYear()}`,
            agenceId: agences.length > 0 ? agences[0].id : '',
        });
        setFormErrors({});
        setIsModalOpen(true);
    };

    const handleOpenEdit = (campagne) => {
        setEditingCampagne(campagne);
        setFormData({
            libelle: campagne.libelle || '',
            dateDebut: campagne.dateDebut || '',
            dateFin: campagne.dateFin || '',
            agenceId: campagne.agenceId || '',
        });
        setFormErrors({});
        setIsModalOpen(true);
    };

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData((prev) => ({ ...prev, [name]: value }));
        if (formErrors[name]) setFormErrors((prev) => ({ ...prev, [name]: '' }));
    };

    const validateForm = () => {
        const errors = {};
        if (!formData.libelle.trim()) errors.libelle = 'Le libellé est obligatoire';
        if (!formData.dateDebut) errors.dateDebut = 'La date de début est obligatoire';
        setFormErrors(errors);
        return Object.keys(errors).length === 0;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!validateForm()) return;

        try {
            setError('');
            const payload = {
                ...formData,
                agenceId: formData.agenceId ? Number(formData.agenceId) : null,
                dateFin: formData.dateFin || null,
            };

            if (editingCampagne) {
                await inventaireService.updateCampagne(editingCampagne.id, payload);
                flash(setSuccess, 'Campagne modifiée avec succès', 3000);
            } else {
                await inventaireService.createCampagne(payload);
                flash(setSuccess, 'Campagne créée avec succès', 3000);
            }
            setIsModalOpen(false);
            loadAll();
        } catch (err) {
            flash(setError, err.response?.data?.message || "Erreur lors de l'enregistrement", 5000);
        }
    };

    const handleDelete = async () => {
        try {
            await inventaireService.deleteCampagne(deleteId);
            flash(setSuccess, 'Campagne supprimée avec succès', 3000);
        } catch (err) {
            flash(setError, err.response?.data?.message || 'Erreur lors de la suppression', 5000);
        } finally {
            setDeleteId(null);
            loadAll();
        }
    };

    const handleCloturer = async () => {
        try {
            await inventaireService.cloturerCampagne(cloturerId);
            flash(setSuccess, 'Campagne clôturée avec succès', 3000);
        } catch {
            flash(setError, 'Erreur lors de la clôture', 5000);
        } finally {
            setCloturerId(null);
            loadAll();
        }
    };

    const formatDate = (dateStr) => {
        if (!dateStr) return '-';
        return new Date(dateStr).toLocaleDateString('fr-FR');
    };

    /* Statistiques d'une campagne — palette réduite, pas de bleu */
    const StatBox = ({ label, value, tone }) => {
        const tones = {
            neutral: 'text-ink',
            brand: 'text-brand-600 dark:text-brand-300',
            danger: 'text-danger-600 dark:text-danger-300',
            warning: 'text-amber-600 dark:text-amber-300',
        };
        return (
            <div className="text-center bg-surface-2 dark:bg-slate-800/50 rounded-panel py-2.5 px-2 border border-line">
                <p className="section-label">{label}</p>
                <p className={`text-lg font-bold ${tones[tone] || tones.neutral}`}>{value || 0}</p>
            </div>
        );
    };

    return (
        <div className="space-y-5">
            {error && <div className="alert-danger alert">{error}</div>}
            {success && <div className="alert-brand alert">{success}</div>}

            <DataToolbar
                keyword={keyword}
                onKeywordChange={setKeyword}
                onSearch={() => {}}
                placeholder="Rechercher une campagne…"
                actionLabel="Nouvelle campagne"
                onAction={handleOpenCreate}
                loading={loading}
                onRefresh={loadAll}
            />

            {loading ? (
                <ListSkeleton items={5} />
            ) : campagnes.length === 0 ? (
                <div className="card">
                    <EmptyState
                        icon={ClipboardList}
                        title="Aucune campagne d'inventaire"
                        message="Créez une campagne pour commencer un inventaire."
                        action={
                            <button onClick={handleOpenCreate} className="btn-primary btn-md mt-2">
                                <Plus size={16} />
                                Nouvelle campagne
                            </button>
                        }
                    />
                </div>
            ) : campagnesFiltrees.length === 0 ? (
                <div className="card">
                    <EmptyState
                        icon={SearchX}
                        title="Aucun résultat"
                        message={`Aucune campagne ne correspond à « ${keyword.trim()} ».`}
                    />
                </div>
            ) : (
                <>
                    <div className="space-y-3">
                        {currentCampagnes.map((campagne) => (
                            <article
                                key={campagne.id}
                                className="card p-4 sm:p-5 hover:shadow-card transition-shadow"
                            >
                                <div className="flex flex-wrap items-start justify-between gap-3 mb-4">
                                    <div className="min-w-0 flex-1">
                                        <div className="mb-1.5">
                                            {campagne.statut === 'CLOTURE' ? (
                                                <Badge tone="neutral" label="Clôturée" />
                                            ) : (
                                                <Badge tone="brand" label="En cours" />
                                            )}
                                        </div>
                                        <h3 className="text-base sm:text-lg font-semibold text-ink truncate">
                                            {campagne.libelle}
                                        </h3>
                                        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 mt-2 text-xs text-ink-muted">
                                            <span className="inline-flex items-center gap-1">
                                                <Calendar size={12} />
                                                Du {formatDate(campagne.dateDebut)}
                                                {campagne.dateFin && ` au ${formatDate(campagne.dateFin)}`}
                                            </span>
                                            {campagne.agenceNom && (
                                                <span className="inline-flex items-center px-2 py-0.5 rounded-control bg-surface-2 dark:bg-slate-800 border border-line">
                                                    {campagne.agenceCode} - {campagne.agenceNom}
                                                </span>
                                            )}
                                        </div>
                                    </div>

                                    <div className="flex items-center gap-1 shrink-0">
                                        <button
                                            onClick={() => navigate(`/inventaires/${campagne.id}`)}
                                            className="btn-brand-soft btn-sm"
                                        >
                                            <Eye size={14} />
                                            Ouvrir
                                        </button>
                                        {campagne.statut === 'EN_COURS' && (
                                            <>
                                                <button
                                                    onClick={() => handleOpenEdit(campagne)}
                                                    className="p-2 act-quiet-bg rounded-panel"
                                                    title="Modifier"
                                                    aria-label="Modifier"
                                                >
                                                    <Pencil size={16} />
                                                </button>
                                                <button
                                                    onClick={() => setCloturerId(campagne.id)}
                                                    className="p-2 text-ink-muted hover:text-amber-600 hover:bg-amber-50 dark:hover:bg-amber-500/15 dark:hover:text-amber-300 rounded-panel transition-colors"
                                                    title="Clôturer"
                                                    aria-label="Clôturer"
                                                >
                                                    <Lock size={16} />
                                                </button>
                                            </>
                                        )}
                                        <button
                                            onClick={() => setDeleteId(campagne.id)}
                                            className="p-2 act-danger-bg rounded-panel"
                                            title="Supprimer"
                                            aria-label="Supprimer"
                                        >
                                            <Trash2 size={16} />
                                        </button>
                                    </div>
                                </div>

                                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-2.5 pt-3 border-t border-line">
                                    <StatBox label="Total biens" value={campagne.totalImmobilisations} tone="neutral" />
                                    <StatBox label="Scannés" value={campagne.totalScannees} tone="neutral" />
                                    <StatBox label="Présents" value={campagne.totalPresentes} tone="brand" />
                                    <StatBox label="Manquants" value={campagne.totalManquantes} tone="danger" />
                                    <StatBox label="Mauvais état" value={campagne.totalMauvaisEtat} tone="warning" />
                                </div>
                            </article>
                        ))}
                    </div>

                    <div className="card overflow-hidden">
                        <Pagination
                            currentPage={currentPage}
                            totalPages={totalPages}
                            onPageChange={handlePageChange}
                            totalItems={totalItems}
                            itemsPerPage={itemsPerPage}
                        />
                    </div>
                </>
            )}

            {/* Création / édition */}
            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title={editingCampagne ? 'Modifier la campagne' : 'Nouvelle campagne'}
                subtitle={
                    editingCampagne
                        ? 'Ajustez la période ou le périmètre de la campagne.'
                        : 'Lancez une campagne de recensement sur un périmètre donné.'
                }
                icon={ClipboardList}
                footer={
                    <FormFooter
                        embedded
                        form="campagne-form"
                        onCancel={() => setIsModalOpen(false)}
                        submitLabel={editingCampagne ? 'Enregistrer' : 'Créer la campagne'}
                        submitIcon={editingCampagne ? Save : Plus}
                        hint="Les champs marqués * sont obligatoires."
                    />
                }
            >
                <form id="campagne-form" onSubmit={handleSubmit} className="space-y-6">
                    <Section
                        title="Informations"
                        description="La campagne regroupe les biens à recenser et les scans effectués sur le terrain."
                    >
                        <Field
                            icon={FileText}
                            label="Libellé"
                            required
                            name="libelle"
                            value={formData.libelle}
                            onChange={handleChange}
                            placeholder="ex : Inventaire Annuel 2026"
                            error={formErrors.libelle}
                        />

                        <FieldGroup className="mt-4">
                            <Field
                                icon={Calendar}
                                label="Date début"
                                required
                                type="date"
                                name="dateDebut"
                                value={formData.dateDebut}
                                onChange={handleChange}
                                error={formErrors.dateDebut}
                            />
                            <Field
                                icon={Calendar}
                                label="Date fin"
                                type="date"
                                name="dateFin"
                                value={formData.dateFin}
                                onChange={handleChange}
                                hint="Facultative — la campagne reste ouverte sans échéance."
                            />
                        </FieldGroup>

                        <div className="mt-4">
                            <Field
                                icon={Building2}
                                label="Agence"
                                hint="Laisser sur « Toutes les agences » pour couvrir l’ensemble du parc."
                            >
                                <select
                                    id="agenceId"
                                    name="agenceId"
                                    value={formData.agenceId}
                                    onChange={handleChange}
                                    className="input"
                                >
                                    <option value="">-- Toutes les agences --</option>
                                    {agences.map((a) => (
                                        <option key={a.id} value={a.id}>
                                            {a.codeAgence} - {a.nomAgence}
                                        </option>
                                    ))}
                                </select>
                            </Field>
                        </div>
                    </Section>
                </form>
            </Modal>

            <ConfirmDialog
                isOpen={deleteId !== null}
                onClose={() => setDeleteId(null)}
                onConfirm={handleDelete}
                title="Supprimer la campagne"
                message="Êtes-vous sûr de vouloir supprimer cette campagne et toutes ses lignes d'inventaire ?"
                confirmLabel="Supprimer"
            />

            <ConfirmDialog
                isOpen={cloturerId !== null}
                onClose={() => setCloturerId(null)}
                onConfirm={handleCloturer}
                title="Clôturer la campagne"
                message="Une fois clôturée, la campagne ne pourra plus être modifiée ni recevoir de nouveaux scans."
                confirmLabel="Clôturer"
            />
        </div>
    );
};

export default GestionInventaires;
