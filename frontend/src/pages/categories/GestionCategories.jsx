import { useState, useEffect, useRef } from 'react';
import { Clock, FileText, Hash, Layers, Percent, Plus, Save, Tag, Tags } from 'lucide-react';
import categorieService from '../../services/categorieService';
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

/**
 * Types d'immobilisation.
 * 3 valeurs seulement → 3 tons de la palette approuvée :
 * COR = vert (neutre-fort), INC = gris, FIN = ambre.
 */
const TYPES = {
    COR: { label: 'Corporelle', tone: 'brand' },
    INC: { label: 'Incorporelle', tone: 'neutral' },
    FIN: { label: 'Financière', tone: 'warning' },
};

const EMPTY_FORM = {
    codeComptable: '',
    nomCategorie: '',
    libelle: '',
    typeImmobilisation: 'COR',
    classeCompte: '',
    tauxAmortissementParDefaut: '',
    dureeUtilisation: '',
    actif: true,
};

const GestionCategories = () => {
    const [categories, setCategories] = useState([]);
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
    } = usePagination(categories, 6);

    useEffect(() => {
        loadCategories();
    }, []);

    const flash = (setter, msg, delay) => {
        setter(msg);
        setTimeout(() => setter(''), delay);
    };

    const loadCategories = async () => {
        try {
            setLoading(true);
            const categoriesData = await categorieService.getAll();
            // Invalide d'éventuelles recherches en vol avant d'écrire la liste.
            searchSeqRef.current += 1;
            fullListRef.current = categoriesData;
            setCategories(categoriesData);
            // Une recherche en cours reste appliquée sur les données rechargées.
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
            setCategories(fullListRef.current);
            return;
        }

        const seq = ++searchSeqRef.current;
        try {
            const resultats = await categorieService.search(k);
            // Réponse obsolète (une frappe plus récente a pris le relais).
            if (seq === searchSeqRef.current) setCategories(resultats);
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

    const openEdit = (cat) => {
        setEditing(cat);
        setFormData({
            codeComptable: cat.codeComptable || '',
            nomCategorie: cat.nomCategorie || '',
            libelle: cat.libelle || '',
            typeImmobilisation: cat.typeImmobilisation || 'COR',
            classeCompte: cat.classeCompte || '',
            tauxAmortissementParDefaut: cat.tauxAmortissementParDefaut || '',
            dureeUtilisation: cat.dureeUtilisation || '',
            actif: cat.actif ?? true,
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
        if (!formData.codeComptable.trim()) errors.codeComptable = 'Le code comptable est obligatoire';
        if (!formData.nomCategorie.trim()) errors.nomCategorie = 'Le nom est obligatoire';
        if (!formData.typeImmobilisation) errors.typeImmobilisation = 'Le type est obligatoire';

        if (formData.tauxAmortissementParDefaut !== '') {
            const taux = parseFloat(formData.tauxAmortissementParDefaut);
            if (Number.isNaN(taux) || taux < 0 || taux > 100) {
                errors.tauxAmortissementParDefaut = 'Le taux doit être compris entre 0 et 100';
            }
        }
        setFormErrors(errors);
        return Object.keys(errors).length === 0;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!validate()) return;
        try {
            setError('');
            const payload = {
                ...formData,
                classeCompte: formData.classeCompte === '' ? null : parseInt(formData.classeCompte),
                tauxAmortissementParDefaut:
                    formData.tauxAmortissementParDefaut === ''
                        ? null
                        : parseFloat(formData.tauxAmortissementParDefaut),
                dureeUtilisation:
                    formData.dureeUtilisation === '' ? null : parseInt(formData.dureeUtilisation),
            };

            if (editing) {
                await categorieService.update(editing.id, payload);
                flash(setSuccess, 'Catégorie modifiée avec succès', 3000);
            } else {
                await categorieService.create(payload);
                flash(setSuccess, 'Catégorie créée avec succès', 3000);
            }
            setIsModalOpen(false);
            loadCategories();
        } catch (err) {
            flash(setError, err.response?.data?.message || 'Erreur lors de l’enregistrement', 5000);
        }
    };

    const handleDelete = async () => {
        try {
            await categorieService.delete(deleteId);
            flash(setSuccess, 'Catégorie supprimée avec succès', 3000);
        } catch (err) {
            flash(setError, err.response?.data?.message || 'Erreur lors de la suppression', 5000);
        } finally {
            setDeleteId(null);
            loadCategories();
        }
    };

    const TypeBadge = ({ type }) => {
        const meta = TYPES[type];
        return <Badge tone={meta?.tone || 'neutral'} label={meta?.label || type} />;
    };

    const COLUMNS = [
        {
            key: 'codeComptable',
            label: 'Code',
            primary: true,
            render: (c) => <span className="font-mono font-semibold text-ink">{c.codeComptable}</span>,
        },
        {
            key: 'nomCategorie',
            label: 'Nom',
            primary: true,
            render: (c) => <span className="font-medium text-ink">{c.nomCategorie}</span>,
        },
        { key: 'type', label: 'Type', hideBelow: 'md', render: (c) => <TypeBadge type={c.typeImmobilisation} /> },
        { key: 'classe', label: 'Classe', hideBelow: 'lg', render: (c) => c.classeCompte || '—' },
        {
            key: 'taux',
            label: 'Taux',
            hideBelow: 'lg',
            render: (c) =>
                c.tauxAmortissementParDefaut != null ? `${c.tauxAmortissementParDefaut} %` : '—',
        },
        {
            key: 'duree',
            label: 'Durée',
            hideBelow: 'xl',
            render: (c) => (c.dureeUtilisation != null ? `${c.dureeUtilisation} ans` : '—'),
        },
        {
            key: 'actif',
            label: 'Statut',
            render: (c) => <Badge tone={c.actif ? 'brand' : 'danger'} label={c.actif ? 'Actif' : 'Inactif'} />,
        },
        {
            key: 'actions',
            label: 'Actions',
            align: 'right',
            actions: true,
            render: (c) => <RowActions onEdit={() => openEdit(c)} onDelete={() => setDeleteId(c.id)} />,
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
                placeholder="Rechercher une catégorie…"
                actionLabel="Nouvelle catégorie"
                onAction={openCreate}
                loading={loading}
                onRefresh={loadCategories}
            />

            <TableCard>
                {loading ? (
                    <TableSkeleton rows={6} cols={8} />
                ) : (
                    <>
                        <ResponsiveTable
                            columns={COLUMNS}
                            data={currentItems}
                            rowKey={(c) => c.id}
                            empty={
                                <EmptyState
                                    icon={Tags}
                                    title="Aucune catégorie trouvée"
                                    message={
                                        keyword
                                            ? 'Aucun résultat pour cette recherche.'
                                            : 'Créez votre première catégorie pour commencer.'
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
                title={editing ? 'Modifier la catégorie' : 'Nouvelle catégorie'}
                subtitle={
                    editing
                        ? 'Ajustez les règles comptables et d’amortissement de cette catégorie.'
                        : 'Définissez une famille de biens et ses règles d’amortissement.'
                }
                icon={Tags}
                size="lg"
                footer={
                    <FormFooter
                        embedded
                        form="categorie-form"
                        onCancel={() => setIsModalOpen(false)}
                        submitLabel={editing ? 'Enregistrer' : 'Créer la catégorie'}
                        submitIcon={editing ? Save : Plus}
                        hint="Les champs marqués * sont obligatoires."
                    />
                }
            >
                <form id="categorie-form" onSubmit={handleSubmit} className="space-y-6">
                    <Section title="Identification">
                        <FieldGroup>
                            <Field
                                icon={Hash}
                                label="Code comptable"
                                required
                                name="codeComptable"
                                value={formData.codeComptable}
                                onChange={handleChange}
                                placeholder="ex : 2183"
                                maxLength={20}
                                hint="Compte du plan comptable associé."
                                error={formErrors.codeComptable}
                            />
                            <Field
                                icon={Tag}
                                label="Nom"
                                required
                                name="nomCategorie"
                                value={formData.nomCategorie}
                                onChange={handleChange}
                                placeholder="ex : MATERIEL INFORMATIQUE"
                                error={formErrors.nomCategorie}
                            />
                        </FieldGroup>

                        <div className="mt-4">
                            <Field
                                icon={FileText}
                                label="Libellé"
                                name="libelle"
                                value={formData.libelle}
                                onChange={handleChange}
                                placeholder="Libellé lisible pour les états et exports"
                            />
                        </div>
                    </Section>

                    <Section
                        title="Comptabilité et amortissement"
                        description="Ces valeurs sont proposées par défaut lors de la création d’une immobilisation."
                    >
                        <FieldGroup>
                            <Field icon={Layers} label="Type" required error={formErrors.typeImmobilisation}>
                                <select
                                    id="typeImmobilisation"
                                    name="typeImmobilisation"
                                    value={formData.typeImmobilisation}
                                    onChange={handleChange}
                                    className="input"
                                >
                                    <option value="COR">Corporelle (COR)</option>
                                    <option value="INC">Incorporelle (INC)</option>
                                    <option value="FIN">Financière (FIN)</option>
                                </select>
                            </Field>
                            <Field
                                icon={Hash}
                                label="Classe"
                                type="number"
                                name="classeCompte"
                                value={formData.classeCompte}
                                onChange={handleChange}
                                placeholder="ex : 21"
                            />
                            <Field
                                icon={Clock}
                                label="Durée d’utilisation (ans)"
                                type="number"
                                name="dureeUtilisation"
                                value={formData.dureeUtilisation}
                                onChange={handleChange}
                                placeholder="ex : 5"
                                min={0}
                            />
                            <Field
                                icon={Percent}
                                label="Taux d’amortissement par défaut (%)"
                                type="number"
                                name="tauxAmortissementParDefaut"
                                value={formData.tauxAmortissementParDefaut}
                                onChange={handleChange}
                                step="0.01"
                                min={0}
                                max={100}
                                placeholder="ex : 20.00"
                                error={formErrors.tauxAmortissementParDefaut}
                            />
                        </FieldGroup>
                    </Section>

                    <CheckboxField
                        id="actif"
                        name="actif"
                        label="Catégorie active"
                        description="Une catégorie inactive reste liée aux biens existants mais n’est plus proposée à la création."
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
                message="Voulez-vous vraiment supprimer cette catégorie ? Cette action est irréversible."
                confirmLabel="Supprimer"
            />
        </div>
    );
};

export default GestionCategories;
