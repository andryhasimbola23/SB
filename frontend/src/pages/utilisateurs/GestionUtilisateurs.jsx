import { useState, useEffect, useRef } from 'react';
import { Briefcase, Building2, Key, KeyRound, Mail, Phone, Plus, Save, ShieldCheck, User, UserPlus, Users, Pencil as PencilIcon, Trash2 as TrashIcon } from 'lucide-react';
import utilisateurService from '../../services/utilisateurService';
import serviceService from '../../services/serviceService';
import agenceService from '../../services/agenceService';
import { useAuth } from '../../hooks/useAuth';
import Modal from '../../components/ui/Modal';
import ConfirmDialog from '../../components/ui/ConfirmDialog';
import Pagination from '../../components/ui/Pagination';
import Field, { FieldGroup, CheckboxField, FormFooter } from '../../components/ui/Field';
import Section from '../../components/ui/Section';
import DataToolbar from '../../components/ui/DataToolbar';
import ResponsiveTable, { TableCard, RowAction } from '../../components/ui/ResponsiveTable';
import Badge from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/Surface';
import { TableSkeleton } from '../../components/ui/Skeleton';
import PhotoUpload from '../../components/ui/PhotoUpload';
import { getPhotoUrl } from '../../utils/format';
import { usePagination } from '../../hooks/usePagination';

const EMPTY_FORM = {
    nomUtilisateur: '',
    motDePasse: '',
    email: '',
    nom: '',
    prenom: '',
    telephone: '',
    photo: null,
    role: 'EMPLOYE',
    actif: true,
    serviceId: '',
    agenceId: '',
};

const GestionUtilisateurs = () => {
    const { user: currentUser } = useAuth();

    const [utilisateurs, setUtilisateurs] = useState([]);
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
    const [isPasswordModalOpen, setIsPasswordModalOpen] = useState(false);
    const [editingUtilisateur, setEditingUtilisateur] = useState(null);
    const [passwordUserId, setPasswordUserId] = useState(null);

    const [formData, setFormData] = useState(EMPTY_FORM);
    const [passwordForm, setPasswordForm] = useState({
        ancienMotDePasse: '',
        nouveauMotDePasse: '',
    });
    const [formErrors, setFormErrors] = useState({});
    const [deleteId, setDeleteId] = useState(null);

    const {
        currentItems: currentUtilisateurs,
        currentPage,
        totalPages,
        totalItems,
        itemsPerPage,
        handlePageChange,
    } = usePagination(utilisateurs, 6);

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
            const [usersData, servicesData, agencesData] = await Promise.all([
                utilisateurService.getAll(),
                serviceService.getAll(),
                agenceService.getAll(),
            ]);
            // Invalide d'éventuelles recherches en vol avant d'écrire la liste.
            searchSeqRef.current += 1;
            fullListRef.current = usersData;
            setUtilisateurs(usersData);
            setServices(servicesData.filter((s) => s.actif));
            setAgences(agencesData.filter((a) => a.actif));
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
            setUtilisateurs(fullListRef.current);
            return;
        }

        const seq = ++searchSeqRef.current;
        try {
            const resultats = await utilisateurService.search(k);
            // Réponse obsolète (une frappe plus récente a pris le relais).
            if (seq === searchSeqRef.current) setUtilisateurs(resultats);
        } catch {
            if (seq === searchSeqRef.current) setError('Erreur lors de la recherche');
        }
    };

    const handleOpenCreate = () => {
        setEditingUtilisateur(null);
        setFormData({
            ...EMPTY_FORM,
            serviceId: services.length > 0 ? services[0].id : '',
            agenceId: agences.length > 0 ? agences[0].id : '',
        });
        setFormErrors({});
        setIsModalOpen(true);
    };

    const handleOpenEdit = (utilisateur) => {
        setEditingUtilisateur(utilisateur);
        setFormData({
            nomUtilisateur: utilisateur.nomUtilisateur || '',
            motDePasse: '',
            email: utilisateur.email || '',
            nom: utilisateur.nom || '',
            prenom: utilisateur.prenom || '',
            telephone: utilisateur.telephone || '',
            photo: utilisateur.photo || null,
            role: utilisateur.role || 'EMPLOYE',
            actif: utilisateur.actif ?? true,
            serviceId: utilisateur.serviceId || '',
            agenceId: utilisateur.agenceId || '',
        });
        setFormErrors({});
        setIsModalOpen(true);
    };

    const handleOpenChangePassword = (utilisateur) => {
        setPasswordUserId(utilisateur.id);
        setPasswordForm({ ancienMotDePasse: '', nouveauMotDePasse: '' });
        setFormErrors({});
        setIsPasswordModalOpen(true);
    };

    const handleChange = (e) => {
        const { name, value, type, checked } = e.target;
        setFormData((prev) => ({
            ...prev,
            [name]: type === 'checkbox' ? checked : value,
        }));
        if (formErrors[name]) setFormErrors((prev) => ({ ...prev, [name]: '' }));
    };

    const handlePasswordChange = (e) => {
        const { name, value } = e.target;
        setPasswordForm((prev) => ({ ...prev, [name]: value }));
        if (formErrors[name]) setFormErrors((prev) => ({ ...prev, [name]: '' }));
    };

    const validateForm = () => {
        const errors = {};
        if (!formData.nomUtilisateur.trim()) errors.nomUtilisateur = "Le nom d'utilisateur est obligatoire";
        if (!formData.email.trim()) errors.email = "L'email est obligatoire";
        else if (!/\S+@\S+\.\S+/.test(formData.email)) errors.email = 'Email invalide';
        if (!formData.nom.trim()) errors.nom = 'Le nom est obligatoire';
        if (!formData.prenom.trim()) errors.prenom = 'Le prénom est obligatoire';
        if (!editingUtilisateur && !formData.motDePasse.trim()) {
            errors.motDePasse = 'Le mot de passe est obligatoire';
        }
        if (formData.motDePasse && formData.motDePasse.length < 6) {
            errors.motDePasse = 'Le mot de passe doit contenir au moins 6 caractères';
        }
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
                serviceId: formData.serviceId === '' ? null : Number(formData.serviceId),
                agenceId: formData.agenceId === '' ? null : Number(formData.agenceId),
            };

            if (editingUtilisateur && !payload.motDePasse) {
                delete payload.motDePasse;
            }

            if (editingUtilisateur) {
                await utilisateurService.update(editingUtilisateur.id, payload);
                flash(setSuccess, 'Utilisateur modifié avec succès', 3000);
            } else {
                await utilisateurService.create(payload);
                flash(setSuccess, 'Utilisateur créé avec succès', 3000);
            }
            setIsModalOpen(false);
            loadAll();
        } catch (err) {
            flash(setError, err.response?.data?.message || "Erreur lors de l'enregistrement", 5000);
        }
    };

    const handleSubmitPassword = async (e) => {
        e.preventDefault();
        const errors = {};
        if (!passwordForm.ancienMotDePasse) errors.ancienMotDePasse = 'Champ obligatoire';
        if (!passwordForm.nouveauMotDePasse) errors.nouveauMotDePasse = 'Champ obligatoire';
        else if (passwordForm.nouveauMotDePasse.length < 6) {
            errors.nouveauMotDePasse = 'Minimum 6 caractères';
        }
        setFormErrors(errors);
        if (Object.keys(errors).length > 0) return;

        try {
            await utilisateurService.changePassword(passwordUserId, passwordForm);
            flash(setSuccess, 'Mot de passe modifié avec succès', 3000);
            setIsPasswordModalOpen(false);
        } catch (err) {
            flash(setError, err.response?.data?.message || 'Erreur lors du changement', 5000);
        }
    };

    const handleDelete = async () => {
        try {
            await utilisateurService.delete(deleteId);
            flash(setSuccess, 'Utilisateur supprimé avec succès', 3000);
        } catch (err) {
            flash(setError, err.response?.data?.message || 'Erreur lors de la suppression', 5000);
        } finally {
            setDeleteId(null);
            loadAll();
        }
    };

    const isCurrentUser = (id) => currentUser && String(currentUser.id) === String(id);

    /* Colonne "utilisateur" : avatar + identifiant */
    const UserCell = ({ u }) => (
        <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full overflow-hidden bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center text-white font-bold text-sm shrink-0 shadow-soft">
                {u.photo ? (
                    <img
                        src={getPhotoUrl(u.photo)}
                        alt={`${u.prenom} ${u.nom}`}
                        className="w-full h-full object-cover"
                    />
                ) : (
                    <span>
                        {u.prenom?.charAt(0).toUpperCase()}
                        {u.nom?.charAt(0).toUpperCase()}
                    </span>
                )}
            </div>
            <div className="min-w-0">
                <p className="text-sm font-mono font-medium text-ink truncate">@{u.nomUtilisateur}</p>
                {isCurrentUser(u.id) && <p className="text-xs text-brand-600 dark:text-brand-300">(vous)</p>}
            </div>
        </div>
    );

    const COLUMNS = [
        { key: 'utilisateur', label: 'Utilisateur', primary: true, render: (u) => <UserCell u={u} /> },
        {
            key: 'nomComplet',
            label: 'Nom complet',
            hideBelow: 'md',
            render: (u) => (
                <span className="font-medium text-ink">
                    {u.prenom} {u.nom}
                </span>
            ),
        },
        { key: 'email', label: 'Email', hideBelow: 'lg', render: (u) => u.email },
        { key: 'telephone', label: 'Téléphone', hideBelow: 'xl', render: (u) => u.telephone || '—' },
        {
            key: 'role',
            label: 'Rôle',
            render: (u) =>
                u.role === 'ADMIN' ? (
                    <Badge tone="danger" label="Admin" />
                ) : (
                    <Badge tone="neutral" label="Employé" />
                ),
        },
        {
            key: 'agence',
            label: 'Agence',
            hideBelow: 'lg',
            render: (u) =>
                u.agenceNom ? (
                    <span className="inline-flex items-center px-2 py-0.5 rounded-control bg-surface-2 dark:bg-slate-800 text-ink-muted text-xs border border-line">
                        {u.agenceNom}
                    </span>
                ) : (
                    '—'
                ),
        },
        {
            key: 'actif',
            label: 'Statut',
            render: (u) => <Badge tone={u.actif ? 'brand' : 'danger'} label={u.actif ? 'Actif' : 'Inactif'} />,
        },
        {
            key: 'actions',
            label: 'Actions',
            align: 'right',
            actions: true,
            render: (u) => (
                <div className="inline-flex items-center gap-1 justify-end">
                    {isCurrentUser(u.id) && (
                        <RowAction
                            icon={Key}
                            label="Changer le mot de passe"
                            onClick={() => handleOpenChangePassword(u)}
                            variant="neutral"
                        />
                    )}
                    <RowAction icon={PencilIcon} label="Modifier" onClick={() => handleOpenEdit(u)} variant="edit" />
                    {!isCurrentUser(u.id) && (
                        <RowAction icon={TrashIcon} label="Supprimer" onClick={() => setDeleteId(u.id)} variant="delete" />
                    )}
                </div>
            ),
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
                placeholder="Rechercher un utilisateur…"
                actionLabel="Nouvel utilisateur"
                onAction={handleOpenCreate}
                loading={loading}
                onRefresh={loadAll}
            />

            <TableCard>
                {loading ? (
                    <TableSkeleton rows={6} cols={8} />
                ) : (
                    <>
                        <ResponsiveTable
                            columns={COLUMNS}
                            data={currentUtilisateurs}
                            rowKey={(u) => u.id}
                            empty={
                                <EmptyState
                                    icon={Users}
                                    title="Aucun utilisateur trouvé"
                                    message={
                                        keyword
                                            ? 'Aucun résultat pour cette recherche.'
                                            : 'Créez votre premier compte utilisateur.'
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

            {/* Création / modification */}
            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title={editingUtilisateur ? "Modifier l'utilisateur" : 'Nouvel utilisateur'}
                subtitle={
                    editingUtilisateur
                        ? 'Mettez à jour l’identité, le rattachement ou les accès de ce compte.'
                        : 'Créez un compte et rattachez-le à une agence et un service.'
                }
                icon={UserPlus}
                size="lg"
                footer={
                    <FormFooter
                        embedded
                        form="utilisateur-form"
                        onCancel={() => setIsModalOpen(false)}
                        submitLabel={
                            editingUtilisateur ? 'Enregistrer les modifications' : "Créer l'utilisateur"
                        }
                        submitIcon={editingUtilisateur ? Save : Plus}
                        hint="Les champs marqués * sont obligatoires."
                    />
                }
            >
                <form id="utilisateur-form" onSubmit={handleSubmit} className="space-y-6">
                    <div className="flex flex-col items-center gap-3 rounded-panel border border-line bg-surface-2 p-4 dark:bg-slate-800/60">
                        <PhotoUpload
                            value={formData.photo}
                            onChange={(url) => setFormData((prev) => ({ ...prev, photo: url }))}
                            size="lg"
                            userId={editingUtilisateur?.id}
                        />
                        <p className="text-xs text-ink-subtle">Format JPG ou PNG · 5 MB maximum</p>
                    </div>

                    <Section title="Identifiants de connexion">
                        <FieldGroup>
                            <Field
                                icon={User}
                                label="Nom d'utilisateur"
                                required
                                name="nomUtilisateur"
                                value={formData.nomUtilisateur}
                                onChange={handleChange}
                                maxLength={25}
                                placeholder="ex : jdupont"
                                hint="Utilisé pour se connecter, sans espaces."
                                error={formErrors.nomUtilisateur}
                            />
                            <Field icon={ShieldCheck} label="Rôle" required>
                                <select
                                    id="role"
                                    name="role"
                                    value={formData.role}
                                    onChange={handleChange}
                                    className="input"
                                >
                                    <option value="EMPLOYE">Employé</option>
                                    <option value="ADMIN">Administrateur</option>
                                </select>
                            </Field>
                        </FieldGroup>
                    </Section>

                    <Section title="Informations personnelles">
                        <FieldGroup>
                            <Field
                                icon={User}
                                label="Nom"
                                required
                                name="nom"
                                value={formData.nom}
                                onChange={handleChange}
                                error={formErrors.nom}
                            />
                            <Field
                                icon={User}
                                label="Prénom"
                                required
                                name="prenom"
                                value={formData.prenom}
                                onChange={handleChange}
                                error={formErrors.prenom}
                            />
                        </FieldGroup>

                        <FieldGroup className="mt-4">
                            <Field
                                icon={Mail}
                                label="Email"
                                required
                                type="email"
                                name="email"
                                value={formData.email}
                                onChange={handleChange}
                                error={formErrors.email}
                            />
                            <Field
                                icon={Phone}
                                label="Téléphone"
                                type="text"
                                name="telephone"
                                value={formData.telephone}
                                onChange={handleChange}
                            />
                        </FieldGroup>
                    </Section>

                    <Section
                        title="Affectation"
                        description="Le rattachement détermine les biens visibles et l’adresse de livraison des demandes."
                    >
                        <FieldGroup>
                            <Field icon={Building2} label="Agence">
                                <select
                                    id="agenceId"
                                    name="agenceId"
                                    value={formData.agenceId}
                                    onChange={handleChange}
                                    className="input"
                                >
                                    <option value="">-- Aucune --</option>
                                    {agences.map((ag) => (
                                        <option key={ag.id} value={ag.id}>
                                            {ag.codeAgence} - {ag.nomAgence}
                                        </option>
                                    ))}
                                </select>
                            </Field>
                            <Field icon={Briefcase} label="Service">
                                <select
                                    id="serviceId"
                                    name="serviceId"
                                    value={formData.serviceId}
                                    onChange={handleChange}
                                    className="input"
                                >
                                    <option value="">-- Aucun --</option>
                                    {services.map((s) => (
                                        <option key={s.id} value={s.id}>
                                            {s.code} - {s.nomService}
                                        </option>
                                    ))}
                                </select>
                            </Field>
                        </FieldGroup>
                    </Section>

                    <Section title="Sécurité">
                        <Field
                            icon={KeyRound}
                            label={
                                editingUtilisateur
                                    ? 'Nouveau mot de passe (laisser vide pour ne pas changer)'
                                    : 'Mot de passe'
                            }
                            required={!editingUtilisateur}
                            type="password"
                            name="motDePasse"
                            value={formData.motDePasse}
                            onChange={handleChange}
                            placeholder={editingUtilisateur ? 'Laisser vide pour conserver' : 'Minimum 6 caractères'}
                            error={formErrors.motDePasse}
                        />

                        <div className="mt-4">
                            <CheckboxField
                                id="actif"
                                name="actif"
                                label="Compte actif"
                                description="L’utilisateur peut se connecter et accéder aux biens de son périmètre."
                                checked={formData.actif}
                                onChange={handleChange}
                            />
                        </div>
                    </Section>
                </form>
            </Modal>

            {/* Changement de mot de passe */}
            <Modal
                isOpen={isPasswordModalOpen}
                onClose={() => setIsPasswordModalOpen(false)}
                title="Changer le mot de passe"
                subtitle="Définissez un nouveau mot de passe pour cet utilisateur."
                icon={KeyRound}
                size="sm"
                footer={
                    <FormFooter
                        embedded
                        form="password-form"
                        onCancel={() => setIsPasswordModalOpen(false)}
                        submitLabel="Valider"
                        submitIcon={Save}
                    />
                }
            >
                <form id="password-form" onSubmit={handleSubmitPassword} className="space-y-4">
                    <Field
                        label="Ancien mot de passe"
                        required
                        type="password"
                        name="ancienMotDePasse"
                        value={passwordForm.ancienMotDePasse}
                        onChange={handlePasswordChange}
                        error={formErrors.ancienMotDePasse}
                    />
                    <Field
                        label="Nouveau mot de passe"
                        required
                        type="password"
                        name="nouveauMotDePasse"
                        value={passwordForm.nouveauMotDePasse}
                        onChange={handlePasswordChange}
                        placeholder="Minimum 6 caractères"
                        error={formErrors.nouveauMotDePasse}
                    />
                </form>
            </Modal>

            <ConfirmDialog
                isOpen={deleteId !== null}
                onClose={() => setDeleteId(null)}
                onConfirm={handleDelete}
                title="Confirmer la suppression"
                message="Êtes-vous sûr de vouloir supprimer cet utilisateur ? Cette action est irréversible."
                confirmLabel="Supprimer"
            />
        </div>
    );
};

export default GestionUtilisateurs;
