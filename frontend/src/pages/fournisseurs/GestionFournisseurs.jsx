import { useState, useEffect, useRef } from 'react';
import { Truck, Mail, Phone, Globe, MapPin, Plus, Save, Edit, Trash2 } from 'lucide-react';
import fournisseurService from '../../services/fournisseurService';
import Modal from '../../components/ui/Modal';
import ConfirmDialog from '../../components/ui/ConfirmDialog';
import Pagination from '../../components/ui/Pagination';
import Field, { FieldGroup, CheckboxField, FormFooter } from '../../components/ui/Field';
import Section from '../../components/ui/Section';
import DataToolbar from '../../components/ui/DataToolbar';
import Badge from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/Surface';
import { ListSkeleton } from '../../components/ui/Skeleton';
import { usePagination } from '../../hooks/usePagination';

const EMPTY_FORM = {
    nomFournisseur: '',
    adresseFournisseur: '',
    telephoneFournisseur: '',
    emailFournisseur: '',
    siteWeb: '',
    actif: true,
};

const GestionFournisseurs = () => {
    const [fournisseurs, setFournisseurs] = useState([]);
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
    } = usePagination(fournisseurs, 6);

    useEffect(() => {
        loadFournisseurs();
    }, []);

    const flash = (setter, msg, delay) => {
        setter(msg);
        setTimeout(() => setter(''), delay);
    };

    const loadFournisseurs = async () => {
        try {
            setLoading(true);
            const fournisseursData = await fournisseurService.getAll();
            // Invalide d'éventuelles recherches en vol avant d'écrire la liste.
            searchSeqRef.current += 1;
            fullListRef.current = fournisseursData;
            setFournisseurs(fournisseursData);
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
            setFournisseurs(fullListRef.current);
            return;
        }

        const seq = ++searchSeqRef.current;
        try {
            const resultats = await fournisseurService.search(k);
            // Réponse obsolète (une frappe plus récente a pris le relais).
            if (seq === searchSeqRef.current) setFournisseurs(resultats);
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

    const openEdit = (f) => {
        setEditing(f);
        setFormData({
            nomFournisseur: f.nomFournisseur || '',
            adresseFournisseur: f.adresseFournisseur || '',
            telephoneFournisseur: f.telephoneFournisseur || '',
            emailFournisseur: f.emailFournisseur || '',
            siteWeb: f.siteWeb || '',
            actif: f.actif ?? true,
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
        if (!formData.nomFournisseur.trim()) errors.nomFournisseur = 'Le nom est obligatoire';
        if (formData.emailFournisseur && !/\S+@\S+\.\S+/.test(formData.emailFournisseur)) {
            errors.emailFournisseur = 'Email invalide';
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
                await fournisseurService.update(editing.id, formData);
                flash(setSuccess, 'Fournisseur modifié avec succès', 3000);
            } else {
                await fournisseurService.create(formData);
                flash(setSuccess, 'Fournisseur créé avec succès', 3000);
            }
            setIsModalOpen(false);
            loadFournisseurs();
        } catch (err) {
            flash(setError, err.response?.data?.message || 'Erreur lors de l’enregistrement', 5000);
        }
    };

    const handleDelete = async () => {
        try {
            await fournisseurService.delete(deleteId);
            flash(setSuccess, 'Fournisseur supprimé avec succès', 3000);
        } catch (err) {
            flash(setError, err.response?.data?.message || 'Erreur lors de la suppression', 5000);
        } finally {
            setDeleteId(null);
            loadFournisseurs();
        }
    };

    const InfoRow = ({ icon: Icon, children }) => (
        <div className="flex items-start gap-2.5">
            <div className="w-7 h-7 rounded-control bg-surface-2 dark:bg-slate-800 border border-line flex items-center justify-center shrink-0 mt-0.5">
                <Icon size={13} className="text-ink-muted" />
            </div>
            <div className="min-w-0 text-sm text-ink-muted">{children}</div>
        </div>
    );

    return (
        <div className="space-y-5">
            {error && <div className="alert-danger alert">{error}</div>}
            {success && <div className="alert-brand alert">{success}</div>}

            <DataToolbar
                keyword={keyword}
                onKeywordChange={setKeyword}
                onSearch={handleSearch}
                placeholder="Rechercher un fournisseur…"
                actionLabel="Nouveau fournisseur"
                onAction={openCreate}
                loading={loading}
                onRefresh={loadFournisseurs}
            />

            {loading ? (
                <ListSkeleton items={6} />
            ) : fournisseurs.length === 0 ? (
                <div className="card">
                    <EmptyState
                        icon={Truck}
                        title="Aucun fournisseur trouvé"
                        message={
                            keyword
                                ? 'Aucun résultat pour cette recherche.'
                                : 'Ajoutez votre premier fournisseur pour commencer.'
                        }
                    />
                </div>
            ) : (
                <>
                    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
                        {currentItems.map((f, index) => (
                            <article
                                key={f.id}
                                className="card p-5 hover:shadow-card hover:-translate-y-0.5 transition-all duration-200 animate-fade-in-up"
                                style={{ animationDelay: `${index * 40}ms` }}
                            >
                                <div className="flex items-start gap-3 mb-4">
                                    <div className="w-12 h-12 rounded-panel bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center text-white font-bold text-lg shrink-0">
                                        {f.nomFournisseur?.charAt(0)?.toUpperCase()}
                                    </div>
                                    <div className="min-w-0 flex-1">
                                        <h3 className="font-semibold text-ink text-base truncate">{f.nomFournisseur}</h3>
                                        <div className="mt-1">
                                            <Badge tone={f.actif ? 'brand' : 'danger'} label={f.actif ? 'Actif' : 'Inactif'} />
                                        </div>
                                    </div>
                                </div>

                                <div className="space-y-2.5 mb-4">
                                    {f.telephoneFournisseur && (
                                        <InfoRow icon={Phone}>
                                            <span className="truncate block">{f.telephoneFournisseur}</span>
                                        </InfoRow>
                                    )}
                                    {f.emailFournisseur && (
                                        <InfoRow icon={Mail}>
                                            <span className="truncate block">{f.emailFournisseur}</span>
                                        </InfoRow>
                                    )}
                                    {f.adresseFournisseur && (
                                        <InfoRow icon={MapPin}>
                                            <span className="line-clamp-2">{f.adresseFournisseur}</span>
                                        </InfoRow>
                                    )}
                                    {f.siteWeb && (
                                        <InfoRow icon={Globe}>
                                            <a
                                                href={
                                                    f.siteWeb.startsWith('http')
                                                        ? f.siteWeb
                                                        : `https://${f.siteWeb}`
                                                }
                                                target="_blank"
                                                rel="noopener noreferrer"
                                                className="text-brand-600 dark:text-brand-300 hover:underline truncate block"
                                            >
                                                {f.siteWeb}
                                            </a>
                                        </InfoRow>
                                    )}
                                </div>

                                <div className="flex justify-end gap-2 pt-3 border-t border-line">
                                    <button
                                        onClick={() => openEdit(f)}
                                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium act-quiet-bg rounded-control"
                                    >
                                        <Edit size={14} />
                                        Modifier
                                    </button>
                                    <button
                                        onClick={() => setDeleteId(f.id)}
                                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium act-danger-bg rounded-control"
                                    >
                                        <Trash2 size={14} />
                                        Supprimer
                                    </button>
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

            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title={editing ? 'Modifier le fournisseur' : 'Nouveau fournisseur'}
                subtitle={
                    editing
                        ? 'Mettez à jour les coordonnées de ce fournisseur.'
                        : 'Ajoutez un partenaire aux fiches d’immobilisation.'
                }
                icon={Truck}
                footer={
                    <FormFooter
                        embedded
                        form="fournisseur-form"
                        onCancel={() => setIsModalOpen(false)}
                        submitLabel={editing ? 'Enregistrer' : 'Créer le fournisseur'}
                        submitIcon={editing ? Save : Plus}
                        hint="Les champs marqués * sont obligatoires."
                    />
                }
            >
                <form id="fournisseur-form" onSubmit={handleSubmit} className="space-y-6">
                    <Section title="Identité">
                        <Field
                            icon={Truck}
                            label="Nom du fournisseur"
                            required
                            name="nomFournisseur"
                            value={formData.nomFournisseur}
                            onChange={handleChange}
                            placeholder="ex : BUREAU CENTER"
                            error={formErrors.nomFournisseur}
                        />

                        <div className="mt-4">
                            <Field
                                icon={MapPin}
                                label="Adresse"
                                as="textarea"
                                name="adresseFournisseur"
                                value={formData.adresseFournisseur}
                                onChange={handleChange}
                                rows={2}
                                placeholder="Adresse complète"
                                className="resize-none"
                            />
                        </div>
                    </Section>

                    <Section
                        title="Coordonnées"
                        description="Utilisées pour joindre le fournisseur lors des achats et du suivi."
                    >
                        <FieldGroup>
                            <Field
                                icon={Phone}
                                label="Téléphone"
                                name="telephoneFournisseur"
                                value={formData.telephoneFournisseur}
                                onChange={handleChange}
                                placeholder="ex : 020 22 111 11"
                            />
                            <Field
                                icon={Mail}
                                label="Email"
                                type="email"
                                name="emailFournisseur"
                                value={formData.emailFournisseur}
                                onChange={handleChange}
                                placeholder="contact@fournisseur.mg"
                                error={formErrors.emailFournisseur}
                            />
                        </FieldGroup>

                        <div className="mt-4">
                            <Field
                                icon={Globe}
                                label="Site web"
                                name="siteWeb"
                                value={formData.siteWeb}
                                onChange={handleChange}
                                placeholder="ex : www.fournisseur.mg"
                            />
                        </div>
                    </Section>

                    <CheckboxField
                        id="actif"
                        name="actif"
                        label="Fournisseur actif"
                        description="Un fournisseur inactif n’est plus proposé lors de la création d’une immobilisation."
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
                message="Voulez-vous vraiment supprimer ce fournisseur ? Cette action est irréversible."
                confirmLabel="Supprimer"
            />
        </div>
    );
};

export default GestionFournisseurs;
